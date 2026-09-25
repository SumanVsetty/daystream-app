package com.ivy.planner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    ],
    version = 1,
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

    companion object {
        const val NAME = "apeiro_planner.db"

        fun create(context: Context): PlannerDatabase =
            Room.databaseBuilder(context, PlannerDatabase::class.java, NAME).build()
    }
}
