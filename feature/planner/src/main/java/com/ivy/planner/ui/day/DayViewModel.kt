package com.ivy.planner.ui.day

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewModelScope
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.data.PlannerSnapshot
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.isoWeek
import com.ivy.planner.ui.PlannerColors
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
)

@Immutable
data class DayState(
    val date: LocalDate,
    val today: LocalDate,
    val summary: String,
    val rows: ImmutableList<DayRow>,
    val overdue: ImmutableList<Entry>,
    val dots: ImmutableMap<LocalDate, Color>,
)

sealed interface DayEvent {
    data class SelectDate(val date: LocalDate) : DayEvent
    data class Toggle(val row: DayRow) : DayEvent
    data class QuickAdd(val kind: EntryKind, val title: String) : DayEvent
    data class OverdueDone(val id: String) : DayEvent
    data class OverdueMove(val id: String, val date: LocalDate) : DayEvent
    data class OverdueDrop(val id: String) : DayEvent
}

@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<DayState, DayEvent>() {

    private var selected by mutableStateOf(LocalDate.now())

    @Composable
    override fun uiState(): DayState {
        val today = LocalDate.now()
        val date = selected
        val week = date.isoWeek()
        val snapshot by remember(week, today) {
            repository.observe(week.monday, week.sunday, today, week)
        }.collectAsState(initial = null)

        val s = snapshot ?: return DayState(date, today, "", persistentListOf(), persistentListOf(), persistentMapOf())
        val items = itemsFor(s, date, today)
        val rows = items.map { it.toRow(s, today) }
        val open = rows.count { it.kind == EntryKind.TASK && it.state == EntryState.OPEN }
        val done = rows.count { it.state == EntryState.DONE }
        return DayState(
            date = date,
            today = today,
            summary = "$open open · $done done",
            rows = rows.toImmutableList(),
            overdue = (if (date == today) s.overdue else emptyList()).toImmutableList(),
            dots = week.days.mapNotNull { d -> dotFor(itemsFor(s, d, today), d, today)?.let { d to it } }
                .toMap().toImmutableMap(),
        )
    }

    private fun itemsFor(s: PlannerSnapshot, date: LocalDate, today: LocalDate): List<DayItem> =
        Planner.dayItems(date, today, s.entries, s.series, s.records)

    private fun dotFor(items: List<DayItem>, day: LocalDate, today: LocalDate): Color? {
        val states = items.map {
            when (it) {
                is DayItem.Single -> if (it.entry.kind == EntryKind.TASK) it.entry.state else null
                is DayItem.Occurrence -> it.state
            }
        }.filterNotNull()
        if (states.isEmpty()) return if (items.isNotEmpty()) PlannerColors.Missed else null
        return when {
            !day.isBefore(today) -> PlannerColors.Missed
            states.all { it == EntryState.DONE || it == EntryState.SKIPPED } -> PlannerColors.Done
            else -> PlannerColors.Accent
        }
    }

    private fun DayItem.toRow(s: PlannerSnapshot, today: LocalDate): DayRow = when (this) {
        is DayItem.Single -> DayRow(
            key = "e:${entry.id}",
            kind = entry.kind,
            title = entry.title,
            description = entry.description,
            meta = listOfNotNull(
                entry.time?.label(),
                if (entry.kind == EntryKind.EVENT && entry.time == null) "All day" else null,
                if (entry.migrationCount > 0) "migrated ${entry.migrationCount}×" else null,
            ).joinToString(" · "),
            state = entry.state,
            repeating = false,
            entryId = entry.id,
            seriesId = null,
            date = entry.date ?: today,
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
                    repositoryDescribe(this),
                    dueSince?.let { "due since ${it.withWeek()}" },
                    if (state == EntryState.MISSED) "missed" else null,
                    if (consistency.scheduled > 0) "done ${consistency.done} of last ${consistency.scheduled}" else null,
                ).joinToString(" · "),
                state = state,
                repeating = true,
                entryId = null,
                seriesId = series.id,
                date = date,
            )
        }
    }

    private fun repositoryDescribe(o: DayItem.Occurrence): String =
        repository.describe(o.series).replaceFirstChar { it.lowercase() }

    override fun onEvent(event: DayEvent) {
        when (event) {
            is DayEvent.SelectDate -> selected = event.date
            is DayEvent.Toggle -> viewModelScope.launch {
                val row = event.row
                val newState = if (row.state == EntryState.DONE) EntryState.OPEN else EntryState.DONE
                if (row.entryId != null) {
                    repository.setState(row.entryId, newState)
                } else if (row.seriesId != null) {
                    repository.setOccurrenceState(row.seriesId, row.date, newState)
                }
            }
            is DayEvent.QuickAdd -> viewModelScope.launch {
                repository.quickAdd(event.kind, event.title, selected)
            }
            is DayEvent.OverdueDone -> viewModelScope.launch { repository.setState(event.id, EntryState.DONE) }
            is DayEvent.OverdueMove -> viewModelScope.launch {
                repository.move(event.id, event.date, today = LocalDate.now())
            }
            is DayEvent.OverdueDrop -> viewModelScope.launch { repository.setState(event.id, EntryState.DROPPED) }
        }
    }
}
