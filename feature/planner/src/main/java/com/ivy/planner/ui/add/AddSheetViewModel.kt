package com.ivy.planner.ui.add

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.ChecklistItem
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.domain.Series
import com.ivy.planner.domain.isoWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.launch

/** What the add sheet opens with. */
data class AddRequest(val kind: EntryKind, val date: LocalDate, val text: String = "")

/** Opens the add sheet from anywhere (the logo menu, the Day tab's log bar, the Journal tab). */
object PlannerAddSheet {
    var request: AddRequest? by mutableStateOf(null)

    fun open(kind: EntryKind, date: LocalDate, text: String = "") {
        request = AddRequest(kind, date, text)
    }

    /** For callers outside the planner: "TASK", "EVENT", "NOTE" or "JOURNAL". */
    fun open(kindName: String, date: LocalDate) {
        open(EntryKind.values().firstOrNull { it.name == kindName } ?: EntryKind.TASK, date)
    }

    fun close() {
        request = null
    }
}

/**
 * The quick add sheet: a title line that understands dates, times, durations and #boards,
 * plus chips for everything else. Chip choices win over what the text says.
 */
@HiltViewModel
class AddSheetViewModel @Inject constructor(
    private val planner: PlannerRepository,
    val library: LibraryRepository,
    private val prefs: PlannerPrefs,
) : ViewModel() {
    var kind by mutableStateOf(EntryKind.TASK)
    var baseDate by mutableStateOf(LocalDate.now())
    var text by mutableStateOf("")
    var description by mutableStateOf("")
    var showDetails by mutableStateOf(false)
    var date by mutableStateOf<LocalDate?>(null)
    var time by mutableStateOf<LocalTime?>(null)
    var duration by mutableStateOf<Int?>(null)
    var reminders by mutableStateOf<List<Int>?>(null)
    var schedule by mutableStateOf<RepeatSchedule?>(null)
    var boardId by mutableStateOf<String?>(null)
    var importance by mutableStateOf(0)
    var peopleIds by mutableStateOf(setOf<String>())
    var collectionIds by mutableStateOf(setOf<String>())
    var photos by mutableStateOf(listOf<Uri>())
    var files by mutableStateOf(listOf<Uri>())
    var checklist by mutableStateOf(listOf<String>())
    var showChecklist by mutableStateOf(false)

    init {
        com.ivy.planner.ui.ImportanceNames.labels = prefs.importanceLabels
    }

    fun reset(request: AddRequest) {
        kind = request.kind
        baseDate = request.date
        text = request.text
        description = ""
        showDetails = false
        date = null
        time = null
        duration = null
        reminders = null
        schedule = null
        boardId = null
        importance = 0
        peopleIds = emptySet()
        collectionIds = emptySet()
        photos = emptyList()
        files = emptyList()
        checklist = emptyList()
        showChecklist = false
    }

    val parsed get() = RapidLogParser.parse(text, LocalDate.now())

    /** The kind that will be saved: "- " / "o " at the start of the text override the chosen type. */
    val effectiveKind: EntryKind get() = parsed.kind.takeIf { it != EntryKind.TASK } ?: kind

    val effectiveDate: LocalDate get() = date ?: parsed.date ?: baseDate
    val effectiveTime: LocalTime? get() = time ?: parsed.time
    val effectiveDuration: Int get() = duration ?: parsed.durationMinutes ?: AutoTime.DEFAULT_DURATION

    /** Reminders shown on the chip: yours, or the default when you've given a time. */
    val effectiveReminders: List<Int>
        get() = reminders ?: if (effectiveTime != null && (effectiveKind == EntryKind.TASK || effectiveKind == EntryKind.EVENT)) {
            listOfNotNull(prefs.defaultReminder)
        } else {
            emptyList()
        }

    fun save(onDone: () -> Unit) {
        val p = parsed
        if (p.title.isBlank()) return
        val k = effectiveKind
        val d = effectiveDate
        val t = effectiveTime
        val dur = duration ?: p.durationMinutes
        val rem = effectiveReminders
        val sch = schedule
        viewModelScope.launch {
            val owner: String
            if (sch != null && (k == EntryKind.TASK || k == EntryKind.EVENT)) {
                owner = planner.newSeriesId()
                planner.saveSeries(
                    Series(
                        id = owner,
                        kind = k,
                        title = p.title,
                        description = description.trim(),
                        time = t,
                        schedule = sch.copy(start = d),
                        durationMinutes = dur,
                    ),
                )
            } else {
                owner = planner.quickAdd(k, p.title, d, week = d.isoWeek(), time = t, description = description.trim(), durationMinutes = dur)
                if (k == EntryKind.JOURNAL) {
                    planner.setImportance(owner, importance)
                    library.setPeople(owner, peopleIds.toList())
                    photos.forEach { library.addPhoto(owner, it) }
                } else if (photos.isNotEmpty()) {
                    photos.forEach { library.addPhoto(owner, it) }
                }
            }
            if (sch == null || !(k == EntryKind.TASK || k == EntryKind.EVENT)) {
                files.forEach { library.addFile(owner, it) }
                if (k == EntryKind.TASK && checklist.isNotEmpty()) {
                    library.saveChecklist(owner, checklist.map { ChecklistItem(library.newItemId(), it) })
                }
            }
            if (k == EntryKind.TASK || k == EntryKind.EVENT) planner.setReminders(owner, rem)
            val collections = library.resolveTags(p.tags) + listOfNotNull(boardId) +
                if (k == EntryKind.JOURNAL) collectionIds.toList() else emptyList()
            if (collections.isNotEmpty()) library.setCollections(owner, collections)
            onDone()
        }
    }
}
