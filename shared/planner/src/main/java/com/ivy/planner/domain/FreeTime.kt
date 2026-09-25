package com.ivy.planner.domain

import java.time.LocalTime

/** Free stretches in the rest of a day, for the "Free 20:45 – 21:30" hints. */
object FreeTime {
    /** A free stretch from [start] until [end] (null = midnight). */
    data class Gap(val start: LocalTime, val end: LocalTime?)

    /**
     * Gaps of at least [minMinutes] between [from] and midnight, around [busy]
     * (start time and duration in minutes). The last gap runs to midnight only
     * when [includeEvening] is set, so a quiet evening doesn't always show.
     */
    fun gaps(
        from: LocalTime,
        busy: List<Pair<LocalTime, Int>>,
        minMinutes: Int = 30,
        includeEvening: Boolean = false,
    ): List<Gap> {
        val dayEnd = 24 * 60
        var cursor = from.hour * 60 + from.minute
        val out = mutableListOf<Gap>()
        busy.map { (t, d) -> (t.hour * 60 + t.minute) to (t.hour * 60 + t.minute + maxOf(d, 1)) }
            .filter { it.second > cursor }
            .sortedBy { it.first }
            .forEach { (s, e) ->
                if (s - cursor >= minMinutes) out += Gap(time(cursor), time(s))
                cursor = maxOf(cursor, e)
            }
        if (includeEvening && dayEnd - cursor >= minMinutes && cursor < dayEnd) out += Gap(time(cursor), null)
        return out
    }

    private fun time(m: Int): LocalTime = LocalTime.of((m / 60).coerceAtMost(23), if (m >= 24 * 60) 59 else m % 60)

    /** "In 25 min", "In 2 h 10 min", "Now". */
    fun countdown(from: LocalTime, to: LocalTime): String {
        val m = (to.hour * 60 + to.minute) - (from.hour * 60 + from.minute)
        return when {
            m <= 0 -> "Now"
            m < 60 -> "In $m min"
            m % 60 == 0 -> "In ${m / 60} h"
            else -> "In ${m / 60} h ${m % 60} min"
        }
    }
}
