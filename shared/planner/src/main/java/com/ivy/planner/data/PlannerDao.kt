package com.ivy.planner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun findById(id: String): EntryEntity?

    /** Entries dated in [from]..[to] (epoch days). */
    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to")
    fun observeBetween(from: Long, to: Long): Flow<List<EntryEntity>>

    /** Open tasks dated before [today]: the "needs a decision" list. */
    @Query("SELECT * FROM entries WHERE kind = 'TASK' AND state = 'OPEN' AND date < :today ORDER BY date")
    fun observeOverdue(today: Long): Flow<List<EntryEntity>>

    /** Week-level tasks (no day yet). */
    @Query("SELECT * FROM entries WHERE date IS NULL AND week_year = :year AND week_num = :week")
    fun observeWeekLevel(year: Int, week: Int): Flow<List<EntryEntity>>

    /** Full-text search over title and description, newest first. */
    @Query(
        """
        SELECT entries.* FROM entries
        JOIN entries_fts ON entries.rowid = entries_fts.rowid
        WHERE entries_fts MATCH :query
        ORDER BY entries.date DESC
        """,
    )
    suspend fun search(query: String): List<EntryEntity>

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM entries WHERE date = :date")
    suspend fun onDate(date: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE date BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<EntryEntity>

    @Query("SELECT * FROM entries")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries")
    suspend fun all(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE month_key = :key AND date IS NULL")
    fun observeMonthGoals(key: Int): Flow<List<EntryEntity>>

    /** Journal entries and notes, newest first. */
    @Query("SELECT * FROM entries WHERE kind IN ('JOURNAL', 'NOTE') ORDER BY date DESC, time_minutes DESC, created_at DESC")
    fun observeJournal(): Flow<List<EntryEntity>>
}

@Dao
interface SeriesDao {
    @Upsert
    suspend fun upsert(series: SeriesEntity)

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun findById(id: String): SeriesEntity?

    @Query("SELECT * FROM series")
    fun observeAll(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM series")
    suspend fun all(): List<SeriesEntity>

    @Query("DELETE FROM series WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface OccurrenceDao {
    @Upsert
    suspend fun upsert(occurrence: OccurrenceEntity)

    @Query("SELECT * FROM occurrences WHERE series_id = :seriesId AND date = :date")
    suspend fun find(seriesId: String, date: Long): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE date >= :from")
    suspend fun since(from: Long): List<OccurrenceEntity>

    /** Records from [from] onwards: enough for day views and 30-day consistency. */
    @Query("SELECT * FROM occurrences WHERE date >= :from")
    fun observeSince(from: Long): Flow<List<OccurrenceEntity>>

    /** Latest completion per series (for after-completion repeats). */
    @Query("SELECT * FROM occurrences WHERE state = 'DONE' ORDER BY date DESC")
    fun observeCompletions(): Flow<List<OccurrenceEntity>>

    @Query("DELETE FROM occurrences WHERE series_id = :seriesId AND date = :date")
    suspend fun delete(seriesId: String, date: Long)

    @Query("DELETE FROM occurrences WHERE series_id = :seriesId")
    suspend fun deleteForSeries(seriesId: String)
}

@Dao
interface StepDao {
    @Upsert
    suspend fun upsert(step: StepEntity)

    @Query("SELECT * FROM steps WHERE series_id = :seriesId ORDER BY position")
    suspend fun forSeries(seriesId: String): List<StepEntity>

    @Query("SELECT * FROM steps WHERE entry_id = :entryId ORDER BY position")
    suspend fun forEntry(entryId: String): List<StepEntity>

    @Query("DELETE FROM steps WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM steps WHERE series_id IS NOT NULL ORDER BY position")
    fun observeRoutineSteps(): Flow<List<StepEntity>>

    @Query("DELETE FROM steps WHERE series_id = :seriesId")
    suspend fun deleteForSeries(seriesId: String)

    @Query("SELECT * FROM steps WHERE entry_id IS NOT NULL ORDER BY position")
    fun observeChecklists(): Flow<List<StepEntity>>

    @Query("DELETE FROM steps WHERE entry_id = :entryId")
    suspend fun deleteForEntry(entryId: String)

    @Query("UPDATE steps SET done = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean)
}

@Dao
interface OccurrenceStepDao {
    @Upsert
    suspend fun upsert(state: OccurrenceStepEntity)

    @Query("DELETE FROM occurrence_steps WHERE series_id = :seriesId AND date = :date AND step_id = :stepId")
    suspend fun delete(seriesId: String, date: Long, stepId: String)

    @Query("SELECT * FROM occurrence_steps WHERE date >= :from")
    fun observeSince(from: Long): Flow<List<OccurrenceStepEntity>>

    @Query("SELECT * FROM occurrence_steps WHERE series_id = :seriesId AND date = :date")
    suspend fun forDay(seriesId: String, date: Long): List<OccurrenceStepEntity>

    @Query("DELETE FROM occurrence_steps WHERE series_id = :seriesId AND date = :date")
    suspend fun clearDay(seriesId: String, date: Long)
}

@Dao
interface CollectionDao {
    @Upsert
    suspend fun upsert(collection: CollectionEntity)

    @Upsert
    suspend fun link(link: EntryCollectionEntity)

    @Query("DELETE FROM entry_collections WHERE owner_id = :ownerId")
    suspend fun unlinkAll(ownerId: String)

    @Query("SELECT * FROM collections WHERE archived = 0 ORDER BY position, name")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM entry_collections")
    fun observeLinks(): Flow<List<EntryCollectionEntity>>

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM entry_collections WHERE collection_id = :collectionId")
    suspend fun unlinkCollection(collectionId: String)

    @Query("SELECT * FROM collections")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT collection_id FROM entry_collections WHERE owner_id = :ownerId")
    suspend fun collectionsOf(ownerId: String): List<String>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun findById(id: String): CollectionEntity?
}

@Dao
interface PeopleDao {
    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Upsert
    suspend fun tag(link: EntryPersonEntity)

    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun findById(id: String): PersonEntity?

    @Query("SELECT * FROM entry_people")
    fun observeTags(): Flow<List<EntryPersonEntity>>

    @Query("SELECT person_id FROM entry_people WHERE entry_id = :entryId")
    suspend fun peopleOf(entryId: String): List<String>

    @Query("DELETE FROM entry_people WHERE entry_id = :entryId")
    suspend fun untagAll(entryId: String)

    @Query("DELETE FROM entry_people WHERE person_id = :personId")
    suspend fun untagPerson(personId: String)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AttachmentDao {
    @Upsert
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments ORDER BY created_at")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE entry_id = :entryId ORDER BY created_at")
    suspend fun forEntry(entryId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun findById(id: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TrackerDao {
    @Upsert
    suspend fun upsert(tracker: TrackerEntity)

    @Upsert
    suspend fun addReading(reading: ReadingEntity)

    @Query("SELECT * FROM trackers ORDER BY name")
    fun observeAll(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM readings WHERE tracker_id = :trackerId ORDER BY measured_at")
    fun observeReadings(trackerId: String): Flow<List<ReadingEntity>>
}

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE owner_id = :ownerId ORDER BY minutes_before DESC")
    suspend fun forOwner(ownerId: String): List<ReminderEntity>

    @Query("DELETE FROM reminders WHERE owner_id = :ownerId")
    suspend fun deleteForOwner(ownerId: String)

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>
}

@Dao
interface FocusDao {
    @Upsert
    suspend fun upsert(focus: FocusEntity)

    @Query("DELETE FROM day_focus WHERE date = :date AND owner_id = :ownerId")
    suspend fun delete(date: Long, ownerId: String)

    @Query("SELECT * FROM day_focus WHERE date = :date ORDER BY position")
    suspend fun forDay(date: Long): List<FocusEntity>

    @Query("SELECT * FROM day_focus")
    fun observeAll(): Flow<List<FocusEntity>>
}
