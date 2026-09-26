package com.ivy.planner.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import com.ivy.base.backup.BackupSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.Base64
import javax.inject.Inject

/** Everything in the planner database, as stored in the backup file. */
@Serializable
data class PlannerBackupData(
    /** 1: files inside the data as base64. 2: files beside the data in the zip. */
    @SerialName("version") val version: Int = 1,
    @SerialName("entries") val entries: List<EntryEntity> = emptyList(),
    @SerialName("series") val series: List<SeriesEntity> = emptyList(),
    @SerialName("occurrences") val occurrences: List<OccurrenceEntity> = emptyList(),
    @SerialName("steps") val steps: List<StepEntity> = emptyList(),
    @SerialName("occurrenceSteps") val occurrenceSteps: List<OccurrenceStepEntity> = emptyList(),
    @SerialName("collections") val collections: List<CollectionEntity> = emptyList(),
    @SerialName("entryCollections") val entryCollections: List<EntryCollectionEntity> = emptyList(),
    @SerialName("people") val people: List<PersonEntity> = emptyList(),
    @SerialName("entryPeople") val entryPeople: List<EntryPersonEntity> = emptyList(),
    /** Attachment records; the files themselves are stored in the zip under "apeiro_files/". */
    @SerialName("attachments") val attachments: List<AttachmentEntity> = emptyList(),
    @SerialName("trackers") val trackers: List<TrackerEntity> = emptyList(),
    @SerialName("readings") val readings: List<ReadingEntity> = emptyList(),
    @SerialName("reminders") val reminders: List<ReminderEntity> = emptyList(),
    /** Only in version 1 backups: files as base64. Still read, so old backups restore fully. */
    @SerialName("files") val files: Map<String, String> = emptyMap(),
    /** Wallet tags linked to people and collections. */
    @SerialName("tagLinks") val tagLinks: Map<String, List<String>> = emptyMap(),
    /** Preferences worth keeping: importance names, Day layout, default reminder. */
    @SerialName("prefs") val prefs: BackupPrefs? = null,
)

@Serializable
data class BackupPrefs(
    @SerialName("importanceLabels") val importanceLabels: List<String>? = null,
    @SerialName("focusLayout") val focusLayout: Boolean? = null,
    @SerialName("defaultReminder") val defaultReminder: Int? = null,
)

@Dao
interface PlannerBackupDao {
    @Query("SELECT * FROM entries") suspend fun entries(): List<EntryEntity>
    @Query("SELECT * FROM series") suspend fun series(): List<SeriesEntity>
    @Query("SELECT * FROM occurrences") suspend fun occurrences(): List<OccurrenceEntity>
    @Query("SELECT * FROM steps") suspend fun steps(): List<StepEntity>
    @Query("SELECT * FROM occurrence_steps") suspend fun occurrenceSteps(): List<OccurrenceStepEntity>
    @Query("SELECT * FROM collections") suspend fun collections(): List<CollectionEntity>
    @Query("SELECT * FROM entry_collections") suspend fun entryCollections(): List<EntryCollectionEntity>
    @Query("SELECT * FROM people") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM entry_people") suspend fun entryPeople(): List<EntryPersonEntity>
    @Query("SELECT * FROM attachments") suspend fun attachments(): List<AttachmentEntity>
    @Query("SELECT * FROM trackers") suspend fun trackers(): List<TrackerEntity>
    @Query("SELECT * FROM readings") suspend fun readings(): List<ReadingEntity>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>

    @Upsert suspend fun upsertEntries(items: List<EntryEntity>)
    @Upsert suspend fun upsertSeries(items: List<SeriesEntity>)
    @Upsert suspend fun upsertOccurrences(items: List<OccurrenceEntity>)
    @Upsert suspend fun upsertSteps(items: List<StepEntity>)
    @Upsert suspend fun upsertOccurrenceSteps(items: List<OccurrenceStepEntity>)
    @Upsert suspend fun upsertCollections(items: List<CollectionEntity>)
    @Upsert suspend fun upsertEntryCollections(items: List<EntryCollectionEntity>)
    @Upsert suspend fun upsertPeople(items: List<PersonEntity>)
    @Upsert suspend fun upsertEntryPeople(items: List<EntryPersonEntity>)
    @Upsert suspend fun upsertAttachments(items: List<AttachmentEntity>)
    @Upsert suspend fun upsertTrackers(items: List<TrackerEntity>)
    @Upsert suspend fun upsertReadings(items: List<ReadingEntity>)
    @Upsert suspend fun upsertReminders(items: List<ReminderEntity>)
}

/**
 * Adds the planner to the app's backup file under "apeiroPlanner".
 * Import merges by id, so restoring the same backup twice doesn't create duplicates.
 */
class PlannerBackupSection @Inject constructor(
    private val db: PlannerDatabase,
    private val json: Json,
    private val store: AttachmentStore,
    private val prefs: PlannerPrefs,
) : BackupSection {
    override val key: String = KEY

    private val dao get() = db.backupDao()

    override suspend fun export(): JsonElement = json.encodeToJsonElement(
        PlannerBackupData.serializer(),
        PlannerBackupData(
            version = 2,
            entries = dao.entries(),
            series = dao.series(),
            occurrences = dao.occurrences(),
            steps = dao.steps(),
            occurrenceSteps = dao.occurrenceSteps(),
            collections = dao.collections(),
            entryCollections = dao.entryCollections(),
            people = dao.people(),
            entryPeople = dao.entryPeople(),
            attachments = dao.attachments(),
            trackers = dao.trackers(),
            readings = dao.readings(),
            reminders = dao.reminders(),
            tagLinks = prefs.tagLinks.mapValues { it.value.toList() },
            prefs = BackupPrefs(
                importanceLabels = prefs.importanceLabels,
                focusLayout = prefs.focusLayout,
                defaultReminder = prefs.defaultReminder ?: -1,
            ),
        ),
    )

    /** Photos, PDFs, people's photos and collection covers, stored beside the data in the zip. */
    override suspend fun exportFiles(): Map<String, File> =
        (dao.attachments().map { it.fileName } + dao.people().mapNotNull { it.photoUri } + dao.collections().mapNotNull { it.coverUri })
            .distinct()
            .map { it to store.file(it) }
            .filter { it.second.exists() }
            .associate { (name, file) -> "$FILES_DIR/$name" to file }

    override suspend fun importFiles(dir: File) {
        File(dir, FILES_DIR).listFiles()?.forEach { f -> runCatching { store.copyIn(f.name, f) } }
    }

    override suspend fun import(data: JsonElement) {
        val backup = json.decodeFromJsonElement(PlannerBackupData.serializer(), data)
        if (backup.tagLinks.isNotEmpty()) prefs.tagLinks = prefs.tagLinks + backup.tagLinks.mapValues { it.value.toSet() }
        backup.prefs?.let { p ->
            p.importanceLabels?.takeIf { it.size == 4 }?.let { prefs.importanceLabels = it }
            p.focusLayout?.let { prefs.focusLayout = it }
            p.defaultReminder?.let { prefs.defaultReminder = it.takeIf { m -> m >= 0 } }
        }
        backup.files.forEach { (name, b64) ->
            runCatching { store.write(name, Base64.getDecoder().decode(b64)) }
        }
        db.withTransaction {
            dao.upsertEntries(backup.entries)
            dao.upsertSeries(backup.series)
            dao.upsertOccurrences(backup.occurrences)
            dao.upsertSteps(backup.steps)
            dao.upsertOccurrenceSteps(backup.occurrenceSteps)
            dao.upsertCollections(backup.collections)
            dao.upsertEntryCollections(backup.entryCollections)
            dao.upsertPeople(backup.people)
            dao.upsertEntryPeople(backup.entryPeople)
            dao.upsertAttachments(backup.attachments)
            dao.upsertTrackers(backup.trackers)
            dao.upsertReadings(backup.readings)
            dao.upsertReminders(backup.reminders)
        }
    }

    companion object {
        const val KEY = "apeiroPlanner"
        const val FILES_DIR = "apeiro_files"
    }
}
