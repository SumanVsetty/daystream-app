package com.ivy.planner.ui.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.domain.Series
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.launch

/** How an edit to a repeating task applies. */
enum class EditScope { ONLY_TODAY, TODAY_AND_FUTURE }

@Immutable
data class EditorState(
    val loaded: Boolean,
    val isNew: Boolean,
    val isOccurrence: Boolean,
    val kind: EntryKind,
    val title: String,
    val description: String,
    val date: LocalDate,
    val time: LocalTime?,
    val durationMinutes: Int,
    val schedule: RepeatSchedule?,
    val repeatLabel: String,
    val askScope: Boolean,
    val closed: Boolean,
)

sealed interface EditorEvent {
    data class Load(val entryId: String?, val seriesId: String?, val date: LocalDate, val kind: EntryKind) : EditorEvent
    data class SetKind(val kind: EntryKind) : EditorEvent
    data class SetTitle(val title: String) : EditorEvent
    data class SetDescription(val description: String) : EditorEvent
    data class SetDate(val date: LocalDate) : EditorEvent
    data class SetTime(val time: LocalTime?) : EditorEvent
    data class SetDuration(val minutes: Int) : EditorEvent
    data class SetRepeat(val schedule: RepeatSchedule?) : EditorEvent
    data object Save : EditorEvent
    data class ConfirmScope(val scope: EditScope) : EditorEvent
    data object DismissScope : EditorEvent
    data object SkipToday : EditorEvent
    data object EndSeries : EditorEvent
    data object Delete : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<EditorState, EditorEvent>() {

    private var loaded by mutableStateOf(false)
    private var entry: Entry? = null
    private var series: Series? = null

    private var kind by mutableStateOf(EntryKind.TASK)
    private var title by mutableStateOf("")
    private var description by mutableStateOf("")
    private var date by mutableStateOf(LocalDate.now())
    private var time by mutableStateOf<LocalTime?>(null)
    private var duration by mutableStateOf(AutoTime.DEFAULT_DURATION)
    private var schedule by mutableStateOf<RepeatSchedule?>(null)
    private var askScope by mutableStateOf(false)
    private var closed by mutableStateOf(false)

    @Composable
    override fun uiState(): EditorState = EditorState(
        loaded = loaded,
        isNew = entry == null && series == null,
        isOccurrence = series != null,
        kind = kind,
        title = title,
        description = description,
        date = date,
        time = time,
        durationMinutes = duration,
        schedule = schedule,
        repeatLabel = schedule?.let { RepeatCodec.describe(it) } ?: "Never",
        askScope = askScope,
        closed = closed,
    )

    override fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.Load -> load(event)
            is EditorEvent.SetKind -> {
                kind = event.kind
                if (kind == EntryKind.NOTE || kind == EntryKind.JOURNAL) schedule = null
            }
            is EditorEvent.SetTitle -> title = event.title
            is EditorEvent.SetDescription -> description = event.description
            is EditorEvent.SetDate -> {
                date = event.date
                // a new repeat starts from the chosen date
                if (series == null) schedule = schedule?.copy(start = event.date)
            }
            is EditorEvent.SetTime -> time = event.time
            is EditorEvent.SetDuration -> duration = event.minutes
            is EditorEvent.SetRepeat -> schedule = event.schedule?.copy(start = if (series == null) date else event.schedule.start)
            EditorEvent.Save -> save()
            is EditorEvent.ConfirmScope -> {
                askScope = false
                saveSeriesEdit(event.scope)
            }
            EditorEvent.DismissScope -> askScope = false
            EditorEvent.SkipToday -> series?.let { s ->
                viewModelScope.launch {
                    repository.setOccurrenceState(s.id, date, EntryState.SKIPPED)
                    closed = true
                }
            }
            EditorEvent.EndSeries -> series?.let { s ->
                viewModelScope.launch {
                    repository.endSeries(s, date)
                    closed = true
                }
            }
            EditorEvent.Delete -> viewModelScope.launch {
                entry?.let { repository.deleteEntry(it.id) }
                series?.let { repository.deleteSeries(it.id) }
                closed = true
            }
        }
    }

    private fun load(event: EditorEvent.Load) {
        if (loaded) return
        date = event.date
        kind = event.kind
        viewModelScope.launch {
            if (event.entryId != null) {
                repository.getEntry(event.entryId)?.let { e ->
                    entry = e
                    kind = e.kind
                    title = e.title
                    description = e.description
                    date = e.date ?: event.date
                    time = e.time
                    duration = e.durationMinutes ?: AutoTime.DEFAULT_DURATION
                }
            } else if (event.seriesId != null) {
                repository.getSeries(event.seriesId)?.let { s ->
                    series = s
                    kind = s.kind
                    title = s.title
                    description = s.description
                    time = s.time
                    duration = s.durationMinutes ?: AutoTime.DEFAULT_DURATION
                    schedule = s.schedule
                }
            }
            loaded = true
        }
    }

    private fun save() {
        if (title.isBlank()) return
        val s = series
        if (s != null) {
            val changed = title != s.title || description != s.description || time != s.time ||
                schedule != s.schedule || duration != (s.durationMinutes ?: AutoTime.DEFAULT_DURATION)
            if (!changed) {
                closed = true
                return
            }
            if (schedule == null || schedule != s.schedule) {
                // repeat changes (or removing the repeat) only make sense going forward
                saveSeriesEdit(EditScope.TODAY_AND_FUTURE)
            } else {
                askScope = true
            }
            return
        }
        viewModelScope.launch {
            val sch = schedule
            if (sch != null && (kind == EntryKind.TASK || kind == EntryKind.EVENT)) {
                // a one-off becoming repeating is replaced by a new series
                entry?.let { repository.deleteEntry(it.id) }
                repository.saveSeries(
                    Series(
                        id = repository.newSeriesId(),
                        kind = kind,
                        title = title.trim(),
                        description = description.trim(),
                        time = time,
                        schedule = sch.copy(start = date),
                        durationMinutes = duration,
                    ),
                )
            } else {
                val base = entry ?: Entry(id = "", kind = kind, title = "")
                if (entry == null) {
                    repository.quickAdd(kind, title, date, time = time, description = description.trim(), durationMinutes = duration)
                } else {
                    repository.saveEntry(
                        base.copy(
                            kind = kind,
                            title = title.trim(),
                            description = description.trim(),
                            date = date,
                            time = time,
                            durationMinutes = duration,
                        ),
                    )
                }
            }
            closed = true
        }
    }

    private fun saveSeriesEdit(scope: EditScope) {
        val s = series ?: return
        viewModelScope.launch {
            when (scope) {
                EditScope.ONLY_TODAY -> repository.editOccurrence(
                    seriesId = s.id,
                    date = date,
                    title = title.trim().takeIf { it != s.title },
                    description = description.trim().takeIf { it != s.description },
                    time = time.takeIf { it != s.time },
                )
                EditScope.TODAY_AND_FUTURE -> {
                    val sch = schedule
                    if (sch == null) {
                        // repeat turned off: end the series and keep today as a one-off
                        repository.endSeries(s, date.minusDays(1))
                        repository.quickAdd(kind, title, date, time = time, description = description.trim(), durationMinutes = duration)
                    } else {
                        repository.updateSeriesFrom(
                            original = s,
                            updated = s.copy(
                                kind = kind,
                                title = title.trim(),
                                description = description.trim(),
                                time = time,
                                schedule = sch,
                                durationMinutes = duration,
                            ),
                            from = date,
                        )
                    }
                }
            }
            closed = true
        }
    }
}
