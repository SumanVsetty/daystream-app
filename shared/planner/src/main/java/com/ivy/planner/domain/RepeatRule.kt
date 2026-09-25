package com.ivy.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * How a repeating task (or routine) repeats.
 * Every rule has an [interval]: "every 2 weeks" has interval 2.
 */
sealed interface RepeatRule {
    val interval: Int

    /** Every [interval] days. */
    data class Daily(override val interval: Int = 1) : RepeatRule

    /** Every [interval] weeks, on the given [days]. */
    data class Weekly(
        override val interval: Int = 1,
        val days: Set<DayOfWeek>,
    ) : RepeatRule

    /** Every [interval] months on day [dayOfMonth]. Short months use their last day. */
    data class MonthlyOnDay(
        override val interval: Int = 1,
        val dayOfMonth: Int,
    ) : RepeatRule

    /**
     * Every [interval] months on the [nth] [dayOfWeek], e.g. the 4th Thursday.
     * [nth] is 1..4, or [LAST] for the last such weekday of the month.
     */
    data class MonthlyOnWeekday(
        override val interval: Int = 1,
        val nth: Int,
        val dayOfWeek: DayOfWeek,
    ) : RepeatRule {
        companion object {
            const val LAST = -1
        }
    }

    /** Every [interval] months on the last day of the month. */
    data class MonthlyLastDay(override val interval: Int = 1) : RepeatRule

    /**
     * [interval] days after the previous occurrence was completed.
     * Not tied to the calendar, so these occurrences are never "missed".
     */
    data class AfterCompletion(override val interval: Int) : RepeatRule
}

/** When a repeating series stops. */
sealed interface RepeatEnd {
    data object Never : RepeatEnd

    /** The series ends on [lastDate] (inclusive). */
    data class OnDate(val lastDate: LocalDate) : RepeatEnd

    /** The series ends after [count] occurrences. */
    data class AfterCount(val count: Int) : RepeatEnd
}

/**
 * A complete repeat schedule: a rule, a start date and an end.
 */
data class RepeatSchedule(
    val rule: RepeatRule,
    val start: LocalDate,
    val end: RepeatEnd = RepeatEnd.Never,
) {
    init {
        require(rule.interval >= 1) { "Interval must be at least 1" }
        if (rule is RepeatRule.Weekly) require(rule.days.isNotEmpty()) { "Pick at least one day" }
        if (rule is RepeatRule.MonthlyOnDay) require(rule.dayOfMonth in 1..31)
        if (rule is RepeatRule.MonthlyOnWeekday) {
            require(rule.nth in 1..4 || rule.nth == RepeatRule.MonthlyOnWeekday.LAST)
        }
        if (end is RepeatEnd.AfterCount) require(end.count >= 1)
    }

    val isCalendarBased: Boolean get() = rule !is RepeatRule.AfterCompletion

    /** Happens once only, on [start] (used for one-off routines). */
    val isOnce: Boolean get() = end is RepeatEnd.OnDate && end.lastDate == start && rule is RepeatRule.Daily

    companion object {
        /** A schedule that happens once, on [date]. */
        fun once(date: LocalDate) = RepeatSchedule(RepeatRule.Daily(1), date, RepeatEnd.OnDate(date))
    }

    /** Whether the rule's pattern matches [date], ignoring the end condition. */
    private fun matchesPattern(date: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        return when (val r = rule) {
            is RepeatRule.Daily ->
                ChronoUnit.DAYS.between(start, date) % r.interval == 0L

            is RepeatRule.Weekly ->
                date.dayOfWeek in r.days &&
                    ChronoUnit.WEEKS.between(start.weekStart(), date.weekStart()) % r.interval == 0L

            is RepeatRule.MonthlyOnDay ->
                monthMatches(date, r.interval) &&
                    date.dayOfMonth == minOf(r.dayOfMonth, date.lengthOfMonth())

            is RepeatRule.MonthlyOnWeekday ->
                monthMatches(date, r.interval) &&
                    date.dayOfWeek == r.dayOfWeek &&
                    if (r.nth == RepeatRule.MonthlyOnWeekday.LAST) {
                        date.dayOfMonth + 7 > date.lengthOfMonth()
                    } else {
                        (date.dayOfMonth - 1) / 7 + 1 == r.nth
                    }

            is RepeatRule.MonthlyLastDay ->
                monthMatches(date, r.interval) && date.dayOfMonth == date.lengthOfMonth()

            is RepeatRule.AfterCompletion -> false
        }
    }

    private fun monthMatches(date: LocalDate, interval: Int): Boolean =
        ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)) % interval == 0L

    /** Whether an occurrence falls on [date]. Always false for [RepeatRule.AfterCompletion]. */
    fun occursOn(date: LocalDate): Boolean {
        if (!matchesPattern(date)) return false
        return when (val e = end) {
            RepeatEnd.Never -> true
            is RepeatEnd.OnDate -> !date.isAfter(e.lastDate)
            is RepeatEnd.AfterCount -> occurrenceIndex(date) < e.count
        }
    }

    /** 0-based index of the occurrence on [date], counting from [start]. */
    private fun occurrenceIndex(date: LocalDate): Int {
        var count = 0
        var d = start
        while (d.isBefore(date)) {
            if (matchesPattern(d)) count++
            d = d.plusDays(1)
        }
        return count
    }

    /** All occurrence dates in [from]..[to] (inclusive). */
    fun occurrencesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
        if (!isCalendarBased || to.isBefore(from)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var d = maxOf(from, start)
        while (!d.isAfter(to)) {
            if (occursOn(d)) result += d
            d = d.plusDays(1)
        }
        return result
    }

    /** The first occurrence on or after [date], searching up to [horizonDays] ahead. */
    fun nextOnOrAfter(date: LocalDate, horizonDays: Long = 3 * 366): LocalDate? {
        if (!isCalendarBased) return null
        var d = maxOf(date, start)
        val limit = date.plusDays(horizonDays)
        while (!d.isAfter(limit)) {
            if (occursOn(d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** The next [n] occurrences on or after [date], for the "Next dates" preview. */
    fun preview(date: LocalDate, n: Int = 5): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var d: LocalDate? = date
        while (d != null && result.size < n) {
            d = nextOnOrAfter(d) ?: break
            result += d
            d = d.plusDays(1)
        }
        return result
    }

    /**
     * For [RepeatRule.AfterCompletion]: the due date given when it was last completed.
     * Before the first completion, it's due on [start].
     */
    fun dueAfterCompletion(lastCompleted: LocalDate?): LocalDate {
        val r = rule as RepeatRule.AfterCompletion
        return lastCompleted?.plusDays(r.interval.toLong()) ?: start
    }
}
