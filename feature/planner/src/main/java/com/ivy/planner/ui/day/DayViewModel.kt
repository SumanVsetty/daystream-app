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
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.data.PlannerSnapshot
import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.MoneyItem
import com.ivy.planner.ui.MoneySource
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerSelection
import com.ivy.planner.ui.label
import com.ivy.planner.ui.withWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

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
    /** Softened: before "now" today and nothing left to do. */
    val dimmed: Boolean = false,
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
    /** Index in [rows] before which the "now" marker goes (today only). */
    val nowIndex: Int?,
    val nowLabel: String,
    /** Index in [rows] to scroll to when opening today (one hour before now). */
    val scrollIndex: Int?,
)

sealed interface DayEvent {
    data class SelectDate(val date: LocalDate) : DayEvent
    data class Toggle(val row: DayRow) : DayEvent
    data class RapidLog(val text: String) : DayEvent
    data class OverdueDone(val id: String) : DayEvent
    data class OverdueMove(val id: String, val date: LocalDate) : DayEvent
    data class OverdueDrop(val id: String) : DayEvent
    data object ToggleFilter : DayEvent
    /** Reload expenses (e.g. after returning from the money screens). */
    data object Refresh : DayEvent
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val moneySource: MoneySource,
    private val prefs: PlannerPrefs,
) : ComposeViewModel<DayState, DayEvent>() {

    private var selected by mutableStateOf(PlannerSelection.date)
    private var todoOnly by mutableStateOf(prefs.todoOnly)
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

        val s = snapshot ?: return DayState(
            date, today, "", persistentListOf(), persistentListOf(), persistentMapOf(),
            todoOnly, null, "", null,
        )
        val allRows = (itemsFor(s, date, today).map { it.toRow(s, today) } + money.filter { it.date == date }.map { it.toRow() })
            .sortedWith(compareBy<DayRow>({ it.time != null }, { it.time }, { it.title.lowercase() }))
        val isToday = date == today
        val visible = if (todoOnly) allRows.filter { it.isToDo(isToday, now) } else allRows
        val rows = visible.map { row -> row.copy(dimmed = row.shouldSoften(isToday, now)) }
        val open = allRows.count { it.isOpenTask() }
        val done = allRows.count { it.state == EntryState.DONE }
        val nowIndex = if (isToday) rows.indexOfFirst { it.time != null && it.time >= now }.let { if (it < 0) rows.size else it } else null
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
            nowIndex = nowIndex,
            nowLabel = "Now ${now.label()}",
            scrollIndex = scrollIndex,
        )
    }

    private fun DayRow.isOpenTask() =
        money == null && kind == EntryKind.TASK && state == EntryState.OPEN

    /** When an event ends (events default to an hour). Null for all-day events. */
    private fun DayRow.eventEnd(): LocalTime? {
        val start = time ?: return null
        val end = start.plusMinutes((durationMinutes ?: 60).toLong())
        return if (end < start) LocalTime.MAX else end // runs past midnight
    }

    /**
     * Softening combines status and time:
     * done / missed / skipped tasks always; today, notes and expenses before now,
     * and timed events once they've ended. Open tasks and all-day events never.
     */
    private fun DayRow.shouldSoften(isToday: Boolean, now: LocalTime): Boolean = when {
        money != null -> isToday && time != null && time < now
        kind == EntryKind.TASK -> state == EntryState.DONE || state == EntryState.MISSED || state == EntryState.SKIPPED
        kind == EntryKind.EVENT -> isToday && eventEnd()?.let { it <= now } ?: false
        else -> isToday && (time == null || time < now) // notes and journal: records of the past
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
                entry.time?.label(),
                if (entry.kind == EntryKind.TASK && entry.time != null) duration(entry.durationMinutes) else null,
                if (entry.kind == EntryKind.EVENT && entry.time == null) "All day" else null,
                if (entry.migrationCount > 0) "migrated ${entry.migrationCount}×" else null,
            ).joinToString(" · "),
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
                meta = listOfNotNull(
                    sortTime?.label(),
                    if (series.kind == EntryKind.TASK && sortTime != null) duration(series.durationMinutes) else null,
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
            )
        }
    }

    private fun MoneyItem.toRow() = DayRow(
        key = "m:$id",
        kind = EntryKind.NOTE,
        title = title,
        description = "",
        meta = listOf(time.label(), account).filter { it.isNotBlank() }.joinToString(" · "),
        state = EntryState.OPEN,
        repeating = false,
        entryId = null,
        seriesId = null,
        date = date,
        time = time,
        money = this,
    )

    override fun onEvent(event: DayEvent) {
        when (event) {
            is DayEvent.SelectDate -> {
                selected = event.date
                PlannerSelection.date = event.date
            }
            is DayEvent.Toggle -> viewModelScope.launch {
                val row = event.row
                if (row.money != null || row.kind != EntryKind.TASK) return@launch
                val newState = if (row.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE
                if (row.entryId != null) {
                    repository.setState(row.entryId, newState)
                } else if (row.seriesId != null) {
                    repository.setOccurrenceState(row.seriesId, row.date, newState)
                }
            }
            is DayEvent.RapidLog -> viewModelScope.launch { repository.rapidLog(event.text, selected) }
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
        }
    }
}
