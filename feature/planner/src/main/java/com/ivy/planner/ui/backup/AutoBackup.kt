package com.ivy.planner.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ivy.data.backup.BackupDataUseCase
import com.ivy.navigation.navigation
import com.ivy.planner.data.PlannerPrefs
import com.ivy.planner.ui.Pill
import com.ivy.planner.ui.PlannerColors
import com.ivy.planner.ui.PlannerTheme
import com.ivy.planner.ui.SectionLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** What the background backup needs from the app. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoBackupEntryPoint {
    fun backup(): BackupDataUseCase
    fun prefs(): PlannerPrefs
}

/**
 * Makes a full backup (money, planner, photos and files) into the chosen folder,
 * named "Apeiro-backup-2026-09-25-2130.zip", and keeps the newest [KEEP].
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, AutoBackupEntryPoint::class.java)
        val prefs = deps.prefs()
        val folder = prefs.autoBackupFolder?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: return Result.success()
        return runCatching {
            val resolver = applicationContext.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
            val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
            val file = DocumentsContract.createDocument(resolver, parent, "application/zip", name) ?: error("Couldn't create the backup file")
            deps.backup().exportToFile(file)
            prune(folder)
            prefs.autoBackupLast = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
            Result.success()
        }.getOrElse { Result.retry() }
    }

    /** Keeps the newest [KEEP] automatic backups in the folder; your manual backups aren't touched. */
    private fun prune(folder: Uri) {
        val resolver = applicationContext.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        val found = mutableListOf<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val n = c.getString(1) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(".zip")) found += id to n
            }
        }
        found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
        }
    }

    companion object {
        const val PREFIX = "Apeiro-backup-"
        const val KEEP = 5
        private const val WORK = "apeiro_auto_backup"

        /** Starts, changes or stops the schedule. */
        fun schedule(context: Context, days: Int) {
            val wm = WorkManager.getInstance(context)
            if (days <= 0) {
                wm.cancelUniqueWork(WORK)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(days.toLong(), TimeUnit.DAYS).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}

@Composable
fun PlannerAutoBackupScreenImpl() {
    PlannerTheme { AutoBackupUi() }
}

@Composable
private fun AutoBackupUi() {
    val nav = navigation()
    val context = LocalContext.current
    val prefs = remember { EntryPointAccessors.fromApplication(context.applicationContext, AutoBackupEntryPoint::class.java).prefs() }
    var folder by remember { mutableStateOf(prefs.autoBackupFolder?.takeIf { it.isNotBlank() }) }
    var days by remember { mutableStateOf(prefs.autoBackupDays) }
    var message by remember { mutableStateOf<String?>(null) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // keep access to the folder after restarts
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            prefs.autoBackupFolder = uri.toString()
            folder = uri.toString()
            if (days <= 0) {
                days = 7
                prefs.autoBackupDays = 7
            }
            AutoBackupWorker.schedule(context, days)
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                IconButton(onClick = { nav.back() }) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text("Automatic backup", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "A full backup (money, planner, photos and files) is saved to a folder you choose, and the newest ${AutoBackupWorker.KEEP} are kept. " +
                    "Pick a folder on Google Drive to keep your backups off the phone.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionLabel("Folder")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pickFolder.launch(null) }
                    .padding(14.dp),
            ) {
                Text(
                    folder?.let { Uri.parse(it).lastPathSegment?.substringAfter(':')?.ifBlank { "Chosen folder" } ?: "Chosen folder" } ?: "Choose a folder",
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Change", color = PlannerColors.Accent, fontWeight = FontWeight.Bold)
            }
            SectionLabel("How often")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 1 to "Daily", 7 to "Weekly", 30 to "Monthly").forEach { (d, label) ->
                    Pill(label, days == d, {
                        days = d
                        prefs.autoBackupDays = d
                        AutoBackupWorker.schedule(context, if (folder != null) d else 0)
                    })
                }
            }
            prefs.autoBackupLast?.takeIf { it.isNotBlank() }?.let {
                Text("Last automatic backup: $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (folder != null) {
                TextButton(onClick = {
                    AutoBackupWorker.runNow(context)
                    message = "Backing up now. It takes a moment; the file appears in the folder when done."
                }) { Text("Back up now", color = PlannerColors.Accent, fontWeight = FontWeight.Bold) }
            }
            message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
