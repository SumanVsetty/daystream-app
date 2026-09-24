package com.ivy.planner.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.isoWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class WeekDayLine(val symbol: String, val title: String, val state: EntryState, val kind: EntryKind)

@Immutable
data class WeekDaySummary(val date: LocalDate, val lines: ImmutableList<WeekDayLine>)

@Immutable
data class WeekState(
    val week: IsoWeek,
    val today: LocalDate,
    val done: Int,
    val total: Int,
    val weekTasks: ImmutableList<Entry>,
    val days: ImmutableList<WeekDaySummary>,
)

sealed interface WeekEvent {
    data class SetWeek(val week: IsoWeek) : WeekEvent
    data class AddWeekTask(val title: String) : WeekEvent
    data class AssignToDay(val id: String, val date: LocalDate) : WeekEvent
    data class ToggleWeekTask(val entry: Entry) : WeekEvent
}

@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<WeekState, WeekEvent>() {

    private var week by mutableStateOf(LocalDate.now().isoWeek())

    @Composable
    override fun uiState(): WeekState {
        val today = LocalDate.now()
        val w = week
        val snapshot by remember(w, today) {
            repository.observe(w.monday, w.sunday, today, w)
        }.collectAsState(initial = null)
        val s = snapshot ?: return WeekState(w, today, 0, 0, persistentListOf(), persistentListOf())

        val days = w.days.map { day ->
            val items = Planner.dayItems(day, today, s.entries, s.series, s.records)
            WeekDaySummary(day, items.map { it.toLine() }.toImmutableList())
        }
        val weekTasks = Planner.weekTasks(s.weekLevel, w)
        val taskStates = days.flatMap { d -> d.lines.filter { it.kind == EntryKind.TASK }.map { it.state } } +
            weekTasks.map { it.state }
        return WeekState(
            week = w,
            today = today,
            done = taskStates.count { it == EntryState.DONE },
            total = taskStates.count { it != EntryState.SKIPPED },
            weekTasks = weekTasks.toImmutableList(),
            days = days.toImmutableList(),
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
        return WeekDayLine(symbol, title, state, kind)
    }

    override fun onEvent(event: WeekEvent) {
        when (event) {
            is WeekEvent.SetWeek -> week = event.week
            is WeekEvent.AddWeekTask -> viewModelScope.launch {
                repository.quickAdd(EntryKind.TASK, event.title, date = null, week = week)
            }
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
