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
        FocusEntity::class,
    ],
    version = 5,
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
    abstract fun attachmentDao(): AttachmentDao
    abstract fun occurrenceStepDao(): OccurrenceStepDao
    abstract fun focusDao(): FocusDao

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

        /** v3: month goals (tasks that belong to a month). Existing data is kept. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `entries` ADD COLUMN `month_key` INTEGER")
            }
        }

        /** v4: today's 3 (a small table of starred tasks per day). Existing data is kept. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `day_focus` (`date` INTEGER NOT NULL, `owner_id` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, PRIMARY KEY(`date`, `owner_id`))",
                )
            }
        }

        /** v5: a day of a repeating task can be moved to another day. Existing data is kept. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `occurrences` ADD COLUMN `moved_to` INTEGER")
            }
        }

        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
