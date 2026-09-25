package com.ivy.planner.ui

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.ivy.planner.domain.ImportanceLabels
import java.io.File

/** Opens an attached file (e.g. a PDF) in a viewer app. */
fun openAttachment(context: Context, file: File, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".apeiro.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime.substringBefore(';'))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Your names for importance levels 1–4, shared by every screen (loaded from preferences). */
object ImportanceNames {
    var labels: List<String> by mutableStateOf(ImportanceLabels.defaults)

    fun label(level: Int): String? = labels.getOrNull(level - 1)
}
