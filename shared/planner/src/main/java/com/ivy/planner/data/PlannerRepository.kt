package com.ivy.planner.data

import com.ivy.planner.domain.AutoTime
import com.ivy.planner.domain.DayItem
import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.domain.OccurrenceRecord
import com.ivy.planner.domain.Planner
import com.ivy.planner.domain.RapidLogParser
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatEnd
import com.ivy.planner.domain.Series
import com.ivy.planner.domain.isoWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the planner screens need for a range of days. */
data class PlannerSnapshot(
    val entries: List<Entry>,
    val overdue: List<Entry>,
    val weekLevel: List<Entry>,
    val series: List<Series>,
    val records: Map<String, List<OccurrenceRecord>>,
)

@Singleton
class PlannerRepository @Inject constructor(
    private val db: PlannerDatabase,
) {
    private val entryDao get() = db.entryDao()
    private val seriesDao get() = db.seriesDao()
    private val occurrenceDao get() = db.occurrenceDao()

    private fun now() = System.currentTimeMillis()
    private fun newId() = UUID.randomUUID().toString()

    /**
     * Observes entries dated in [from]..[to], overdue tasks relative to [today],
     * week-level tasks of [week], all series, and occurrence records from 60 days
     * before [from] (enough for consistency stats and after-completion repeats).
     */
    fun observe(from: LocalDate, to: LocalDate, today: LocalDate, week: IsoWeek): Flow<PlannerSnapshot> {
        val entries = entryDao.observeBetween(from.toEpochDay(), to.toEpochDay())
        val overdue = entryDao.observeOverdue(today.toEpochDay())
        val weekLevel = entryDao.observeWeekLevel(week.year, week.week)
        val series = seriesDao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }
        val records = occurrenceDao.observeSince(minOf(from, today).minusDays(60).toEpochDay())
        return combine(entries, overdue, weekLevel, series, records) { e, o, w, s, r ->
            PlannerSnapshot(
                entries = e.map { it.toDomain() },
                overdue = o.map { it.toDomain() },
                weekLevel = w.map { it.toDomain() },
                series = s,
                records = r.map { it.toDomain() }.groupBy { it.seriesId },
            )
        }
    }

    // ---------------------------------------------------------------- one-off entries

    /** Rapid log: a task, event or note on [date], or a week-level task when [date] is null. */
    suspend fun quickAdd(
        kind: EntryKind,
        title: String,
        date: LocalDate?,
        week: IsoWeek? = date?.isoWeek(),
        time: LocalTime? = null,
        description: String = "",
        durationMinutes: Int? = null,
    ): String {
        val id = newId()
        val entry = Entry(
            id = id,
            kind = kind,
            title = title.trim(),
            description = description,
            date = date,
            time = time ?: defaultTime(kind, date, durationMinutes, excludeId = id),
            week = week,
            durationMinutes = durationMinutes,
        )
        entryDao.upsert(entry.toEntity(createdAt = now(), now = now()))
        return id
    }

    /**
     * Rapid log: understands day, time and duration in the text (see [RapidLogParser]).
     * Without a day it goes on [selectedDate].
     */
    suspend fun rapidLog(text: String, selectedDate: LocalDate?, library: LibraryRepository? = null, boardId: String? = null): String? {
        val parsed = RapidLogParser.parse(text, LocalDate.now())
        if (parsed.title.isBlank()) return null
        val id = quickAdd(
            kind = parsed.kind,
            title = parsed.title,
            date = parsed.date ?: selectedDate,
            week = (parsed.date ?: selectedDate)?.isoWeek(),
            time = parsed.time,
            durationMinutes = parsed.durationMinutes,
        )
        if (library != null) {
            val collections = library.resolveTags(parsed.tags) + listOfNotNull(boardId)
            if (collections.isNotEmpty()) library.setCollections(id, collections)
        }
        return id
    }

    /**
     * The time a new entry gets when none was given:
     * tasks take the earliest free slot (08:00–24:00, from now if today),
     * notes on today take the current time, events stay all-day.
     */
    private suspend fun defaultTime(kind: EntryKind, date: LocalDate?, durationMinutes: Int?, excludeId: String?): LocalTime? {
        if (date == null) return null
        return when (kind) {
            EntryKind.TASK -> autoTime(date, durationMinutes ?: AutoTime.DEFAULT_DURATION, excludeId)
            EntryKind.NOTE, EntryKind.JOURNAL ->
                if (date == LocalDate.now()) LocalTime.now().withSecond(0).withNano(0) else null
            EntryKind.EVENT -> null
        }
    }

    /** Earliest free slot on [date] given every timed task, event and repeating task that day. */
    suspend fun autoTime(date: LocalDate, durationMinutes: Int = AutoTime.DEFAULT_DURATION, excludeId: String? = null): LocalTime {
        val today = LocalDate.now()
        val busy = mutableListOf<AutoTime.Busy>()
        entryDao.onDate(date.toEpochDay())
            .map { it.toDomain() }
            .filter { it.id != excludeId && it.time != null && it.state != EntryState.DROPPED }
            .filter { it.kind == EntryKind.TASK || it.kind == EntryKind.EVENT }
            .forEach { busy += AutoTime.Busy(it.time!!, it.durationMinutes ?: AutoTime.DEFAULT_DURATION) }
        val series = seriesDao.all().mapNotNull { it.toDomain() }.filter { it.id != excludeId }
        val records = occurrenceDao.since(date.toEpochDay()).map { it.toDomain() }.groupBy { it.seriesId }
        Planner.dayItems(date, today, emptyList(), series, records)
            .filterIsInstance<DayItem.Occurrence>()
            .forEach { o -> o.sortTime?.let { busy += AutoTime.Busy(it, o.series.durationMinutes ?: AutoTime.DEFAULT_DURATION) } }
        return AutoTime.assign(date, today, LocalTime.now(), busy, durationMinutes)
    }

    suspend fun getEntry(id: String): Entry? = entryDao.findById(id)?.toDomain()

    fun observeJournal(): Flow<List<Entry>> = entryDao.observeJournal().map { list -> list.map { it.toDomain() } }

    suspend fun saveEntry(entry: Entry) {
        val existing = entryDao.findById(entry.id)
        val week = entry.date?.isoWeek() ?: entry.week
        val time = entry.time ?: defaultTime(entry.kind, entry.date, entry.durationMinutes, excludeId = entry.id)
        entryDao.upsert(
            entry.copy(
                week = week,
                time = time,
                title = entry.title.trim(),
            ).toEntity(createdAt = existing?.createdAt ?: now(), now = now()),
        )
    }

    suspend fun setImportance(id: String, level: Int) {
        val e = entryDao.findById(id) ?: return
        if (e.importance != level) entryDao.upsert(e.copy(importance = level, updatedAt = now()))
    }

    suspend fun setState(id: String, state: EntryState) {
        val e = entryDao.findById(id) ?: return
        entryDao.upsert(
            e.copy(
                state = state.name,
                completedAt = if (state == EntryState.DONE) now() else null,
                updatedAt = now(),
            ),
        )
    }

    /**
     * Moves a task to [date] (or to a week, when [date] is null).
     * Moving a task out of the past counts as a migration, like "&gt;" in a bullet journal.
     */
    suspend fun move(id: String, date: LocalDate?, week: IsoWeek? = date?.isoWeek(), today: LocalDate) {
        val e = entryDao.findById(id) ?: return
        val wasPast = e.date != null && e.date < today.toEpochDay()
        val wasOtherWeek = e.date == null && e.weekYear != null &&
            IsoWeek(e.weekYear, e.weekNum ?: 0) < today.isoWeek()
        // a task moved to a day gets a fresh free slot; a week-level task has no time
        val newTime = if (date != null && e.kind == EntryKind.TASK.name) {
            autoTime(date, e.durationMinutes ?: AutoTime.DEFAULT_DURATION, excludeId = id).toMinutes()
        } else if (date == null) {
            null
        } else {
            e.timeMinutes
        }
        entryDao.upsert(
            e.copy(
                date = date?.toEpochDay(),
                weekYear = week?.year,
                weekNum = week?.week,
                timeMinutes = newTime,
                migrationCount = e.migrationCount + if (wasPast || wasOtherWeek) 1 else 0,
                updatedAt = now(),
            ),
        )
    }

    suspend fun deleteEntry(id: String) = entryDao.delete(id)

    suspend fun search(text: String): List<Entry> {
        val terms = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        // prefix-match each word: "serv" finds "service"
        val query = terms.joinToString(" ") { it.replace("\"", "") + "*" }
        return entryDao.search(query).map { it.toDomain() }
    }

    // ---------------------------------------------------------------- repeating series

    suspend fun getSeries(id: String): Series? = seriesDao.findById(id)?.toDomain()

    fun observeSeries(): Flow<List<Series>> = seriesDao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    /** Creates or replaces a series as a whole (new series, or pause/resume). */
    suspend fun saveSeries(series: Series) {
        val existing = seriesDao.findById(series.id)
        val time = series.time ?: if (series.kind == EntryKind.TASK) {
            autoTime(series.schedule.start, series.durationMinutes ?: AutoTime.DEFAULT_DURATION, excludeId = series.id)
        } else {
            null
        }
        seriesDao.upsert(
            series.copy(
                time = time,
                title = series.title.trim(),
            ).toEntity(createdAt = existing?.createdAt ?: now(), now = now()),
        )
    }

    // ---------------------------------------------------------------- reminders

    suspend fun reminders(ownerId: String): List<Int> = db.reminderDao().forOwner(ownerId).map { it.minutesBefore }

    /** Replaces the reminders of an entry or series ([minutesBefore]: 0 = at the time). */
    suspend fun setReminders(ownerId: String, minutesBefore: List<Int>) {
        db.reminderDao().deleteForOwner(ownerId)
        minutesBefore.distinct().forEach { db.reminderDao().upsert(ReminderEntity(newId(), ownerId, it)) }
    }

    /**
     * "Today and all future days": ends the current series the day before [from] and
     * starts [updated] (a new series) on [from], so past days keep their history.
     * If the series starts on or after [from], it's simply replaced.
     */
    suspend fun updateSeriesFrom(original: Series, updated: Series, from: LocalDate): String {
        if (!original.schedule.start.isBefore(from)) {
            saveSeries(updated.copy(id = original.id, schedule = updated.schedule.copy(start = from)))
            return original.id
        }
        saveSeries(original.copy(schedule = original.schedule.copy(end = RepeatEnd.OnDate(from.minusDays(1)))))
        val newId = newId()
        saveSeries(updated.copy(id = newId, schedule = updated.schedule.copy(start = from)))
        return newId
    }

    /** Ends a series: no occurrences after [lastDate]; history is kept. */
    suspend fun endSeries(series: Series, lastDate: LocalDate) {
        saveSeries(series.copy(schedule = series.schedule.copy(end = RepeatEnd.OnDate(lastDate))))
    }

    suspend fun deleteSeries(id: String) {
        occurrenceDao.deleteForSeries(id)
        seriesDao.delete(id)
    }

    /** Ticks or unticks one day of a series. */
    suspend fun setOccurrenceState(seriesId: String, date: LocalDate, state: EntryState) {
        val existing = occurrenceDao.find(seriesId, date.toEpochDay())
        val hasOverrides = existing != null &&
            (existing.titleOverride != null || existing.descriptionOverride != null || existing.timeOverride != null)
        if (state == EntryState.OPEN && !hasOverrides) {
            occurrenceDao.delete(seriesId, date.toEpochDay())
            return
        }
        occurrenceDao.upsert(
            (existing ?: OccurrenceEntity(seriesId, date.toEpochDay(), state.name)).copy(
                state = state.name,
                completedAt = if (state == EntryState.DONE) now() else null,
            ),
        )
    }

    /** "Only today": overrides for a single day of a series. Nulls mean "use the master". */
    suspend fun editOccurrence(
        seriesId: String,
        date: LocalDate,
        title: String?,
        description: String?,
        time: LocalTime?,
    ) {
        val existing = occurrenceDao.find(seriesId, date.toEpochDay())
        occurrenceDao.upsert(
            (existing ?: OccurrenceEntity(seriesId, date.toEpochDay(), EntryState.OPEN.name)).copy(
                titleOverride = title,
                descriptionOverride = description,
                timeOverride = time?.toMinutes(),
            ),
        )
    }

    suspend fun newSeriesId(): String = newId()

    fun describe(series: Series): String = RepeatCodec.describe(series.schedule)
}
