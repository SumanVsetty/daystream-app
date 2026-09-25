package com.ivy.planner.ui.reminders

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** After a restart, an app update, or a clock / time-zone change, alarms are planned again. */
@AndroidEntryPoint
class SystemEventsReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { scheduler.schedule() }
            } finally {
                pending.finish()
            }
        }
    }
}
