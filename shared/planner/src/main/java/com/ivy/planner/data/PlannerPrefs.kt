package com.ivy.planner.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Small planner preferences, e.g. the Day view filter. */
@Singleton
class PlannerPrefs @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("apeiro_planner", Context.MODE_PRIVATE)

    /** true = "To do" view, false = "Everything". */
    var todoOnly: Boolean
        get() = prefs.getBoolean(KEY_TODO_ONLY, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TODO_ONLY, value).apply()
        }

    /** Snoozed reminders: reminder key → when to remind again. Past snoozes are dropped. */
    var snoozes: Map<String, LocalDateTime>
        get() = prefs.getStringSet(KEY_SNOOZES, emptySet()).orEmpty().mapNotNull { s ->
            val i = s.lastIndexOf('|')
            if (i < 0) null else runCatching { s.substring(0, i) to LocalDateTime.parse(s.substring(i + 1)) }.getOrNull()
        }.toMap()
        set(value) {
            val keep = value.filterValues { it.isAfter(LocalDateTime.now().minusDays(1)) }
            prefs.edit().putStringSet(KEY_SNOOZES, keep.map { (k, v) -> "$k|$v" }.toSet()).apply()
        }

    fun snooze(key: String, until: LocalDateTime) {
        snoozes = snoozes + (key to until)
    }

    fun clearSnooze(key: String) {
        snoozes = snoozes - key
    }

    /** Request codes of the alarms currently scheduled, so they can be cancelled. */
    var scheduledCodes: Set<Int>
        get() = prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(value) {
            prefs.edit().putStringSet(KEY_CODES, value.map { it.toString() }.toSet()).apply()
        }

    /** Tasks you give a time yourself get this reminder (minutes before; null = none). */
    var defaultReminder: Int?
        get() = prefs.getInt(KEY_DEFAULT_REMINDER, 0).takeIf { it >= 0 }
        set(value) {
            prefs.edit().putInt(KEY_DEFAULT_REMINDER, value ?: -1).apply()
        }

    private companion object {
        const val KEY_TODO_ONLY = "day_filter_todo_only"
        const val KEY_SNOOZES = "reminder_snoozes"
        const val KEY_CODES = "reminder_alarm_codes"
        const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"
    }
}
