package com.ivy.planner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        FileOutputStream(file(name)).use { out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
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
        if (mime == "application/pdf") compressPdf(file(name))
        name to mime
    }.getOrNull()

    /** The display name of a picked file, e.g. "Lab report Sep.pdf". */
    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Restores a file from an unzipped backup. */
    fun copyIn(name: String, from: File) {
        from.inputStream().use { input -> file(name).outputStream().use { input.copyTo(it) } }
    }

    fun read(name: String): ByteArray? = file(name).takeIf { it.exists() }?.readBytes()

    fun write(name: String, bytes: ByteArray) {
        file(name).writeBytes(bytes)
    }

    fun delete(name: String) {
        file(name).delete()
    }

    /**
     * PDFs are kept for reference, so large ones are redrawn page by page at reference quality.
     * This shrinks scanned documents a lot; a PDF that's already small, or that the redraw
     * wouldn't make smaller, is kept as it is. (Text in a redrawn PDF can't be selected.)
     */
    private fun compressPdf(file: File) {
        if (file.length() < PDF_MIN_BYTES) return
        val out = File(file.parentFile, file.name + ".tmp")
        val ok = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount > PDF_MAX_PAGES) return@runCatching false
                    val doc = PdfDocument()
                    try {
                        for (i in 0 until renderer.pageCount) {
                            renderer.openPage(i).use { page ->
                                // page size is in points (1/72 inch); render at about 110 dpi
                                val scale = PDF_DPI / 72f
                                val w = (page.width * scale).toInt().coerceAtLeast(1)
                                val h = (page.height * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                // a JPEG round trip keeps the embedded image small
                                val jpeg = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, PDF_JPEG_QUALITY, it) }.toByteArray()
                                bmp.recycle()
                                val small = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                                val info = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                                val target = doc.startPage(info)
                                target.canvas.drawBitmap(small, null, android.graphics.Rect(0, 0, page.width, page.height), null)
                                doc.finishPage(target)
                                small.recycle()
                            }
                        }
                        FileOutputStream(out).use { doc.writeTo(it) }
                    } finally {
                        doc.close()
                    }
                }
            }
            true
        }.getOrDefault(false)
        if (ok && out.exists() && out.length() in 1 until file.length()) {
            out.copyTo(file, overwrite = true)
        }
        out.delete()
    }

    private companion object {
        /** Photos are kept for reference: 1280 px on the long side, JPEG quality 75. */
        const val MAX_SIDE = 1280
        const val JPEG_QUALITY = 75
        const val PDF_MIN_BYTES = 300_000L
        const val PDF_MAX_PAGES = 60
        const val PDF_DPI = 110f
        const val PDF_JPEG_QUALITY = 60
    }
}
