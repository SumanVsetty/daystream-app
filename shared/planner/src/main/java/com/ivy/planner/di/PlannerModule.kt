package com.ivy.planner.di

import android.content.Context
import com.ivy.planner.data.PlannerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlannerModule {
    @Provides
    @Singleton
    fun providePlannerDatabase(@ApplicationContext context: Context): PlannerDatabase =
        PlannerDatabase.create(context)
}
