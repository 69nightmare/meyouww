package com.meyouww.feature.chat

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Copies a picture, video or PDF from the chat into the phone's own folders, where the
 * gallery and file apps find it: Pictures/meyouww, Movies/meyouww or Download/meyouww.
 * Android 10+ needs no permission for this; Android 8-9 use the storage permission the
 * app already declares for saving doodles.
 */
internal object MediaSaver {

    suspend fun save(context: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        runCatching { write(context, file) }.isSuccess
    }

    private fun write(context: Context, file: File) {
        val mime = mimeOf(file)
        val name = "meyouww_${System.currentTimeMillis()}.${file.extension.ifBlank { "bin" }}"
        val (collection, folder) = when {
            mime.startsWith("image/") -> imagesUri() to Environment.DIRECTORY_PICTURES
            mime.startsWith("video/") -> videosUri() to Environment.DIRECTORY_MOVIES
            else -> downloadsUri() to Environment.DIRECTORY_DOWNLOADS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && collection != null) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/meyouww")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("no uri")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("no stream")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(folder), "meyouww").apply { mkdirs() }
            val target = File(dir, name)
            file.copyTo(target)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        }
    }

    private fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    private fun imagesUri(): Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private fun videosUri(): Uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    private fun downloadsUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else null
}
