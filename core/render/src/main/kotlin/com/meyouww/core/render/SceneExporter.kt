package com.meyouww.core.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.meyouww.core.scene.ExportPlan
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneTransform
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** A finished image and how it was encoded. */
class ExportedImage(val bytes: ByteArray, val png: Boolean, val width: Int, val height: Int) {
    val mimeType: String get() = if (png) "image/png" else "image/webp"
    val extension: String get() = if (png) "png" else "webp"
}

/** Flattens scenes into wallpaper images. Uses the same renderer as the studio and previews. */
class SceneExporter(private val renderer: SceneRenderer) {

    /**
     * Renders at the recipient's physical resolution when known (capped at
     * 1440×3200), using the same cover transform as the preview.
     */
    fun render(scene: Scene, targetWidth: Int? = null, targetHeight: Int? = null, focusX: Float = 0.5f, focusY: Float = 0.5f): Bitmap {
        val (w, h) = ExportPlan.size(targetWidth, targetHeight, scene.aspect)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        renderer.draw(canvas, scene, SceneTransform.cover(scene.aspect, w, h, focusX, focusY))
        return bitmap
    }

    /** PNG for flat doodles, WebP for photos; anything over ~3 MB is re-encoded smaller. */
    fun encode(bitmap: Bitmap, preferPng: Boolean): ExportedImage {
        if (preferPng) {
            val png = compress(bitmap, Bitmap.CompressFormat.PNG, 100)
            if (png.size <= ExportPlan.MAX_BYTES) return ExportedImage(png, true, bitmap.width, bitmap.height)
        }
        var quality = 90
        var bytes = compress(bitmap, webp(), quality)
        while (bytes.size > ExportPlan.MAX_BYTES && quality > 50) {
            quality -= 10
            bytes = compress(bitmap, webp(), quality)
        }
        return ExportedImage(bytes, false, bitmap.width, bitmap.height)
    }

    fun export(scene: Scene, targetWidth: Int? = null, targetHeight: Int? = null): ExportedImage {
        val bitmap = render(scene, targetWidth, targetHeight)
        try {
            return encode(bitmap, ExportPlan.usePng(scene))
        } finally {
            bitmap.recycle()
        }
    }

    /** A small preview image for the drafts list. */
    fun thumbnail(scene: Scene, width: Int = 270): Bitmap {
        val height = (width * scene.aspect).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(bitmap), scene, SceneTransform.cover(scene.aspect, width, height))
        return bitmap
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
        }

    @Suppress("DEPRECATION")
    private fun webp(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
}

/** Imports a picked photo: rotates it upright, shrinks it, and drops all metadata (EXIF, location). */
object PhotoSanitizer {
    const val MAX_SIDE = 2048

    /**
     * Copies [source] (opened twice: once for EXIF orientation, once for pixels)
     * into [target] as a fresh WebP. Returns the photo's height/width ratio.
     */
    fun importInto(openStream: () -> InputStream?, target: File): Float? {
        val orientation = openStream()?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val upright = if (orientation != 0) {
            val m = Matrix().apply { postRotate(orientation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            @Suppress("DEPRECATION")
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            upright.compress(format, 90, out)
        }
        val ratio = upright.height.toFloat() / upright.width
        upright.recycle()
        return ratio
    }
}

/**
 * Turns a picked photo into a profile picture: upright, centre-cropped square JPEG,
 * no metadata. Big enough to look sharp when a friend opens it full size, and kept
 * under the bucket's 512 KB limit.
 */
object AvatarEncoder {
    const val SIDE = 960
    private const val MAX_BYTES = 480 * 1024

    fun encode(openStream: () -> InputStream?): ByteArray? {
        val orientation = openStream()?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2
        val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: return null
        val side = minOf(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            postRotate(orientation.toFloat())
            val scale = SIDE.toFloat() / side
            postScale(scale, scale)
        }
        val square = Bitmap.createBitmap(
            decoded,
            (decoded.width - side) / 2,
            (decoded.height - side) / 2,
            side,
            side,
            matrix,
            true,
        )
        if (square !== decoded) decoded.recycle()
        var quality = 88
        var bytes: ByteArray
        do {
            val out = java.io.ByteArrayOutputStream()
            square.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 8
        } while (bytes.size > MAX_BYTES && quality >= 40)
        square.recycle()
        return bytes
    }
}
