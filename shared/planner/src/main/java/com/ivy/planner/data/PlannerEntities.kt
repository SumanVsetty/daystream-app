package com.ivy.planner.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Apeiro planner database tables.
 * Dates are stored as epoch days (LocalDate.toEpochDay), times as minutes after midnight,
 * and timestamps as epoch milliseconds, so no type converters are needed.
 */

/** A one-off task, event, note or journal entry. */
@Serializable
@Entity(
    tableName = "entries",
    indices = [Index("date"), Index("week_year", "week_num"), Index("state")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val description: String = "",
    val date: Long? = null,
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int? = null,
    @ColumnInfo(name = "week_year") val weekYear: Int? = null,
    @ColumnInfo(name = "week_num") val weekNum: Int? = null,
    val state: String = "OPEN",
    val importance: Int = 0,
    @ColumnInfo(name = "migration_count") val migrationCount: Int = 0,
    @ColumnInfo(name = "reminder_minutes_before") val reminderMinutesBefore: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
    /** Month goals: the month they belong to, e.g. 202609. */
    @ColumnInfo(name = "month_key") val monthKey: Int? = null,
)

/** Full-text search index over entries (title and description). */
@Fts4(contentEntity = EntryEntity::class)
@Entity(tableName = "entries_fts")
data class EntryFts(
    val title: String,
    val description: String,
)

/** The master of a repeating task or routine. */
@Serializable
@Entity(tableName = "series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val description: String = "",
    @ColumnInfo(name = "time_minutes") val timeMinutes: Int? = null,
    val rule: String,
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "end_rule") val endRule: String = "N",
    val paused: Boolean = false,
    val importance: Int = 0,
    @ColumnInfo(name = "reminder_minutes_before") val reminderMinutesBefore: Int? = null,
    @ColumnInfo(name = "is_routine") val isRoutine: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
)

/** One day of a series, stored only when something happened or it was edited "only today". */
@Serializable
@Entity(
    tableName = "occurrences",
    primaryKeys = ["series_id", "date"],
    indices = [Index("date")],
)
data class OccurrenceEntity(
    @ColumnInfo(name = "series_id") val seriesId: String,
    val date: Long,
    val state: String,
    @ColumnInfo(name = "title_override") val titleOverride: String? = null,
    @ColumnInfo(name = "description_override") val descriptionOverride: String? = null,
    @ColumnInfo(name = "time_override") val timeOverride: Int? = null,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    /** JSON snapshot of the routine steps as they were that day (for truthful history). */
    @ColumnInfo(name = "steps_snapshot") val stepsSnapshot: String? = null,
    /** This day was moved to another day (epoch day), e.g. with "Tomorrow". */
    @ColumnInfo(name = "moved_to") val movedTo: Long? = null,
)

/** A step of a routine (or a checklist item). Generic: heading + description + optional extras. */
@Serializable
@Entity(tableName = "steps", indices = [Index("series_id"), Index("entry_id")])
data class StepEntity(
    @PrimaryKey val id: String,
    /** Owner: a series (routine) or a one-off entry (checklist). One of them is set. */
    @ColumnInfo(name = "series_id") val seriesId: String? = null,
    @ColumnInfo(name = "entry_id") val entryId: String? = null,
    val position: Int,
    val heading: String,
    val description: String = "",
    /** Quick facts as a newline-separated list, e.g. "3 sets\n10 reps". */
    val facts: String = "",
    @ColumnInfo(name = "highlight_title") val highlightTitle: String? = null,
    @ColumnInfo(name = "highlight_text") val highlightText: String? = null,
    /** NONE, COUNTDOWN or SETS */
    @ColumnInfo(name = "timer_type") val timerType: String = "NONE",
    @ColumnInfo(name = "timer_seconds") val timerSeconds: Int? = null,
    val sets: Int? = null,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int? = null,
    val link: String? = null,
    /** For checklists on one-off entries. */
    val done: Boolean = false,
)

/** Progress of a routine step on a given day. */
@Serializable
@Entity(tableName = "occurrence_steps", primaryKeys = ["series_id", "date", "step_id"])
data class OccurrenceStepEntity(
    @ColumnInfo(name = "series_id") val seriesId: String,
    val date: Long,
    @ColumnInfo(name = "step_id") val stepId: String,
    val state: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)

/** A collection: a task board (Taskito-style) or a life topic (Car, House, Suchet · School). */
@Serializable
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Int,
    /** BOARD or TOPIC */
    val type: String = "BOARD",
    @ColumnInfo(name = "cover_uri") val coverUri: String? = null,
    val position: Int = 0,
    val archived: Boolean = false,
)

@Serializable
@Entity(
    tableName = "entry_collections",
    primaryKeys = ["owner_id", "collection_id"],
    indices = [Index("collection_id")],
)
data class EntryCollectionEntity(
    /** An entry id or a series id. */
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
)

@Serializable
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
)

@Serializable
@Entity(
    tableName = "entry_people",
    primaryKeys = ["entry_id", "person_id"],
    indices = [Index("person_id")],
)
data class EntryPersonEntity(
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "person_id") val personId: String,
)

/** A photo or file attached to an entry, stored in the app's private files. */
@Serializable
@Entity(tableName = "attachments", indices = [Index("entry_id")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Something measured over time: weight, fasting blood sugar, ALT... */
@Serializable
@Entity(tableName = "trackers")
data class TrackerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    @ColumnInfo(name = "range_min") val rangeMin: Double? = null,
    @ColumnInfo(name = "range_max") val rangeMax: Double? = null,
    val decimals: Int = 1,
)

@Serializable
@Entity(tableName = "readings", indices = [Index("tracker_id"), Index("entry_id")])
data class ReadingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tracker_id") val trackerId: String,
    /** Optional: the entry this reading belongs to, e.g. "Blood test, 24 Sep". */
    @ColumnInfo(name = "entry_id") val entryId: String? = null,
    val value: Double,
    @ColumnInfo(name = "measured_at") val measuredAt: Long,
    val note: String = "",
)

/**
 * A reminder for an entry or a series: [minutesBefore] its time (0 = at the time).
 * An owner can have several reminders.
 */
@Serializable
@Entity(tableName = "reminders", indices = [Index("owner_id")])
data class ReminderEntity(
    @PrimaryKey val id: String,
    /** An entry id or a series id. */
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "minutes_before") val minutesBefore: Int,
)

/** One of a day's 3 most important tasks: the entry or series, on that day. */
@Serializable
@Entity(tableName = "day_focus", primaryKeys = ["date", "owner_id"])
data class FocusEntity(
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "owner_id") val ownerId: String,
    @ColumnInfo(name = "position") val position: Int,
)
