package com.ivy.planner.ui.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.ivy.planner.domain.EntryKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a reminder notification needs, passed between alarms, buttons and the sheet. */
data class ReminderInfo(
    val key: String,
    val ownerId: String,
    val isSeries: Boolean,
    val epochDay: Long,
    val minuteOfDay: Int,
    val title: String,
    val text: String,
    val isTask: Boolean,
    /** For repeating tasks: the day its record belongs to (differs from [epochDay] when moved). */
    val recordDay: Long = epochDay,
) {
    fun into(intent: Intent): Intent = intent
        .putExtra(EXTRA_KEY, key)
        .putExtra(EXTRA_OWNER, ownerId)
        .putExtra(EXTRA_SERIES, isSeries)
        .putExtra(EXTRA_DAY, epochDay)
        .putExtra(EXTRA_MINUTE, minuteOfDay)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_TEXT, text)
        .putExtra(EXTRA_TASK, isTask)
        .putExtra(EXTRA_RECORD_DAY, recordDay)

    /** A stable notification id for this day's reminder. */
    val notificationId: Int get() = key.hashCode()

    companion object {
        private const val EXTRA_KEY = "apeiro.reminder.key"
        private const val EXTRA_OWNER = "apeiro.reminder.owner"
        private const val EXTRA_SERIES = "apeiro.reminder.series"
        private const val EXTRA_DAY = "apeiro.reminder.day"
        private const val EXTRA_MINUTE = "apeiro.reminder.minute"
        private const val EXTRA_TITLE = "apeiro.reminder.title"
        private const val EXTRA_TEXT = "apeiro.reminder.text"
        private const val EXTRA_TASK = "apeiro.reminder.task"
        private const val EXTRA_RECORD_DAY = "apeiro.reminder.recordDay"

        fun from(intent: Intent): ReminderInfo? {
            val key = intent.getStringExtra(EXTRA_KEY) ?: return null
            return ReminderInfo(
                key = key,
                ownerId = intent.getStringExtra(EXTRA_OWNER) ?: return null,
                isSeries = intent.getBooleanExtra(EXTRA_SERIES, false),
                epochDay = intent.getLongExtra(EXTRA_DAY, 0),
                minuteOfDay = intent.getIntExtra(EXTRA_MINUTE, 0),
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                isTask = intent.getBooleanExtra(EXTRA_TASK, true),
                recordDay = intent.getLongExtra(EXTRA_RECORD_DAY, intent.getLongExtra(EXTRA_DAY, 0)),
            )
        }

        fun kindIsTask(kind: EntryKind) = kind == EntryKind.TASK
    }
}

/** Shows reminder notifications with Done, Snooze and Tomorrow. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** The full-colour logo tile, drawn once for the notification's large icon. */
    private val largeIcon by lazy {
        runCatching {
            ContextCompat.getDrawable(context, com.ivy.ui.R.drawable.ic_apeiro_logo_tile)?.toBitmap(192, 192)
        }.getOrNull()
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Reminders for your planner tasks and events"
                    },
                )
            }
        }
    }

    fun show(info: ReminderInfo) {
        ensureChannel()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val id = info.notificationId
        fun action(action: String, code: Int) = PendingIntent.getBroadcast(
            context,
            id * 10 + code,
            info.into(Intent(context, ReminderReceiver::class.java).setAction(action)),
            flags,
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id * 10 + 9, it, flags)
        }
        val sheet = PendingIntent.getActivity(
            context,
            id * 10 + 3,
            info.into(Intent(context, ReminderSheetActivity::class.java)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            flags,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            // the status-bar icon is a tight white silhouette; the colour logo shows on the right
            .setSmallIcon(com.ivy.ui.R.drawable.ic_apeiro_notification)
            .setLargeIcon(largeIcon)
            .setContentTitle(info.title)
            .setContentText(info.text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (info.isTask) builder.addAction(0, "Done", action(ReminderReceiver.ACTION_DONE, 1))
        builder.addAction(0, "Snooze", sheet)
        if (info.isTask && !info.isSeries) builder.addAction(0, "Tomorrow", action(ReminderReceiver.ACTION_TOMORROW, 2))
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun cancel(info: ReminderInfo) {
        NotificationManagerCompat.from(context).cancel(info.notificationId)
    }

    companion object {
        const val CHANNEL = "apeiro_task_reminders"
    }
}
