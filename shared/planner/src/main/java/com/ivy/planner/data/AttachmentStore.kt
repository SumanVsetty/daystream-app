package com.ivy.planner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Photos for entries, people and collections, kept in the app's private storage
 * (never shared with other apps). Photos are resized to at most 1600 px so they
 * stay small on the phone and in backups.
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File get() = File(context.filesDir, "apeiro_attachments").apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Copies a picked photo in as "<id>.jpg" and returns the file name, or null if it can't be read. */
    fun importPhoto(uri: Uri, id: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val out = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        val name = "$id.jpg"
        FileOutputStream(file(name)).use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    /** Copies a picked file (e.g. a PDF) in as "<id>.<ext>"; returns the stored name and its type. */
    fun importFile(uri: Uri, id: String): Pair<String, String>? = runCatching {
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val ext = when {
            mime == "application/pdf" -> "pdf"
            mime.startsWith("image/") -> mime.substringAfter('/')
            else -> "bin"
        }
        val name = "$id.$ext"
        context.contentResolver.openInputStream(uri)?.use { input -> file(name).outputStream().use { input.copyTo(it) } } ?: return null
        name to mime
    }.getOrNull()

    /** The display name of a picked file, e.g. "Lab report Sep.pdf". */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    fun read(name: String): ByteArray? = file(name).takeIf { it.exists() }?.readBytes()

    fun write(name: String, bytes: ByteArray) {
        file(name).writeBytes(bytes)
    }

    fun delete(name: String) {
        file(name).delete()
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
