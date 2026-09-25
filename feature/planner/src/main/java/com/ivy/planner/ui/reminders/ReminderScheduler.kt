package com.ivy.planner.ui.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.data.PlannerRepository
import com.ivy.planner.domain.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Android alarms in line with the planner: every reminder in the next 48 hours
 * gets an exact alarm, and a refresh alarm re-plans before the window runs out.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlannerRepository,
    private val prefs: PlannerPrefs,
) {
    private val lock = Mutex()
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    suspend fun schedule() = lock.withLock {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val targets = repository.reminderTargets(today, today.plusDays(2))
        val planned = ReminderPlanner.plan(now, targets, repository.allReminders(), prefs.snoozes)

        // cancel what was scheduled before, then schedule the new plan
        prefs.scheduledCodes.forEach { code ->
            pending(code, Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE))?.let { alarms.cancel(it) }
        }
        val codes = mutableSetOf<Int>()
        planned.forEach { alarm ->
            val t = alarm.target
            val info = ReminderInfo(
                key = alarm.key,
                ownerId = t.ownerId,
                isSeries = t.isSeries,
                epochDay = t.date.toEpochDay(),
                minuteOfDay = t.time.hour * 60 + t.time.minute,
                title = t.title,
                text = "",
                isTask = ReminderInfo.kindIsTask(t.kind),
            )
            val code = (alarm.key + "#" + alarm.minutesBefore).hashCode()
            codes += code
            val intent = info.into(Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE))
            val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setAlarm(alarm.fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
        }
        prefs.scheduledCodes = codes

        // refresh the plan every 12 hours, so the 48-hour window keeps moving
        val refresh = PendingIntent.getBroadcast(
            context,
            REFRESH_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 12 * 60 * 60 * 1000L, refresh)
    }

    private fun pending(code: Int, intent: Intent): PendingIntent? = PendingIntent.getBroadcast(
        context,
        code,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(atMillis: Long, pi: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    /** Whether reminders fire exactly on time (Android 12+ can restrict exact alarms). */
    fun exactAlarmsAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun today(): LocalDate = LocalDate.now()

    private companion object {
        const val REFRESH_CODE = 0x41504552 // "APER"
    }
}
