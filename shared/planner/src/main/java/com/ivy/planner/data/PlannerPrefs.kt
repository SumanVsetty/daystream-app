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

    /** Day tab layout: "Now & next" (true, default) or the classic timeline (false). */
    var focusLayout: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_LAYOUT, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FOCUS_LAYOUT, value).apply()
        }

    /** Your names for importance levels 1–4 (Lifely-style defaults until changed). */
    var importanceLabels: List<String>
        get() = prefs.getString(KEY_IMPORTANCE, null)?.split("\n")?.takeIf { it.size == 4 }
            ?: com.ivy.planner.domain.ImportanceLabels.defaults
        set(value) {
            prefs.edit().putString(KEY_IMPORTANCE, value.take(4).joinToString("\n") { it.replace("\n", " ").trim() }).apply()
        }

    /**
     * Wallet tags linked to a person or collection (owner id → tag ids), when you've chosen them.
     * Owners without an entry are linked to the tag with the same name.
     */
    var tagLinks: Map<String, Set<String>>
        get() = prefs.getStringSet(KEY_TAG_LINKS, emptySet()).orEmpty()
            .mapNotNull { s -> s.split('|').takeIf { it.size == 2 } }
            .groupBy({ it[0] }, { it[1] })
            .mapValues { (_, v) -> v.filter { it.isNotEmpty() }.toSet() }
        set(value) {
            // an owner with no tags keeps an empty marker, so "none" isn't replaced by the name match
            val flat = value.flatMap { (owner, tags) -> if (tags.isEmpty()) listOf("$owner|") else tags.map { "$owner|$it" } }
            prefs.edit().putStringSet(KEY_TAG_LINKS, flat.toSet()).apply()
        }

    /** Automatic backups: the folder (a document-tree address), every how many days, and the last run. */
    var autoBackupFolder: String?
        get() = prefs.getString(KEY_AUTO_FOLDER, null)
        set(value) {
            prefs.edit().putString(KEY_AUTO_FOLDER, value ?: "").apply()
        }
    var autoBackupDays: Int
        get() = prefs.getInt(KEY_AUTO_DAYS, 0)
        set(value) {
            prefs.edit().putInt(KEY_AUTO_DAYS, value).apply()
        }
    var autoBackupLast: String?
        get() = prefs.getString(KEY_AUTO_LAST, null)
        set(value) {
            prefs.edit().putString(KEY_AUTO_LAST, value ?: "").apply()
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
        const val KEY_FOCUS_LAYOUT = "day_layout_focus"
        const val KEY_IMPORTANCE = "importance_labels"
        const val KEY_TAG_LINKS = "wallet_tag_links"
        const val KEY_AUTO_FOLDER = "auto_backup_folder"
        const val KEY_AUTO_DAYS = "auto_backup_days"
        const val KEY_AUTO_LAST = "auto_backup_last"
        const val KEY_SNOOZES = "reminder_snoozes"
        const val KEY_CODES = "reminder_alarm_codes"
        const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"
    }
}
