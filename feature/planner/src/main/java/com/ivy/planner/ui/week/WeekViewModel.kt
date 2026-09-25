package com.ivy.planner.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.MoneyItem
import com.ivy.planner.ui.MoneySource
import com.ivy.planner.ui.formatMoney
import com.ivy.planner.ui.label
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Immutable
data class WeekDayLine(val symbol: String, val title: String, val state: EntryState, val kind: EntryKind)

@Immutable
data class WeekDaySummary(
    val date: LocalDate,
    val lines: ImmutableList<WeekDayLine>,
    /** e.g. "Spent ₹1,240", or null when nothing was spent. */
    val spend: String?,
)

@Immutable
data class WeekState(
    val week: IsoWeek,
    val today: LocalDate,
    val done: Int,
    val total: Int,
    val weekTasks: ImmutableList<Entry>,
    val days: ImmutableList<WeekDaySummary>,
    val todoOnly: Boolean,
)

sealed interface WeekEvent {
    data class SetWeek(val week: IsoWeek) : WeekEvent
    data class AddWeekTask(val title: String) : WeekEvent
    data class AssignToDay(val id: String, val date: LocalDate) : WeekEvent
    data class ToggleWeekTask(val entry: Entry) : WeekEvent
    data object ToggleFilter : WeekEvent
    data object Refresh : WeekEvent
}

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: PlannerRepository,
    private val moneySource: MoneySource,
    private val prefs: PlannerPrefs,
) : ComposeViewModel<WeekState, WeekEvent>() {

    private var week by mutableStateOf(LocalDate.now().isoWeek())
    private var todoOnly by mutableStateOf(prefs.todoOnly)
    private var refresh by mutableIntStateOf(0)

    @Composable
    override fun uiState(): WeekState {
        val today = LocalDate.now()
        val w = week
        val snapshot by remember(w, today) {
            repository.observe(w.monday, w.sunday, today, w)
        }.collectAsState(initial = null)
        val money by produceState(initialValue = emptyList<MoneyItem>(), w, refresh) {
            value = moneySource.between(w.monday, w.sunday)
        }
        val s = snapshot ?: return WeekState(w, today, 0, 0, persistentListOf(), persistentListOf(), todoOnly)

        // the Week tab is for planning: tasks, events and repeating tasks (no notes, journal or expenses)
        val days = w.days.map { day ->
            val items = Planner.dayItems(day, today, s.entries, s.series, s.records)
            val lines = items.map { it.toLine() }
                .filter { it.kind == EntryKind.TASK || it.kind == EntryKind.EVENT }
                .filter { !todoOnly || it.state == EntryState.OPEN }
            val spent = money.filter { it.date == day && !it.isIncome }
            val spend = spent.groupBy { it.currency }
                .map { (currency, list) -> formatMoney(list.sumOf { it.amount }, currency) }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" + ", prefix = "Spent ")
            WeekDaySummary(day, lines.toImmutableList(), spend)
        }
        val weekTasks = Planner.weekTasks(s.weekLevel, w).filter { !todoOnly || it.state == EntryState.OPEN }
        val taskStates = days.flatMap { d -> d.lines.filter { it.kind == EntryKind.TASK }.map { it.state } } +
            weekTasks.map { it.state }
        return WeekState(
            week = w,
            today = today,
            done = taskStates.count { it == EntryState.DONE },
            total = taskStates.count { it != EntryState.SKIPPED },
            weekTasks = weekTasks.toImmutableList(),
            days = days.toImmutableList(),
            todoOnly = todoOnly,
        )
    }

    private fun DayItem.toLine(): WeekDayLine {
        val (kind, state) = when (this) {
            is DayItem.Single -> entry.kind to entry.state
            is DayItem.Occurrence -> series.kind to state
        }
        val symbol = when {
            kind == EntryKind.EVENT -> "○"
            kind == EntryKind.NOTE || kind == EntryKind.JOURNAL -> "–"
            state == EntryState.DONE -> "×"
            state == EntryState.MISSED -> "·"
            this is DayItem.Single && entry.migrationCount > 0 -> ">"
            else -> "•"
        }
        val time = sortTime?.label()
        return WeekDayLine(symbol, if (time != null) "$time  $title" else title, state, kind)
    }

    override fun onEvent(event: WeekEvent) {
        when (event) {
            is WeekEvent.SetWeek -> week = event.week
            is WeekEvent.AddWeekTask -> viewModelScope.launch {
                // "send quote on friday" lands on Friday; otherwise it's a task for the week
                val parsed = RapidLogParser.parse(event.title, LocalDate.now())
                if (parsed.title.isBlank()) return@launch
                if (parsed.date != null) {
                    repository.quickAdd(parsed.kind, parsed.title, parsed.date, time = parsed.time, durationMinutes = parsed.durationMinutes)
                } else {
                    repository.quickAdd(parsed.kind, parsed.title, date = null, week = week, durationMinutes = parsed.durationMinutes)
                }
            }
            WeekEvent.ToggleFilter -> {
                todoOnly = !todoOnly
                prefs.todoOnly = todoOnly
            }
            WeekEvent.Refresh -> refresh++
            is WeekEvent.AssignToDay -> viewModelScope.launch {
                repository.move(event.id, event.date, today = LocalDate.now())
            }
            is WeekEvent.ToggleWeekTask -> viewModelScope.launch {
                repository.setState(
                    event.entry.id,
                    if (event.entry.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE,
                )
            }
        }
    }
}
