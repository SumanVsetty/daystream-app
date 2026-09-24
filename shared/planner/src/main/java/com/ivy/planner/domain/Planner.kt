package com.ivy.planner.domain

import java.time.LocalDate
import java.time.LocalTime

/** The kinds of entries that live on the timeline. */
enum class EntryKind { TASK, EVENT, NOTE, JOURNAL }

/**
 * The state of a task or of one occurrence of a repeating task.
 * MISSED is never stored: it's derived for past occurrences nobody acted on.
 */
enum class EntryState { OPEN, DONE, DROPPED, SKIPPED, MISSED }

/** Importance levels (Lifely-style), 0 = none. */
object Importance {
    const val NONE = 0
    const val COOL = 1
    const val UNUSUAL = 2
    const val IMPORTANT = 3
    const val LIFE_CHANGING = 4
}

/**
 * A one-off entry: task, event, note or journal entry.
 * A task can belong to a whole week ([week] set, [date] null) before it's pulled into a day.
 */
data class Entry(
    val id: String,
    val kind: EntryKind,
    val title: String,
    val description: String = "",
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val week: IsoWeek? = null,
    val state: EntryState = EntryState.OPEN,
    val importance: Int = Importance.NONE,
    val migrationCount: Int = 0,
    val collectionId: String? = null,
    val completedAt: Long? = null,
)

/** A repeating task or routine: the "master" that owns the schedule. */
data class Series(
    val id: String,
    val kind: EntryKind,
    val title: String,
    val description: String = "",
    val time: LocalTime? = null,
    val schedule: RepeatSchedule,
    val collectionId: String? = null,
    val paused: Boolean = false,
    val stepCount: Int = 0,
)

/**
 * What's stored for a single day of a series, only when something happened:
 * it was done or skipped, or edited "only today".
 */
data class OccurrenceRecord(
    val seriesId: String,
    val date: LocalDate,
    val state: EntryState,
    val titleOverride: String? = null,
    val descriptionOverride: String? = null,
    val timeOverride: LocalTime? = null,
    val completedAt: Long? = null,
    val stepsDone: Int = 0,
)

/** One line on a Day log. */
sealed interface DayItem {
    val sortTime: LocalTime?
    val title: String

    data class Single(val entry: Entry) : DayItem {
        override val sortTime get() = entry.time
        override val title get() = entry.title
    }

    data class Occurrence(
        val series: Series,
        val date: LocalDate,
        val state: EntryState,
        val record: OccurrenceRecord?,
        /** For after-completion series that are late: the date it became due. */
        val dueSince: LocalDate? = null,
    ) : DayItem {
        override val sortTime get() = record?.timeOverride ?: series.time
        override val title get() = record?.titleOverride ?: series.title
        val description get() = record?.descriptionOverride ?: series.description
    }
}

/** Done / scheduled over a recent window, e.g. "done 26 of last 30 days". */
data class Consistency(val done: Int, val scheduled: Int)

/**
 * Pure planning logic: which items appear on a given day, what's overdue,
 * and how consistent a repeating task has been. No Android or database code here,
 * so it's fully unit tested.
 */
object Planner {

    /** State of a series occurrence, applying the missed-day rule (a new day starts at midnight). */
    fun occurrenceState(date: LocalDate, record: OccurrenceRecord?, today: LocalDate): EntryState = when {
        record != null && record.state != EntryState.OPEN -> record.state
        date.isBefore(today) -> EntryState.MISSED
        else -> EntryState.OPEN
    }

    /**
     * Items for [date]: one-off entries dated that day, plus occurrences of every active series.
     * [recordsBySeries] holds the stored records for that series (any dates).
     */
    fun dayItems(
        date: LocalDate,
        today: LocalDate,
        entries: List<Entry>,
        series: List<Series>,
        recordsBySeries: Map<String, List<OccurrenceRecord>>,
    ): List<DayItem> {
        val singles = entries
            .filter { it.date == date && it.state != EntryState.DROPPED }
            .map { DayItem.Single(it) }

        val occurrences = series.filterNot { it.paused }.mapNotNull { s ->
            val records = recordsBySeries[s.id].orEmpty()
            if (s.schedule.isCalendarBased) {
                if (!s.schedule.occursOn(date)) return@mapNotNull null
                val record = records.firstOrNull { it.date == date }
                DayItem.Occurrence(s, date, occurrenceState(date, record, today), record)
            } else {
                afterCompletionItem(s, date, today, records)
            }
        }
        return (singles + occurrences).sortedWith(
            compareBy<DayItem>({ it.sortTime == null }, { it.sortTime }, { it.title.lowercase() }),
        )
    }

    /**
     * After-completion series: a completed occurrence shows on the day it was done;
     * the pending one shows on its due date, or on today if it's late (never "missed").
     */
    private fun afterCompletionItem(
        s: Series,
        date: LocalDate,
        today: LocalDate,
        records: List<OccurrenceRecord>,
    ): DayItem.Occurrence? {
        records.firstOrNull { it.date == date && it.state == EntryState.DONE }?.let {
            return DayItem.Occurrence(s, date, EntryState.DONE, it)
        }
        val lastDone = records.filter { it.state == EntryState.DONE }.maxOfOrNull { it.date }
        val due = s.schedule.dueAfterCompletion(lastDone)
        val showOn = if (due.isBefore(today)) today else due
        if (date != showOn) return null
        val end = s.schedule.end
        if (end is RepeatEnd.OnDate && due.isAfter(end.lastDate)) return null
        if (end is RepeatEnd.AfterCount && records.count { it.state == EntryState.DONE } >= end.count) return null
        return DayItem.Occurrence(
            series = s,
            date = date,
            state = EntryState.OPEN,
            record = null,
            dueSince = due.takeIf { it.isBefore(today) },
        )
    }

    /** One-off tasks dated before [today] that are still open: these "need a decision". */
    fun overdue(entries: List<Entry>, today: LocalDate): List<Entry> = entries
        .filter { it.kind == EntryKind.TASK && it.state == EntryState.OPEN }
        .filter { it.date != null && it.date.isBefore(today) }
        .sortedBy { it.date }

    /** Open tasks that belong to [week] but have no day yet. */
    fun weekTasks(entries: List<Entry>, week: IsoWeek): List<Entry> = entries
        .filter { it.kind == EntryKind.TASK && it.date == null && it.week == week }
        .filter { it.state == EntryState.OPEN || it.state == EntryState.DONE }

    /**
     * Open tasks from [week] (dated in that week, or week-level) for the weekly review.
     */
    fun reviewCandidates(entries: List<Entry>, week: IsoWeek): List<Entry> = entries
        .filter { it.kind == EntryKind.TASK && it.state == EntryState.OPEN }
        .filter { (it.date != null && it.date in week) || (it.date == null && it.week == week) }
        .sortedWith(compareBy({ it.date == null }, { it.date }))

    /** Consistency over the [days] before and including [today] (today only counts once done). */
    fun consistency(
        series: Series,
        records: List<OccurrenceRecord>,
        today: LocalDate,
        days: Long = 30,
    ): Consistency {
        val from = today.minusDays(days - 1)
        val byDate = records.associateBy { it.date }
        if (!series.schedule.isCalendarBased) {
            return Consistency(records.count { it.state == EntryState.DONE && !it.date.isBefore(from) }, 0)
        }
        val dates = series.schedule.occurrencesBetween(from, today)
            .filter { byDate[it]?.state != EntryState.SKIPPED }
            .filter { it.isBefore(today) || byDate[it]?.state == EntryState.DONE }
        return Consistency(
            done = dates.count { byDate[it]?.state == EntryState.DONE },
            scheduled = dates.size,
        )
    }
}
