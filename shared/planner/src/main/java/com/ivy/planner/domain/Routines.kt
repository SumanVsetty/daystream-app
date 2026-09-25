package com.ivy.planner.domain

/** How a step is timed. */
enum class TimerType { NONE, COUNTDOWN, SETS }

/**
 * One step of a routine. Only [heading] is required; everything else is an optional
 * building block, so steps suit exercise, morning or night routines, work and more.
 */
data class RoutineStep(
    val id: String,
    val position: Int,
    val heading: String,
    val description: String = "",
    /** Short labels shown as chips, e.g. "3 sets", "10 min", "At the desk". */
    val facts: List<String> = emptyList(),
    val highlightTitle: String? = null,
    val highlightText: String? = null,
    val timer: TimerType = TimerType.NONE,
    /** Countdown length, or rest between sets. */
    val timerSeconds: Int? = null,
    val sets: Int? = null,
    val link: String? = null,
)

/** A step's state on one day. */
enum class StepState { DONE, SKIPPED }

/** Progress through a routine on one day. */
data class RoutineProgress(
    val steps: List<RoutineStep>,
    val states: Map<String, StepState>,
) {
    val total: Int get() = steps.size
    val done: Int get() = steps.count { states[it.id] == StepState.DONE }
    val handled: Int get() = steps.count { it.id in states }
    val skipped: Int get() = steps.count { states[it.id] == StepState.SKIPPED }
    val isComplete: Boolean get() = total > 0 && handled == total

    /** The first step not done or skipped yet (where "Continue" picks up), or null when all are handled. */
    val nextIndex: Int? get() = steps.indexOfFirst { it.id !in states }.takeIf { it >= 0 }

    /** "3 of 7 steps" */
    fun label(): String = "$done of $total steps"
}

object Routines {
    /** Approximate length in minutes, from step timers (countdowns, and sets with rest). */
    fun estimatedMinutes(steps: List<RoutineStep>): Int? {
        val seconds = steps.sumOf { s ->
            when (s.timer) {
                TimerType.COUNTDOWN -> s.timerSeconds ?: 0
                // about a minute per set, plus the rests between sets
                TimerType.SETS -> (s.sets ?: 1) * 60 + ((s.sets ?: 1) - 1) * (s.timerSeconds ?: 0)
                TimerType.NONE -> 60
            }
        }
        return if (steps.isEmpty()) null else maxOf(1, (seconds + 59) / 60)
    }

    /**
     * A day's snapshot of the steps as they were, so history stays true when a routine
     * changes later. One line per step: heading, then a tab and "D" / "S" / "-".
     */
    fun snapshot(progress: RoutineProgress): String = progress.steps.joinToString("\n") { s ->
        val mark = when (progress.states[s.id]) {
            StepState.DONE -> "D"
            StepState.SKIPPED -> "S"
            null -> "-"
        }
        s.heading.replace('\t', ' ').replace('\n', ' ') + "\t" + mark
    }

    fun readSnapshot(text: String?): List<Pair<String, StepState?>> = text.orEmpty().lines().filter { it.isNotBlank() }.map { line ->
        val heading = line.substringBeforeLast('\t')
        val state = when (line.substringAfterLast('\t', "-")) {
            "D" -> StepState.DONE
            "S" -> StepState.SKIPPED
            else -> null
        }
        heading to state
    }

    /** "1:05" style clock for timers. */
    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /** "3 sets" / "60 s rest" / "10 min" style facts derived from the timer, shown with the step's own facts. */
    fun timerFacts(step: RoutineStep): List<String> = when (step.timer) {
        TimerType.NONE -> emptyList()
        TimerType.COUNTDOWN -> listOfNotNull(step.timerSeconds?.let { duration(it) })
        TimerType.SETS -> listOfNotNull(step.sets?.let { "$it sets" }, step.timerSeconds?.let { "${duration(it)} rest" })
    }

    private fun duration(seconds: Int) = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> clock(seconds)
    }
}
