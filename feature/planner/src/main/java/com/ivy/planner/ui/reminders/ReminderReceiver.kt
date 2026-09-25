package com.ivy.planner.ui.reminders

import android.content.Context
import android.content.Intent
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.ReminderPlanner
import com.ivy.planner.domain.ReminderAlarm
import com.ivy.planner.domain.ReminderTarget
import com.ivy.planner.domain.EntryKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** Handles reminder alarms, the notification buttons, and events that need re-planning. */
@AndroidEntryPoint
class ReminderReceiver : HiltBroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: ReminderNotifier
    @Inject lateinit var repository: PlannerRepository
    @Inject lateinit var prefs: PlannerPrefs

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // Hilt injection happens here
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(intent: Intent) {
        val info = ReminderInfo.from(intent)
        when (intent.action) {
            ACTION_FIRE -> if (info != null) {
                prefs.clearSnooze(info.key)
                val date = LocalDate.ofEpochDay(info.epochDay)
                val time = LocalTime.of(info.minuteOfDay / 60, info.minuteOfDay % 60)
                val target = ReminderTarget(info.ownerId, info.isSeries, date, time, info.title, if (info.isTask) EntryKind.TASK else EntryKind.EVENT, null)
                val text = ReminderPlanner.text(ReminderAlarm(info.key, target, LocalDateTime.now(), 0), LocalDateTime.now())
                notifier.show(info.copy(text = text))
                scheduler.schedule()
            }
            ACTION_DONE -> if (info != null) {
                notifier.cancel(info)
                prefs.clearSnooze(info.key)
                if (info.isSeries) {
                    repository.setOccurrenceState(info.ownerId, LocalDate.ofEpochDay(info.epochDay), EntryState.DONE)
                } else {
                    repository.setState(info.ownerId, EntryState.DONE)
                }
                scheduler.schedule()
            }
            ACTION_TOMORROW -> if (info != null) {
                notifier.cancel(info)
                prefs.clearSnooze(info.key)
                val time = LocalTime.of(info.minuteOfDay / 60, info.minuteOfDay % 60)
                repository.reschedule(info.ownerId, LocalDate.now().plusDays(1), time)
                scheduler.schedule()
            }
            else -> scheduler.schedule() // refresh, boot, time or time zone changed, app updated
        }
    }

    companion object {
        const val ACTION_FIRE = "com.ivy.planner.reminder.FIRE"
        const val ACTION_DONE = "com.ivy.planner.reminder.DONE"
        const val ACTION_TOMORROW = "com.ivy.planner.reminder.TOMORROW"
        const val ACTION_REFRESH = "com.ivy.planner.reminder.REFRESH"
    }
}
