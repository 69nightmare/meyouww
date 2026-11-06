package com.meyouww.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.scene.FontChoice
import java.io.File
import java.util.Random

/** Everything the renderer needs besides the scene itself. */
interface RenderAssets {
    fun typeface(font: FontChoice): Typeface
    fun sticker(name: String): Drawable?
    fun photo(file: String): Bitmap?

    /** White texture whose alpha carries the crayon grain. */
    val grain: Bitmap
}

/**
 * Loads fonts and stickers from resources and photos from a draft folder.
 * Photos are decoded at most 2048 px on the long side and cached by size.
 */
class AndroidRenderAssets(
    private val context: Context,
    private val photoDir: () -> File?,
) : RenderAssets {
    private val typefaces = HashMap<FontChoice, Typeface>()
    private val stickers = HashMap<String, Drawable?>()
    private val photos = object : LruCache<String, Bitmap>(PHOTO_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    override fun typeface(font: FontChoice): Typeface = typefaces.getOrPut(font) {
        val res = when (font) {
            FontChoice.Fredoka -> R.font.fredoka
            FontChoice.Handwritten -> R.font.gaegu_bold
            FontChoice.Pop -> R.font.mochiy_pop_one
            FontChoice.Nunito -> R.font.nunito
        }
        runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    }

    override fun sticker(name: String): Drawable? = stickers.getOrPut(name) {
        WhitySticker.entries.firstOrNull { it.name == name }?.let { ContextCompat.getDrawable(context, it.drawable)?.mutate() }
    }

    override fun photo(file: String): Bitmap? {
        photos.get(file)?.let { return it }
        val dir = photoDir() ?: return null
        val f = File(dir, file)
        // Only plain file names inside the draft folder.
        if (file.contains('/') || !f.isFile) return null
        val bitmap = PhotoImport.decodeSampled(f, MAX_PHOTO_SIDE) ?: return null
        photos.put(file, bitmap)
        return bitmap
    }

    override val grain: Bitmap by lazy { Grain.create() }

    fun clearPhotos() = photos.evictAll()

    private companion object {
        const val PHOTO_CACHE_BYTES = 48 * 1024 * 1024
        const val MAX_PHOTO_SIDE = 2048
    }
}

/** A small, deterministic paper-grain mask, the same on every phone. */
object Grain {
    const val SIZE = 128

    fun create(): Bitmap {
        val random = Random(20261107L)
        val alpha = IntArray(SIZE * SIZE) { 120 + random.nextInt(136) }
        // Smear sideways a little so it reads as crayon on paper, not static.
        val smeared = IntArray(SIZE * SIZE)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val i = y * SIZE + x
                val left = alpha[y * SIZE + (x + SIZE - 1) % SIZE]
                val right = alpha[y * SIZE + (x + 1) % SIZE]
                smeared[i] = ((alpha[i] * 2 + left + right) / 4).coerceIn(0, 255)
            }
        }
        val pixels = IntArray(SIZE * SIZE) { Color.argb(smeared[it], 255, 255, 255) }
        return Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }
}

/** Decoding helpers shared by the renderer and photo import. */
object PhotoImport {
    /** Decodes a file no larger than [maxSide] pixels on its long side. */
    fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
