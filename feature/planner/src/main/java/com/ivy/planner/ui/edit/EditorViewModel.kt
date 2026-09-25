package com.ivy.planner.ui.edit

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.Library
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.CollectionType
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.domain.Series
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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
    val importance: Int,
    val peopleIds: Set<String>,
    val collectionIds: Set<String>,
    val boardId: String?,
    /** Existing photos (attachment id → file) and photos picked but not saved yet. */
    val photos: List<Pair<String, File>>,
    val pendingPhotos: List<Uri>,
    val library: Library?,
    /** Minutes before (0 = at the time). */
    val reminders: List<Int>,
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
    data class SetImportance(val level: Int) : EditorEvent
    data class TogglePerson(val id: String) : EditorEvent
    data class ToggleCollection(val id: String) : EditorEvent
    data class SetBoard(val id: String?) : EditorEvent
    data class AddPerson(val name: String) : EditorEvent
    data class AddCollection(val name: String) : EditorEvent
    data class AddPhotos(val uris: List<Uri>) : EditorEvent
    data class RemovePhoto(val attachmentId: String) : EditorEvent
    data class RemovePendingPhoto(val uri: Uri) : EditorEvent
    data class ToggleReminder(val minutesBefore: Int) : EditorEvent
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val library: LibraryRepository,
    private val prefs: PlannerPrefs,
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
    private var importance by mutableStateOf(0)
    private var peopleIds by mutableStateOf(setOf<String>())
    private var collectionIds by mutableStateOf(setOf<String>())
    private var boardId by mutableStateOf<String?>(null)
    private var photos by mutableStateOf(listOf<Pair<String, File>>())
    private var pendingPhotos by mutableStateOf(listOf<Uri>())
    private var removedPhotos = setOf<String>()
    private var reminders by mutableStateOf(listOf<Int>())
    private var remindersTouched = false

    @Composable
    override fun uiState(): EditorState {
        val lib by remember { library.observe() }.collectAsState(initial = null)
        return state(lib)
    }

    private fun state(lib: Library?): EditorState = EditorState(
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
        importance = importance,
        peopleIds = peopleIds,
        collectionIds = collectionIds,
        boardId = boardId,
        photos = photos,
        pendingPhotos = pendingPhotos,
        library = lib,
        reminders = reminders,
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
            is EditorEvent.SetTime -> {
                time = event.time
                // a time you pick yourself gets the default reminder, unless you've set reminders already
                if (event.time != null && !remindersTouched && reminders.isEmpty()) {
                    prefs.defaultReminder?.let { reminders = listOf(it) }
                }
            }
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
            is EditorEvent.SetImportance -> importance = event.level
            is EditorEvent.TogglePerson -> peopleIds = peopleIds.toggle(event.id)
            is EditorEvent.ToggleCollection -> collectionIds = collectionIds.toggle(event.id)
            is EditorEvent.SetBoard -> boardId = event.id
            is EditorEvent.AddPerson -> viewModelScope.launch { peopleIds = peopleIds + library.savePerson(event.name) }
            is EditorEvent.AddCollection -> viewModelScope.launch {
                collectionIds = collectionIds + library.saveCollection(event.name, CollectionType.TOPIC)
            }
            is EditorEvent.AddPhotos -> pendingPhotos = pendingPhotos + event.uris
            is EditorEvent.RemovePhoto -> {
                removedPhotos = removedPhotos + event.attachmentId
                photos = photos.filterNot { it.first == event.attachmentId }
            }
            is EditorEvent.RemovePendingPhoto -> pendingPhotos = pendingPhotos - event.uri
            is EditorEvent.ToggleReminder -> {
                remindersTouched = true
                reminders = if (event.minutesBefore in reminders) reminders - event.minutesBefore
                else (reminders + event.minutesBefore).sorted()
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
            val owner = event.entryId ?: event.seriesId
            if (owner != null) {
                reminders = repository.reminders(owner).sorted()
                val types = library.allCollections().associateBy { it.id }
                val linked = library.collectionsOf(owner)
                boardId = linked.firstOrNull { types[it]?.type == CollectionType.BOARD }
                collectionIds = linked.filter { types[it]?.type == CollectionType.TOPIC }.toSet()
            }
            if (event.entryId != null) {
                importance = entry?.importance ?: 0
                peopleIds = library.peopleOf(event.entryId).toSet()
                photos = library.photosOf(event.entryId).map { it.id to library.photoFile(it.fileName) }
            }
            loaded = true
        }
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id

    /** Board, collections, people, importance and photos, once the entry or series has an id. */
    private suspend fun applyExtras(ownerId: String, isEntry: Boolean) {
        library.setCollections(ownerId, collectionIds.toList() + listOfNotNull(boardId))
        repository.setReminders(ownerId, if (kind == EntryKind.TASK || kind == EntryKind.EVENT) reminders else emptyList())
        if (!isEntry) return
        library.setPeople(ownerId, peopleIds.toList())
        repository.setImportance(ownerId, importance)
        removedPhotos.forEach { library.removePhoto(it) }
        pendingPhotos.forEach { library.addPhoto(ownerId, it) }
    }

    private fun save() {
        if (title.isBlank()) return
        val s = series
        if (s != null) {
            // board and reminder changes apply straight away, even when nothing else changed
            viewModelScope.launch {
                library.setCollections(s.id, collectionIds.toList() + listOfNotNull(boardId))
                repository.setReminders(s.id, reminders)
            }
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
                val newId = repository.newSeriesId()
                repository.saveSeries(
                    Series(
                        id = newId,
                        kind = kind,
                        title = title.trim(),
                        description = description.trim(),
                        time = time,
                        schedule = sch.copy(start = date),
                        durationMinutes = duration,
                    ),
                )
                applyExtras(newId, isEntry = false)
            } else {
                val base = entry ?: Entry(id = "", kind = kind, title = "")
                if (entry == null) {
                    val id = repository.quickAdd(kind, title, date, time = time, description = description.trim(), durationMinutes = duration)
                    applyExtras(id, isEntry = true)
                } else {
                    repository.saveEntry(
                        base.copy(
                            kind = kind,
                            title = title.trim(),
                            description = description.trim(),
                            date = date,
                            time = time,
                            durationMinutes = duration,
                            importance = importance,
                        ),
                    )
                    applyExtras(base.id, isEntry = true)
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
                        val newId = repository.updateSeriesFrom(
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
                        applyExtras(newId, isEntry = false)
                    }
                }
            }
            closed = true
        }
    }
}
