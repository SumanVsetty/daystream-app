package com.ivy.planner.ui.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
data class ReviewState(
    val week: IsoWeek,
    val thisWeek: IsoWeek,
    val doneCount: Int,
    val habitPercent: Int?,
    val remaining: ImmutableList<Entry>,
    val reviewed: Int,
    val loading: Boolean,
)

sealed interface ReviewEvent {
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

    @Composable
    override fun uiState(): ReviewState {
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

    override fun onEvent(event: ReviewEvent) {
        reviewed++
        viewModelScope.launch {
            val today = LocalDate.now()
            when (event) {
                is ReviewEvent.Done -> repository.setState(event.id, EntryState.DONE)
                is ReviewEvent.Drop -> repository.setState(event.id, EntryState.DROPPED)
                is ReviewEvent.Migrate -> repository.move(event.id, date = null, week = today.isoWeek(), today = today)
                is ReviewEvent.Schedule -> repository.move(event.id, event.date, today = today)
            }
        }
    }
}
