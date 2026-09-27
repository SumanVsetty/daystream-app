package com.ivy.planner.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Checks, before anything opens the planner database, that this build understands it.
 * An older build meeting newer data must not open (or "fix") it: it explains instead.
 */
object StartupGuard {
    /** Set when the data on this phone is newer than this build of the app. */
    var problem: String? = null
        private set

    val dataTooNew: Boolean get() = problem != null

    fun check(context: Context) {
        val file = context.getDatabasePath(PlannerDatabase.NAME)
        if (!file.exists()) return
        val stored = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
        }.getOrNull() ?: return
        if (stored > PlannerDatabase.VERSION) {
            problem = "Your Apeiro data is newer than this version of the app (data version $stored, app version " +
                "${PlannerDatabase.VERSION}). This usually means an older build was installed. " +
                "Install the latest build (in Android Studio: Build → Clean Project, then Run). Your data is safe and hasn't been changed."
        }
    }
}

/**
 * A crash leaves a report in the app's private storage (never sent anywhere), so the next
 * launch can offer to share it. Android still closes the app as usual.
 */
object CrashLog {
    private const val FILE = "apeiro_last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                File(app.filesDir, FILE).writeText(
                    "Apeiro crash report\nTime: ${LocalDateTime.now()}\nThread: ${thread.name}\n" +
                        "Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.MODEL})\n\n$trace",
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash report, if there is one not yet dismissed. */
    fun read(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
