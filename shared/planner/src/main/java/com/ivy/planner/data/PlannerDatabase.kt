package com.ivy.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Apeiro's planner database, kept separate from Ivy's money database
 * so planner changes can never put the finance data at risk.
 */
@Database(
    entities = [
        EntryEntity::class,
        EntryFts::class,
        SeriesEntity::class,
        OccurrenceEntity::class,
        StepEntity::class,
        OccurrenceStepEntity::class,
        CollectionEntity::class,
        EntryCollectionEntity::class,
        PersonEntity::class,
        EntryPersonEntity::class,
        AttachmentEntity::class,
        TrackerEntity::class,
        ReadingEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun seriesDao(): SeriesDao
    abstract fun occurrenceDao(): OccurrenceDao
    abstract fun stepDao(): StepDao
    abstract fun collectionDao(): CollectionDao
    abstract fun peopleDao(): PeopleDao
    abstract fun trackerDao(): TrackerDao
    abstract fun backupDao(): PlannerBackupDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "apeiro_planner.db"

        /** v2: task/event durations and a reminders table. Existing data is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `duration_minutes` INTEGER")
                db.execSQL("ALTER TABLE `series` ADD COLUMN `duration_minutes` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `owner_id` TEXT NOT NULL, " +
                        "`minutes_before` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_owner_id` ON `reminders` (`owner_id`)")
            }
        }

        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
