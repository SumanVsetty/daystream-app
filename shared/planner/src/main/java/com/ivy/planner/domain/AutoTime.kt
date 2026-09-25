package com.ivy.planner.domain

import java.time.LocalDate
import java.time.LocalTime

/**
 * "Every task has a time": a task without one gets the earliest free slot
 * between [DAY_START] and midnight. Today, the search starts from now
 * (rounded up to the next quarter hour), so nothing lands in the past.
 */
object AutoTime {
    val DAY_START: LocalTime = LocalTime.of(8, 0)
    const val DEFAULT_DURATION = 15
    private const val DAY_END_MINUTES = 24 * 60
    private const val STEP = 5

    /** A busy stretch of the day: start time and duration in minutes. */
    data class Busy(val start: LocalTime, val minutes: Int)

    fun roundUpToQuarter(time: LocalTime): Int {
        val m = time.hour * 60 + time.minute + if (time.second > 0 || time.nano > 0) 1 else 0
        return ((m + 14) / 15) * 15
    }

    /**
     * The earliest start for a task of [duration] minutes on [date] that doesn't overlap [busy].
     * Returns null when the day is full (the caller then picks the latest possible slot).
     */
    fun findSlot(
        date: LocalDate,
        today: LocalDate,
        now: LocalTime,
        busy: List<Busy>,
        duration: Int = DEFAULT_DURATION,
    ): LocalTime? {
        if (date.isBefore(today)) return null
        val dayStart = DAY_START.hour * 60 + DAY_START.minute
        val from = if (date == today) maxOf(dayStart, roundUpToQuarter(now)) else dayStart
        val intervals = busy.map {
            val s = it.start.hour * 60 + it.start.minute
            s until s + maxOf(it.minutes, 1)
        }
        var start = from
        while (start + duration <= DAY_END_MINUTES) {
            val end = start + duration
            val clash = intervals.firstOrNull { start < it.last + 1 && it.first < end }
            if (clash == null) return LocalTime.of(start / 60, start % 60)
            // jump past the clash, keeping starts on a 5-minute grid
            start = ((clash.last + 1 + STEP - 1) / STEP) * STEP
        }
        return null
    }

    /** [findSlot], or the latest start that still ends by midnight when the day is full. */
    fun assign(
        date: LocalDate,
        today: LocalDate,
        now: LocalTime,
        busy: List<Busy>,
        duration: Int = DEFAULT_DURATION,
    ): LocalTime = findSlot(date, today, now, busy, duration)
        ?: LocalTime.of(0, 0).plusMinutes((DAY_END_MINUTES - duration).toLong().coerceAtLeast(0))
}
