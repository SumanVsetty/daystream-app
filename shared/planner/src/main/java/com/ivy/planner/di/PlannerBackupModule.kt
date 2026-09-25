package com.ivy.planner.di

import com.ivy.base.backup.BackupSection
import com.ivy.planner.data.PlannerBackupSection
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class PlannerBackupModule {
    @Binds
    @IntoSet
    abstract fun bindPlannerBackupSection(section: PlannerBackupSection): BackupSection
}
