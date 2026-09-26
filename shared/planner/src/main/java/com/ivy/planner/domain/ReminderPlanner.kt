package com.ivy.planner.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Something on a given day that can have reminders: a one-off entry or one day of a series. */
data class ReminderTarget(
    /** The entry id, or the series id for repeating tasks. */
    val ownerId: String,
    val isSeries: Boolean,
    val date: LocalDate,
    val time: LocalTime,
    val title: String,
    val kind: EntryKind,
    val durationMinutes: Int?,
    /** For a repeating task, the day its record belongs to (differs from [date] when moved). */
    val recordDate: LocalDate = date,
) {
    /** Identifies this day's reminder, e.g. for snoozes and notification ids. */
    val key: String get() = "$ownerId@$date"
}

/** One alarm to schedule. */
data class ReminderAlarm(
    val key: String,
    val target: ReminderTarget,
    val fireAt: LocalDateTime,
    val minutesBefore: Int,
    val snoozed: Boolean = false,
)

object ReminderPlanner {
    /** How far ahead alarms are scheduled; the app refreshes them regularly. */
    const val WINDOW_HOURS = 48L

    /** Reminder choices offered in the editor (minutes before; 0 = at the time). */
    val CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

    fun label(minutesBefore: Int): String = when {
        minutesBefore == 0 -> "At the time"
        minutesBefore < 60 -> "$minutesBefore min before"
        minutesBefore == 60 -> "1 hour before"
        minutesBefore < 1440 -> "${minutesBefore / 60} hours before"
        minutesBefore == 1440 -> "1 day before"
        else -> "${minutesBefore / 1440} days before"
    }

    /**
     * Alarms due after [now] and within the window. A snooze replaces that day's
     * other reminders: the target fires once, at the snooze time.
     */
    fun plan(
        now: LocalDateTime,
        targets: List<ReminderTarget>,
        remindersOf: Map<String, List<Int>>,
        snoozes: Map<String, LocalDateTime>,
    ): List<ReminderAlarm> {
        val until = now.plusHours(WINDOW_HOURS)
        val alarms = mutableListOf<ReminderAlarm>()
        targets.forEach { t ->
            val snooze = snoozes[t.key]
            if (snooze != null) {
                if (snooze.isAfter(now) && !snooze.isAfter(until)) {
                    alarms += ReminderAlarm(t.key, t, snooze, 0, snoozed = true)
                }
                return@forEach
            }
            val start = LocalDateTime.of(t.date, t.time)
            remindersOf[t.ownerId].orEmpty().distinct().forEach { before ->
                val fire = start.minusMinutes(before.toLong())
                if (fire.isAfter(now) && !fire.isAfter(until)) alarms += ReminderAlarm(t.key, t, fire, before)
            }
        }
        return alarms.sortedBy { it.fireAt }
    }

    /** Notification text, e.g. "19:00 · 15 min" or "In 10 min · 19:00". */
    fun text(alarm: ReminderAlarm, now: LocalDateTime): String {
        val t = alarm.target
        val time = "%02d:%02d".format(t.time.hour, t.time.minute)
        val start = LocalDateTime.of(t.date, t.time)
        val until = Duration.between(now, start).toMinutes()
        val lead = when {
            alarm.snoozed || until <= 0 -> null
            until < 60 -> "In $until min"
            t.date == now.toLocalDate() -> null
            t.date == now.toLocalDate().plusDays(1) -> "Tomorrow"
            else -> null
        }
        val duration = if (t.kind == EntryKind.TASK) "${t.durationMinutes ?: AutoTime.DEFAULT_DURATION} min" else null
        return listOfNotNull(lead, time, duration).joinToString(" · ")
    }
}
