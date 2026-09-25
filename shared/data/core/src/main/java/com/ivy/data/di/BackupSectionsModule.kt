package com.ivy.data.di

import com.ivy.base.backup.BackupSection
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupSectionsModule {
    /** Declares the set so it may be empty when no feature registers a section. */
    @Multibinds
    abstract fun backupSections(): Set<BackupSection>
}
