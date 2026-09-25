package com.ivy.planner.ui.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.Library
import com.ivy.planner.data.LibraryRepository
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.data.PlannerSnapshot
import com.ivy.planner.data.RoutineRepository
import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.FreeTime
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.MoneyItem
import com.ivy.planner.ui.MoneySource
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerSelection
import com.ivy.planner.ui.RowTag
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch

/** A row on the Day log, ready to display. */
@Immutable
data class DayRow(
    val key: String,
    val kind: EntryKind,
    val title: String,
    val description: String,
    val meta: String,
    val state: EntryState,
    val repeating: Boolean,
    /** One-off entry id, or null for a series occurrence. */
    val entryId: String?,
    val seriesId: String?,
    val date: LocalDate,
    val time: LocalTime?,
    /** Planned length in minutes (null = default). */
    val durationMinutes: Int? = null,
    /** Expense or income from the money side (read-only here). */
    val money: MoneyItem? = null,
    /** Shown in the time column: "10:30". */
    val timeLabel: String = "",
    /** At the right end of the title line: "15 min", "All day". */
    val trailing: String = "",
    val importance: Int = 0,
    val tags: List<RowTag> = emptyList(),
    val photos: List<File> = emptyList(),
    val isRoutine: Boolean = false,
    /** For routines: steps done and total today. */
    val routine: Pair<Int, Int>? = null,
)

@Immutable
data class DayState(
    val date: LocalDate,
    val today: LocalDate,
    val summary: String,
    val rows: ImmutableList<DayRow>,
    val overdue: ImmutableList<Entry>,
    val dots: ImmutableMap<LocalDate, Color>,
    val todoOnly: Boolean,
    /** Index in [rows] to scroll to when opening today (one hour before now). */
    val scrollIndex: Int?,
    /** The "Now & next" layout (true) or the classic timeline (false). */
    val focusLayout: Boolean = true,
    val focus: FocusDay = FocusDay(),
)

/** A line in "Later today": an entry, or a free stretch of time. */
@Immutable
sealed interface LaterItem {
    data class Row(val row: DayRow) : LaterItem
    data class Free(val label: String) : LaterItem
}

/** The day split for the "Now & next" layout. */
@Immutable
data class FocusDay(
    /** Open tasks from earlier today (and earlier days), latest problems first. */
    val stillOpen: List<DayRow> = emptyList(),
    val nextUp: DayRow? = null,
    val nextUpCountdown: String = "",
    val later: List<LaterItem> = emptyList(),
    val earlier: List<DayRow> = emptyList(),
    /** "3 done · 2 expenses · 1 note · 1 memory" */
    val earlierSummary: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val spent: String? = null,
    /** Past days show everything as one list; future days as planned. */
    val isToday: Boolean = true,
)

sealed interface DayEvent {
    data class SelectDate(val date: LocalDate) : DayEvent
    data class Toggle(val row: DayRow) : DayEvent
    data class RapidLog(val text: String) : DayEvent
    data class OverdueDone(val id: String) : DayEvent
    data class OverdueMove(val id: String, val date: LocalDate) : DayEvent
    data class OverdueDrop(val id: String) : DayEvent
    data object ToggleFilter : DayEvent
    data object ToggleLayout : DayEvent
    /** Move to the next free slot today. */
    data class Later(val row: DayRow) : DayEvent
    /** Same time tomorrow (repeating tasks: skip today). */
    data class Tomorrow(val row: DayRow) : DayEvent
    /** Reload expenses (e.g. after returning from the money screens). */
    data object Refresh : DayEvent
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val library: LibraryRepository,
    private val routines: RoutineRepository,
    private val moneySource: MoneySource,
    private val prefs: PlannerPrefs,
) : ComposeViewModel<DayState, DayEvent>() {

    private var selected by mutableStateOf(PlannerSelection.date)
    private var todoOnly by mutableStateOf(prefs.todoOnly)
    private var focusLayout by mutableStateOf(prefs.focusLayout)
    private var refresh by mutableIntStateOf(0)

    @Composable
    override fun uiState(): DayState {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val date = selected
        val week = date.isoWeek()
        val snapshot by remember(week, today) {
            repository.observe(week.monday, week.sunday, today, week)
        }.collectAsState(initial = null)
        val money by produceState(initialValue = emptyList<MoneyItem>(), week, refresh) {
            value = moneySource.between(week.monday, week.sunday)
        }
        val lib by remember { library.observe() }.collectAsState(initial = null)
        val routineSteps by remember { routines.observeSteps() }.collectAsState(initial = emptyMap())
        val stepStates by remember(week) { routines.observeStates(week.monday) }.collectAsState(initial = emptyMap())

        val s = snapshot ?: return DayState(
            date, today, "", persistentListOf(), persistentListOf(), persistentMapOf(),
            todoOnly, null,
        )
        val allRows = (
            itemsFor(s, date, today).map { it.toRow(s, today).withLibrary(lib).withRoutine(routineSteps, stepStates) } +
                money.filter { it.date == date }.map { it.toRow() }
            )
            .sortedWith(compareBy<DayRow>({ it.time != null }, { it.time }, { it.title.lowercase() }))
        val isToday = date == today
        val rows = if (todoOnly) allRows.filter { it.isToDo(isToday, now) } else allRows
        val open = allRows.count { it.isOpenTask() }
        val done = allRows.count { it.state == EntryState.DONE }
        val oneHourAgo = now.minusHours(1).takeIf { now.hour >= 1 } ?: LocalTime.MIDNIGHT
        val scrollIndex = if (isToday) rows.indexOfFirst { it.time != null && it.time >= oneHourAgo }.takeIf { it > 0 } else null
        return DayState(
            date = date,
            today = today,
            summary = "$open open · $done done",
            rows = rows.toImmutableList(),
            overdue = (if (isToday) s.overdue else emptyList()).toImmutableList(),
            dots = week.days.mapNotNull { d -> dotFor(itemsFor(s, d, today), d, today)?.let { d to it } }
                .toMap().toImmutableMap(),
            todoOnly = todoOnly,
            scrollIndex = scrollIndex,
            focusLayout = focusLayout,
            focus = focusDay(allRows, s.overdue, date, today, now, money.filter { it.date == date }),
        )
    }

    private fun DayRow.isOpenTaskLike() = money == null && kind == EntryKind.TASK && state == EntryState.OPEN

    /** Splits the day into still open, next up, later (with free time) and earlier. */
    private fun focusDay(
        rows: List<DayRow>,
        overdue: List<Entry>,
        date: LocalDate,
        today: LocalDate,
        now: LocalTime,
        money: List<MoneyItem>,
    ): FocusDay {
        val tasks = rows.filter { it.money == null && it.kind == EntryKind.TASK && it.state != EntryState.SKIPPED }
        val spentByCurrency = money.filter { !it.isIncome }.groupBy { it.currency }
        val spent = spentByCurrency.map { (c, l) -> com.ivy.planner.ui.formatMoney(l.sumOf { it.amount }, c) }
            .takeIf { it.isNotEmpty() }?.joinToString(" + ")
        val done = tasks.count { it.state == EntryState.DONE }
        if (date != today) {
            // past days: the whole day as one list; future days: everything is planned
            val later = if (date.isAfter(today)) withFreeTime(rows, LocalTime.of(8, 0)) else rows.map { LaterItem.Row(it) }
            return FocusDay(later = later, done = done, total = tasks.size, spent = spent, isToday = false)
        }
        val stillOpen = rows.filter { it.isOpenTaskLike() && it.time != null && it.time < now } +
            overdue.map { e ->
                DayRow(
                    key = "o:${e.id}", kind = e.kind, title = e.title, description = e.description,
                    meta = if (e.migrationCount > 0) "moved ${e.migrationCount}×" else "",
                    state = e.state, repeating = false, entryId = e.id, seriesId = null,
                    date = e.date ?: date, time = e.time,
                    timeLabel = e.date?.let { "from " + it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH) } ?: "",
                )
            }
        val upcoming = rows.filter { it.isOpenTaskLike() && it.time != null && it.time >= now }
        val nextUp = upcoming.firstOrNull()
        val laterRows = rows.filter { r ->
            r.key != nextUp?.key && r !in stillOpen && r.state != EntryState.DONE &&
                (r.time == null && r.kind == EntryKind.EVENT || (r.time != null && r.time >= now))
        }
        val from = nextUp?.let { n -> n.time!!.plusMinutes((n.durationMinutes ?: 15).toLong()) } ?: now
        val earlier = rows.filter { r -> r.key != nextUp?.key && r !in stillOpen && r !in laterRows }
        val summary = listOfNotNull(
            earlier.count { it.money == null && it.kind == EntryKind.TASK && it.state == EntryState.DONE }.takeIf { it > 0 }?.let { "$it done" },
            earlier.count { it.money?.isIncome == false }.takeIf { it > 0 }?.let { if (it == 1) "1 expense" else "$it expenses" },
            earlier.count { it.money == null && it.kind == EntryKind.EVENT }.takeIf { it > 0 }?.let { if (it == 1) "1 event" else "$it events" },
            earlier.count { it.money == null && it.kind == EntryKind.NOTE }.takeIf { it > 0 }?.let { if (it == 1) "1 note" else "$it notes" },
            earlier.count { it.money == null && it.kind == EntryKind.JOURNAL }.takeIf { it > 0 }?.let { if (it == 1) "1 memory" else "$it memories" },
        ).joinToString(" · ")
        return FocusDay(
            stillOpen = stillOpen,
            nextUp = nextUp,
            nextUpCountdown = nextUp?.time?.let { FreeTime.countdown(now, it) } ?: "",
            later = withFreeTime(laterRows, from),
            earlier = earlier,
            earlierSummary = summary,
            done = done,
            total = tasks.size,
            spent = spent,
            isToday = true,
        )
    }

    /** Rows with "Free 20:45 – 21:30" lines where there's half an hour or more. */
    private fun withFreeTime(rows: List<DayRow>, from: LocalTime): List<LaterItem> {
        val busy = rows.filter { it.money == null && it.time != null && (it.kind == EntryKind.TASK || it.kind == EntryKind.EVENT) }
            .map { it.time!! to (it.durationMinutes ?: if (it.kind == EntryKind.EVENT) 60 else 15) }
        val gaps = FreeTime.gaps(from, busy)
        val items = rows.map { LaterItem.Row(it) as LaterItem }.toMutableList()
        gaps.forEach { g ->
            val label = "Free ${g.start.label()} – ${g.end?.label() ?: "midnight"}"
            val at = items.indexOfFirst { it is LaterItem.Row && it.row.time != null && !it.row.time.isBefore(g.end ?: LocalTime.MAX) }
            if (at >= 0) items.add(at, LaterItem.Free(label)) else items += LaterItem.Free(label)
        }
        return items
    }

    private fun DayRow.isOpenTask() =
        money == null && kind == EntryKind.TASK && state == EntryState.OPEN

    /** When an event ends (events default to an hour). Null for all-day events. */
    private fun DayRow.eventEnd(): LocalTime? {
        val start = time ?: return null
        val end = start.plusMinutes((durationMinutes ?: 60).toLong())
        return if (end < start) LocalTime.MAX else end // runs past midnight
    }

    /** "To do": open tasks, and events that haven't ended (all-day events all day). */
    private fun DayRow.isToDo(isToday: Boolean, now: LocalTime): Boolean = when {
        money != null -> false
        kind == EntryKind.TASK -> state == EntryState.OPEN
        kind == EntryKind.EVENT -> !isToday || eventEnd()?.let { it > now } ?: true
        else -> false
    }

    private fun itemsFor(s: PlannerSnapshot, date: LocalDate, today: LocalDate): List<DayItem> =
        Planner.dayItems(date, today, s.entries, s.series, s.records)

    private fun dotFor(items: List<DayItem>, day: LocalDate, today: LocalDate): Color? {
        val states = items.mapNotNull {
            when (it) {
                is DayItem.Single -> if (it.entry.kind == EntryKind.TASK) it.entry.state else null
                is DayItem.Occurrence -> it.state
            }
        }
        if (states.isEmpty()) return if (items.isNotEmpty()) PlannerColors.Missed else null
        return when {
            !day.isBefore(today) -> PlannerColors.Missed
            states.all { it == EntryState.DONE || it == EntryState.SKIPPED } -> PlannerColors.Done
            else -> PlannerColors.Accent
        }
    }

    private fun duration(minutes: Int?) = "${minutes ?: AutoTime.DEFAULT_DURATION} min"

    private fun DayItem.toRow(s: PlannerSnapshot, today: LocalDate): DayRow = when (this) {
        is DayItem.Single -> DayRow(
            key = "e:${entry.id}",
            kind = entry.kind,
            title = entry.title,
            description = entry.description,
            meta = listOfNotNull(
                if (entry.migrationCount > 0) "migrated ${entry.migrationCount}×" else null,
            ).joinToString(" · "),
            timeLabel = entry.time?.label() ?: "",
            trailing = when {
                entry.kind == EntryKind.TASK && entry.time != null -> duration(entry.durationMinutes)
                entry.kind == EntryKind.EVENT && entry.time == null -> "All day"
                else -> ""
            },
            importance = entry.importance,
            state = entry.state,
            repeating = false,
            entryId = entry.id,
            seriesId = null,
            date = entry.date ?: today,
            time = entry.time,
            durationMinutes = entry.durationMinutes,
        )
        is DayItem.Occurrence -> {
            val consistency = Planner.consistency(series, s.records[series.id].orEmpty(), today)
            DayRow(
                key = "s:${series.id}:$date",
                kind = series.kind,
                title = title,
                description = description,
                timeLabel = sortTime?.label() ?: "",
                trailing = when {
                    series.kind == EntryKind.TASK && sortTime != null -> duration(series.durationMinutes)
                    series.kind == EntryKind.EVENT && sortTime == null -> "All day"
                    else -> ""
                },
                meta = listOfNotNull(
                    repository.describe(series).replaceFirstChar { it.lowercase() },
                    dueSince?.let { "due since ${it.withWeek()}" },
                    if (state == EntryState.MISSED) "missed" else null,
                    if (consistency.scheduled > 0) "done ${consistency.done} of ${consistency.scheduled}" else null,
                ).joinToString(" · "),
                state = state,
                repeating = true,
                entryId = null,
                seriesId = series.id,
                date = date,
                time = sortTime,
                durationMinutes = series.durationMinutes,
                isRoutine = series.isRoutine,
            )
        }
    }

    private fun MoneyItem.toRow() = DayRow(
        key = "m:$id",
        kind = EntryKind.NOTE,
        title = title,
        description = "",
        meta = "",
        timeLabel = time.label(),
        state = EntryState.OPEN,
        repeating = false,
        entryId = null,
        seriesId = null,
        date = date,
        time = time,
        money = this,
    )

    /** Routine progress for the day ("3 of 7 steps"). */
    private fun DayRow.withRoutine(
        steps: Map<String, List<com.ivy.planner.domain.RoutineStep>>,
        states: Map<String, Map<String, com.ivy.planner.domain.StepState>>,
    ): DayRow {
        val id = seriesId ?: return this
        val list = steps[id] ?: return this
        if (!isRoutine) return this
        val progress = com.ivy.planner.domain.RoutineProgress(list, states["$id@$date"].orEmpty())
        val done = if (state == EntryState.DONE) progress.total else progress.done
        return copy(routine = done to progress.total, meta = (listOf("$done of ${progress.total} steps") + meta).filter { it.isNotBlank() }.joinToString(" · "))
    }

    /** Adds board / collection tags, people and photos from the library. */
    private fun DayRow.withLibrary(lib: Library?): DayRow {
        if (lib == null) return this
        val owner = entryId ?: seriesId ?: return this
        val collections = lib.collectionsOf[owner].orEmpty().mapNotNull { lib.collection(it) }
        val people = entryId?.let { lib.peopleOf[it] }.orEmpty().mapNotNull { lib.person(it)?.name }
        return copy(
            tags = collections.map { RowTag(it.name, Color(it.color)) },
            meta = (listOf(meta) + people).filter { it.isNotBlank() }.joinToString(" · "),
            photos = entryId?.let { lib.photosOf[it] }.orEmpty().map { library.photoFile(it) },
        )
    }

    override fun onEvent(event: DayEvent) {
        when (event) {
            is DayEvent.SelectDate -> {
                selected = event.date
                PlannerSelection.date = event.date
            }
            is DayEvent.Toggle -> viewModelScope.launch {
                val row = event.row
                if (row.money != null || row.kind != EntryKind.TASK || row.routine != null) return@launch
                val newState = if (row.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE
                if (row.entryId != null) {
                    repository.setState(row.entryId, newState)
                } else if (row.seriesId != null) {
                    repository.setOccurrenceState(row.seriesId, row.date, newState)
                }
            }
            is DayEvent.RapidLog -> viewModelScope.launch { repository.rapidLog(event.text, selected, library) }
            is DayEvent.OverdueDone -> viewModelScope.launch { repository.setState(event.id, EntryState.DONE) }
            is DayEvent.OverdueMove -> viewModelScope.launch {
                repository.move(event.id, event.date, today = LocalDate.now())
            }
            is DayEvent.OverdueDrop -> viewModelScope.launch { repository.setState(event.id, EntryState.DROPPED) }
            DayEvent.ToggleFilter -> {
                todoOnly = !todoOnly
                prefs.todoOnly = todoOnly
            }
            DayEvent.Refresh -> refresh++
            DayEvent.ToggleLayout -> {
                focusLayout = !focusLayout
                prefs.focusLayout = focusLayout
            }
            is DayEvent.Later -> viewModelScope.launch {
                val row = event.row
                val today = LocalDate.now()
                if (row.entryId != null) {
                    repository.move(row.entryId, today, today = today)
                } else if (row.seriesId != null) {
                    val slot = repository.autoTime(today, row.durationMinutes ?: 15, excludeId = row.seriesId)
                    repository.editOccurrence(row.seriesId, row.date, null, null, slot)
                }
            }
            is DayEvent.Tomorrow -> viewModelScope.launch {
                val row = event.row
                if (row.entryId != null) {
                    repository.reschedule(row.entryId, LocalDate.now().plusDays(1), row.time ?: LocalTime.of(9, 0))
                } else if (row.seriesId != null) {
                    repository.setOccurrenceState(row.seriesId, row.date, EntryState.SKIPPED)
                }
            }
        }
    }
}
