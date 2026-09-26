package com.ivy.planner.domain

import java.time.LocalDate
import java.time.YearMonth

/** A month as a number, e.g. 202609 for September 2026 (how month goals are stored). */
fun YearMonth.key(): Int = year * 100 + monthValue
fun monthOf(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)

/** How a month or year went. Percentages are null when there was nothing to count. */
data class PeriodStats(
    val tasksDone: Int,
    val tasksDue: Int,
    val routinesDone: Int,
    val routinesDue: Int,
    val memories: Int,
    val lifeChanging: Int,
) {
    val taskPercent: Int? get() = if (tasksDue == 0) null else tasksDone * 100 / tasksDue
    val routinePercent: Int? get() = if (routinesDue == 0) null else routinesDone * 100 / routinesDue
}

object Periods {
    /**
     * Stats for [from]..[to]. Only days up to [today] count as due, so a month in progress
     * isn't judged on days that haven't happened. Skipped days and dropped tasks don't count.
     */
    fun stats(
        from: LocalDate,
        to: LocalDate,
        today: LocalDate,
        entries: List<Entry>,
        series: List<Series>,
        records: Map<String, List<OccurrenceRecord>>,
    ): PeriodStats {
        val last = minOf(to, today)
        val inRange = entries.filter { e -> e.date?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
        val tasks = inRange.filter { it.kind == EntryKind.TASK && it.state != EntryState.DROPPED && !it.date!!.isAfter(last) }
        var tasksDone = tasks.count { it.state == EntryState.DONE }
        var tasksDue = tasks.size
        var routinesDone = 0
        var routinesDue = 0
        if (!last.isBefore(from)) {
            series.filter { it.kind == EntryKind.TASK && it.schedule.isCalendarBased }.forEach { s ->
                val byDate = records[s.id].orEmpty().associateBy { it.date }
                s.schedule.occurrencesBetween(from, last).forEach { d ->
                    val state = Planner.occurrenceState(d, byDate[d], today)
                    if (state == EntryState.SKIPPED) return@forEach
                    val done = state == EntryState.DONE
                    if (s.isRoutine) {
                        routinesDue++
                        if (done) routinesDone++
                    } else if (d.isBefore(today) || done) {
                        tasksDue++
                        if (done) tasksDone++
                    }
                }
            }
        }
        val journal = inRange.filter { it.kind == EntryKind.JOURNAL }
        return PeriodStats(
            tasksDone = tasksDone,
            tasksDue = tasksDue,
            routinesDone = routinesDone,
            routinesDue = routinesDue,
            memories = journal.size,
            lifeChanging = journal.count { it.importance == Importance.LIFE_CHANGING },
        )
    }

    /**
     * The notable entry of each day: the most important journal entry, else an event,
     * else a note; days with nothing notable are left out (the month log adds spending).
     */
    fun notableByDay(from: LocalDate, to: LocalDate, entries: List<Entry>): Map<LocalDate, Entry> =
        entries.filter { e -> e.date?.let { !it.isBefore(from) && !it.isAfter(to) } == true }
            .filter { it.kind == EntryKind.JOURNAL || it.kind == EntryKind.EVENT || it.kind == EntryKind.NOTE }
            .groupBy { it.date!! }
            .mapValues { (_, list) ->
                list.sortedWith(
                    compareBy<Entry>(
                        { when (it.kind) { EntryKind.JOURNAL -> 0; EntryKind.EVENT -> 1; else -> 2 } },
                        { -it.importance },
                        { it.time },
                    ),
                ).first()
            }

    /** Journal entries per month of [year] (index 0 = January). */
    fun memoriesByMonth(year: Int, entries: List<Entry>): List<Int> {
        val counts = IntArray(12)
        entries.filter { it.kind == EntryKind.JOURNAL && it.date?.year == year }.forEach { counts[it.date!!.monthValue - 1]++ }
        return counts.toList()
    }
}
