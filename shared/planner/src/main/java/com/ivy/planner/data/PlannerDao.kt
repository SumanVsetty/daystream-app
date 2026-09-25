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
}

@Dao
interface PeopleDao {
    @Upsert
    suspend fun upsert(person: PersonEntity)

    @Upsert
    suspend fun tag(link: EntryPersonEntity)

    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>
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
}
