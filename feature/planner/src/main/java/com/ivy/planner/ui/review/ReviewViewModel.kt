package com.ivy.planner.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ivy.planner.domain.Periods
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.isoWeek
import com.ivy.ui.ComposeViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Immutable
data class ReviewState(
    val week: IsoWeek,
    val thisWeek: IsoWeek,
    val doneCount: Int,
    val habitPercent: Int?,
    val remaining: ImmutableList<Entry>,
    val reviewed: Int,
    val loading: Boolean,
    /** "Closing week 38" or "Closing September 2026" */
    val title: String = "Closing week ${week.week}",
    /** Where Migrate sends a task: "Into week 39" or "Into October's goals" */
    val migrateTo: String = "Into week ${thisWeek.week}",
    /** "Week 38 is closed." */
    val closedText: String = "Week ${week.week} is closed.",
    val nothingText: String = "Nothing left open in week ${week.week}.",
    val goodbye: String = "Every open task has a decision. Have a good week ${thisWeek.week}.",
    /** What a task without a day is called: "Week 38 task" or "September goal" */
    val undatedLabel: String = "Week ${week.week} task",
)

sealed interface ReviewEvent {
    /** Review a month instead of last week. */
    data class SetMonth(val month: YearMonth) : ReviewEvent
    data class Done(val id: String) : ReviewEvent
    data class Migrate(val id: String) : ReviewEvent
    data class Schedule(val id: String, val date: LocalDate) : ReviewEvent
    data class Drop(val id: String) : ReviewEvent
}

/** Weekly review ("migration"): decide on every task left open last week. */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: PlannerRepository,
) : ComposeViewModel<ReviewState, ReviewEvent>() {

    private var reviewed by mutableIntStateOf(0)
    private var month by mutableStateOf<YearMonth?>(null)

    @Composable
    override fun uiState(): ReviewState {
        month?.let { return monthState(it) }
        val today = LocalDate.now()
        val thisWeek = today.isoWeek()
        val week = thisWeek.previous()
        val snapshot by remember(week, today) {
            repository.observe(week.monday, week.sunday, today, week)
        }.collectAsState(initial = null)
        val s = snapshot ?: return ReviewState(week, thisWeek, 0, null, persistentListOf(), reviewed, loading = true)

        val candidates = Planner.reviewCandidates(s.entries + s.weekLevel, week)
        val items = week.days.flatMap { Planner.dayItems(it, today, s.entries, s.series, s.records) }
        val oneOffDone = (s.entries + s.weekLevel).count { it.kind == EntryKind.TASK && it.state == EntryState.DONE }
        val occurrences = items.filterIsInstance<DayItem.Occurrence>().filter { it.state != EntryState.SKIPPED }
        val occDone = occurrences.count { it.state == EntryState.DONE }
        return ReviewState(
            week = week,
            thisWeek = thisWeek,
            doneCount = oneOffDone + occDone,
            habitPercent = if (occurrences.isEmpty()) null else occDone * 100 / occurrences.size,
            remaining = candidates.toImmutableList(),
            reviewed = reviewed,
            loading = false,
        )
    }

    /** Closing a month: open tasks dated in it (before today) and its unfinished goals. */
    @Composable
    private fun monthState(m: YearMonth): ReviewState {
        val today = LocalDate.now()
        val from = m.atDay(1)
        val to = m.atEndOfMonth()
        val snapshot by remember(m, today) { repository.observe(from, to, today, today.isoWeek()) }.collectAsState(initial = null)
        val goals by remember(m) { repository.observeMonthGoals(m) }.collectAsState(initial = emptyList())
        val monthName = m.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val next = m.plusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val week = today.isoWeek()
        val s = snapshot ?: return ReviewState(week, week, 0, null, persistentListOf(), reviewed, loading = true, title = "Closing $monthName ${m.year}")
        val open = s.entries.filter { it.kind == EntryKind.TASK && it.state == EntryState.OPEN && it.date?.isBefore(today) == true }
            .sortedBy { it.date } + goals.filter { it.state == EntryState.OPEN }
        val stats = Periods.stats(from, to, today, s.entries, s.series, s.records)
        return ReviewState(
            week = week,
            thisWeek = week,
            doneCount = stats.tasksDone + goals.count { it.state == EntryState.DONE },
            habitPercent = stats.routinePercent,
            remaining = open.toImmutableList(),
            reviewed = reviewed,
            loading = false,
            title = "Closing $monthName ${m.year}",
            migrateTo = "Into $next's goals",
            closedText = "$monthName is closed.",
            nothingText = "Nothing left open in $monthName.",
            goodbye = "Every open task has a decision. Have a good $next.",
            undatedLabel = "$monthName goal",
        )
    }

    override fun onEvent(event: ReviewEvent) {
        if (event is ReviewEvent.SetMonth) {
            month = event.month
            return
        }
        reviewed++
        viewModelScope.launch {
            val today = LocalDate.now()
            val m = month
            when (event) {
                is ReviewEvent.Done -> repository.setState(event.id, EntryState.DONE)
                is ReviewEvent.Drop -> repository.setState(event.id, EntryState.DROPPED)
                is ReviewEvent.Migrate ->
                    if (m != null) repository.migrateToMonth(event.id, m.plusMonths(1))
                    else repository.move(event.id, date = null, week = today.isoWeek(), today = today)
                is ReviewEvent.Schedule -> repository.move(event.id, event.date, today = today)
                is ReviewEvent.SetMonth -> Unit
            }
        }
    }
}
