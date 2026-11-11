package com.meyouww.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.meyouww.core.chat.Attachment
import com.meyouww.core.chat.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** A picked file, copied into app storage and ready to be encrypted and sent. */
internal class PreparedMedia(
    val file: File,
    val mime: String,
    val name: String,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val thumb: ByteArray?,
)

internal sealed interface PrepareResult {
    data class Ready(val media: PreparedMedia) : PrepareResult
    data object TooBig : PrepareResult
    data object Unsupported : PrepareResult
    data object Failed : PrepareResult
}

/**
 * Gets a picked photo, video or PDF ready to send:
 * - photos are turned upright, shrunk to at most 2048 px and re-saved as JPEG, which
 *   also drops their metadata (location, camera); GIFs are kept as they are;
 * - videos, PDFs and Word files are copied as they are (up to 25 MB);
 * - every file gets a tiny preview picture that travels inside the encrypted message.
 */
internal object ChatMediaPreparer {
    private const val MAX_PHOTO_SIDE = 2048
    private const val THUMB_SIDE = 240

    suspend fun prepare(context: Context, uri: Uri): PrepareResult = withContext(Dispatchers.IO) {
        runCatching { prepareNow(context, uri) }.getOrElse { PrepareResult.Failed }
    }

    private fun prepareNow(context: Context, uri: Uri): PrepareResult {
        val resolver = context.contentResolver
        var name = "file"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        val mime = MediaKind.guessMime(resolver.getType(uri), name) ?: return PrepareResult.Unsupported
        val kind = MediaKind.of(mime)
        if (kind == MediaKind.Other) return PrepareResult.Unsupported
        val dir = File(context.cacheDir, "chat_prepare").apply { mkdirs() }
        dir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 3_600_000 }?.forEach { it.delete() }
        val out = File(dir, UUID.randomUUID().toString())

        if (kind == MediaKind.Image && mime != "image/gif") {
            val photo = decodeUpright(context, uri, MAX_PHOTO_SIDE) ?: return PrepareResult.Failed
            out.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            val thumb = thumbnail(photo)
            val result = PreparedMedia(out, "image/jpeg", name.substringBeforeLast('.') + ".jpg", photo.width, photo.height, null, thumb)
            photo.recycle()
            if (out.length() > Attachment.MAX_BYTES) return PrepareResult.TooBig.also { out.delete() }
            return PrepareResult.Ready(result)
        }

        if (size > Attachment.MAX_BYTES) return PrepareResult.TooBig
        val copied = resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> copyLimited(input, output) }
        } ?: return PrepareResult.Failed
        if (!copied) return PrepareResult.TooBig.also { out.delete() }

        return when (kind) {
            MediaKind.Video -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(out.path)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val turned = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let { it == 90 || it == 270 } == true
                    val frame = runCatching { retriever.getFrameAtTime(0) }.getOrNull()
                    val thumb = frame?.let { thumbnail(it).also { _ -> it.recycle() } }
                    PrepareResult.Ready(PreparedMedia(out, mime, name, if (turned) h else w, if (turned) w else h, duration, thumb))
                } finally {
                    runCatching { retriever.release() }
                }
            }
            MediaKind.Pdf -> PrepareResult.Ready(PreparedMedia(out, mime, name, null, null, null, pdfThumbnail(out)))
            MediaKind.Doc -> PrepareResult.Ready(PreparedMedia(out, mime, name, null, null, null, null))
            MediaKind.Audio -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(out.path)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    PrepareResult.Ready(PreparedMedia(out, mime, name, null, null, duration, null))
                } catch (e: Exception) {
                    PrepareResult.Ready(PreparedMedia(out, mime, name, null, null, null, null))
                } finally {
                    runCatching { retriever.release() }
                }
            }
            else -> {
                // A GIF: kept as it is so it still moves; the first frame is the preview.
                val first = BitmapFactory.decodeFile(out.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                PrepareResult.Ready(PreparedMedia(out, mime, name, null, null, null, first?.let { thumbnail(it).also { _ -> it.recycle() } }))
            }
        }
    }

    /** Copies at most 25 MB; false if there was more. */
    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > Attachment.MAX_BYTES) return false
            output.write(buffer, 0, read)
        }
    }

    private fun decodeUpright(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val rotation = resolver.openInputStream(uri)?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = (maxSide.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        if (rotation == 0 && scale == 1f) return decoded
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
    }

    /** A small JPEG, kept under about 12 KB so it fits inside the encrypted message. */
    private fun thumbnail(source: Bitmap): ByteArray? {
        val scale = THUMB_SIDE.toFloat() / max(source.width, source.height)
        val w = (source.width * scale).roundToInt().coerceAtLeast(1)
        val h = (source.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        var quality = 60
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { small.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }
            quality -= 15
        } while (bytes.size > 12_000 && quality > 10)
        if (small !== source) small.recycle()
        return bytes.takeIf { it.size <= 12_000 }
    }

    private fun pdfThumbnail(file: File): ByteArray? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val scale = THUMB_SIDE.toFloat() / max(page.width, page.height)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).roundToInt().coerceAtLeast(1),
                        (page.height * scale).roundToInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    thumbnail(bitmap).also { bitmap.recycle() }
                }
            }
        }
    }.getOrNull()
}
