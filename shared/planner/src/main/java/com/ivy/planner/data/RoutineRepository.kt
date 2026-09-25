package com.ivy.planner.data

import androidx.room.withTransaction
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.RoutineProgress
import com.ivy.planner.domain.RoutineStep
import com.ivy.planner.domain.Routines
import com.ivy.planner.domain.StepState
import com.ivy.planner.domain.TimerType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Routine steps and each day's progress through them. */
@Singleton
class RoutineRepository @Inject constructor(
    private val db: PlannerDatabase,
    private val planner: PlannerRepository,
) {
    fun newStepId(): String = UUID.randomUUID().toString()

    /** series id → its steps, in order. */
    fun observeSteps(): Flow<Map<String, List<RoutineStep>>> = db.stepDao().observeRoutineSteps().map { list ->
        list.groupBy({ it.seriesId!! }, { it.toStep() })
    }

    /** "seriesId@date" → step id → state, for days from [from]. */
    fun observeStates(from: LocalDate): Flow<Map<String, Map<String, StepState>>> =
        db.occurrenceStepDao().observeSince(from.toEpochDay()).map { list ->
            list.groupBy { "${it.seriesId}@${LocalDate.ofEpochDay(it.date)}" }
                .mapValues { (_, rows) -> rows.associate { it.stepId to it.state.toStepState() } }
        }

    suspend fun steps(seriesId: String): List<RoutineStep> = db.stepDao().forSeries(seriesId).map { it.toStep() }

    suspend fun progress(seriesId: String, date: LocalDate): RoutineProgress = RoutineProgress(
        steps = steps(seriesId),
        states = db.occurrenceStepDao().forDay(seriesId, date.toEpochDay()).associate { it.stepId to it.state.toStepState() },
    )

    /** Replaces a routine's steps (positions follow the list order). */
    suspend fun saveSteps(seriesId: String, steps: List<RoutineStep>) = db.withTransaction {
        db.stepDao().deleteForSeries(seriesId)
        steps.forEachIndexed { i, s -> db.stepDao().upsert(s.copy(position = i).toEntity(seriesId)) }
    }

    /** Marks a step done or skipped for a day (null clears it). */
    suspend fun setStep(seriesId: String, date: LocalDate, stepId: String, state: StepState?) {
        if (state == null) {
            db.occurrenceStepDao().delete(seriesId, date.toEpochDay(), stepId)
        } else {
            db.occurrenceStepDao().upsert(
                OccurrenceStepEntity(seriesId, date.toEpochDay(), stepId, state.name, System.currentTimeMillis()),
            )
        }
    }

    /**
     * Finishes the day's routine: the day counts as done, the steps are kept as they were,
     * and an optional "How did it go?" note lands on the Day log.
     */
    suspend fun finish(seriesId: String, title: String, date: LocalDate, note: String?) {
        val progress = progress(seriesId, date)
        planner.setOccurrenceState(seriesId, date, EntryState.DONE)
        db.occurrenceDao().find(seriesId, date.toEpochDay())?.let {
            db.occurrenceDao().upsert(it.copy(stepsSnapshot = Routines.snapshot(progress)))
        }
        if (!note.isNullOrBlank()) {
            planner.quickAdd(EntryKind.NOTE, "$title: ${note.trim()}", date)
        }
    }

    /** Starts the day again: clears step progress and the done mark. */
    suspend fun restart(seriesId: String, date: LocalDate) {
        db.occurrenceStepDao().clearDay(seriesId, date.toEpochDay())
        planner.setOccurrenceState(seriesId, date, EntryState.OPEN)
    }
}

private fun String.toStepState() = if (this == StepState.SKIPPED.name) StepState.SKIPPED else StepState.DONE

fun StepEntity.toStep() = RoutineStep(
    id = id,
    position = position,
    heading = heading,
    description = description,
    facts = facts.lines().filter { it.isNotBlank() },
    highlightTitle = highlightTitle,
    highlightText = highlightText,
    timer = TimerType.values().firstOrNull { it.name == timerType } ?: TimerType.NONE,
    timerSeconds = if (timerType == TimerType.SETS.name) restSeconds else timerSeconds,
    sets = sets,
    link = link,
)

fun RoutineStep.toEntity(seriesId: String) = StepEntity(
    id = id,
    seriesId = seriesId,
    position = position,
    heading = heading.trim(),
    description = description.trim(),
    facts = facts.joinToString("\n") { it.trim() },
    highlightTitle = highlightTitle?.trim()?.ifBlank { null },
    highlightText = highlightText?.trim()?.ifBlank { null },
    timerType = timer.name,
    timerSeconds = if (timer == TimerType.COUNTDOWN) timerSeconds else null,
    sets = if (timer == TimerType.SETS) sets else null,
    restSeconds = if (timer == TimerType.SETS) timerSeconds else null,
    link = link?.trim()?.ifBlank { null },
)
