package com.meyouww.core.scene

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One wallpaper design. The editor, the recipient preview and the exported image
 * are all drawn from this.
 *
 * Coordinates are in canvas-width units on BOTH axes: x runs 0..1 and y runs
 * 0..[aspect]. Brush widths and text sizes use the same unit. This keeps strokes
 * from stretching when the design is shown at a different aspect ratio; the
 * mapping to pixels is always one uniform "cover" (or "fit") transform.
 */
@Serializable
data class Scene(
    val version: Int = 1,
    /** Height divided by width of the design canvas, for example 20:9 → 2.222. */
    val aspect: Float = DEFAULT_ASPECT,
    val background: Background = Background.Solid(CREAM),
    /** Bottom to top. */
    val elements: List<Element> = emptyList(),
) {
    val height: Float get() = aspect

    val hasPhotos: Boolean get() = elements.any { it is PhotoElement }

    fun element(id: String): Element? = elements.firstOrNull { it.id == id }

    companion object {
        const val DEFAULT_ASPECT = 20f / 9f
        const val CREAM = 0xFFFFF6E0.toInt()
    }
}

@Serializable
sealed interface Background {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: Int) : Background

    @Serializable
    @SerialName("gradient")
    data class Gradient(val top: Int, val bottom: Int) : Background

    /** A bundled, procedurally drawn "Doodle Town" scene. */
    @Serializable
    @SerialName("preset")
    data class Preset(val id: String) : Background

    companion object {
        val presets = listOf("sky", "night", "meadow", "candy", "sea")
    }
}

@Serializable
enum class Tool {
    Pencil,
    Crayon,
    Marker,
    Highlighter,
    Neon,
    Watercolor,
    Eraser;

    /** Tools whose width follows stylus pressure. */
    val usesPressure: Boolean get() = this == Pencil || this == Crayon || this == Marker
}

@Serializable
enum class ShapeKind { Rectangle, Oval, Heart, Star, Line, Arrow }

@Serializable
enum class FontChoice { Fredoka, Handwritten, Pop, Nunito }

@Serializable
sealed interface Element {
    val id: String
}

/** Something that sits at a centre point and can be moved, scaled and rotated. */
@Serializable
sealed interface Placed : Element {
    val x: Float
    val y: Float
    val rotation: Float

    /** Width in canvas-width units. */
    val boxWidth: Float

    /** Height in canvas-width units. */
    val boxHeight: Float
}

/**
 * A freehand stroke. [points] holds x, y, pressure triples; x and y in canvas-width
 * units, pressure 0..1.
 */
@Serializable
@SerialName("stroke")
data class StrokeElement(
    override val id: String,
    val tool: Tool,
    val color: Int,
    val width: Float,
    val opacity: Float = 1f,
    val points: List<Float>,
) : Element {
    val pointCount: Int get() = points.size / 3

    fun x(i: Int): Float = points[i * 3]
    fun y(i: Int): Float = points[i * 3 + 1]
    fun pressure(i: Int): Float = points[i * 3 + 2]
}

@Serializable
@SerialName("shape")
data class ShapeElement(
    override val id: String,
    val kind: ShapeKind,
    override val x: Float,
    override val y: Float,
    val w: Float,
    val h: Float,
    override val rotation: Float = 0f,
    val color: Int,
    val strokeWidth: Float,
    val filled: Boolean = false,
) : Placed {
    override val boxWidth: Float get() = kotlin.math.abs(w)
    override val boxHeight: Float get() = kotlin.math.abs(h)
}

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String,
    val text: String,
    override val x: Float,
    override val y: Float,
    /** Font size in canvas-width units. */
    val size: Float,
    val color: Int,
    val font: FontChoice = FontChoice.Fredoka,
    override val rotation: Float = 0f,
    val outline: Boolean = true,
) : Placed {
    private val lines: List<String> get() = text.lines()

    /** Rough box for selection; the renderer measures precisely. */
    override val boxWidth: Float get() = (lines.maxOfOrNull { it.length } ?: 1).coerceAtLeast(1) * size * 0.6f
    override val boxHeight: Float get() = lines.size.coerceAtLeast(1) * size * 1.25f
}

/** A Whity sticker (by name) or an emoji ("emoji:🌸"). */
@Serializable
@SerialName("sticker")
data class StickerElement(
    override val id: String,
    val sticker: String,
    override val x: Float,
    override val y: Float,
    val size: Float,
    override val rotation: Float = 0f,
) : Placed {
    override val boxWidth: Float get() = size
    override val boxHeight: Float get() = size

    val emoji: String? get() = sticker.removePrefix(EMOJI_PREFIX).takeIf { sticker.startsWith(EMOJI_PREFIX) }

    companion object {
        const val EMOJI_PREFIX = "emoji:"
    }
}

/** A photo imported through the Photo Picker, stored without metadata in the draft folder. */
@Serializable
@SerialName("photo")
data class PhotoElement(
    override val id: String,
    val file: String,
    override val x: Float,
    override val y: Float,
    val width: Float,
    /** Height divided by width of the photo. */
    val photoAspect: Float,
    override val rotation: Float = 0f,
    val opacity: Float = 1f,
    /** -1 (darker) .. 1 (brighter). */
    val brightness: Float = 0f,
) : Placed {
    override val boxWidth: Float get() = width
    override val boxHeight: Float get() = width * photoAspect
}
