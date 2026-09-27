package com.ivy.wallet

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ivy.base.legacy.appContext
import com.ivy.planner.data.CrashLog
import com.ivy.planner.data.StartupGuard
import com.ivy.planner.ui.reminders.ReminderSync
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber
import timber.log.Timber.DebugTree

/**
 * Created by iliyan on 24.02.18.
 */
@HiltAndroidApp
class IvyAndroidApp : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Keeps planner reminder alarms in sync with tasks and repeating tasks. */
    @Inject
    lateinit var reminderSync: ReminderSync

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContext = this
        // a crash leaves a report on the phone; newer data than this build understands is never opened
        CrashLog.install(this)
        StartupGuard.check(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
        if (!StartupGuard.dataTooNew) reminderSync.start()
    }
}
