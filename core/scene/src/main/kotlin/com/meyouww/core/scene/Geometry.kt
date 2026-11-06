package com.meyouww.core.scene

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A rectangle in design units or pixels. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Maps design units to pixels: px = x * scale + offsetX. One uniform scale, so
 * nothing ever stretches.
 */
data class SceneTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toPxX(x: Float): Float = x * scale + offsetX
    fun toPxY(y: Float): Float = y * scale + offsetY
    fun toDesignX(px: Float): Float = (px - offsetX) / scale
    fun toDesignY(py: Float): Float = (py - offsetY) / scale

    /** The part of the design that lands inside a target of [targetWidth] × [targetHeight] pixels. */
    fun visibleDesignBox(targetWidth: Int, targetHeight: Int): Box =
        Box(toDesignX(0f), toDesignY(0f), toDesignX(targetWidth.toFloat()), toDesignY(targetHeight.toFloat()))

    companion object {
        /**
         * Scale to fill the target, then crop the overflow. [focusX]/[focusY] (0..1)
         * choose which part stays when cropping; 0.5 keeps the centre.
         * The same function is used for previews and for the exported wallpaper.
         */
        fun cover(aspect: Float, targetWidth: Int, targetHeight: Int, focusX: Float = 0.5f, focusY: Float = 0.5f): SceneTransform {
            require(aspect > 0f && targetWidth > 0 && targetHeight > 0) { "empty target" }
            val scale = max(targetWidth / 1f, targetHeight / aspect)
            val overflowX = 1f * scale - targetWidth
            val overflowY = aspect * scale - targetHeight
            return SceneTransform(scale, -overflowX * focusX.coerceIn(0f, 1f), -overflowY * focusY.coerceIn(0f, 1f))
        }

        /** Shows the whole design inside the target, centred (used by the editor). */
        fun fit(aspect: Float, targetWidth: Float, targetHeight: Float): SceneTransform {
            require(aspect > 0f && targetWidth > 0f && targetHeight > 0f) { "empty target" }
            val scale = min(targetWidth / 1f, targetHeight / aspect)
            return SceneTransform(scale, (targetWidth - scale) / 2f, (targetHeight - aspect * scale) / 2f)
        }
    }
}

/** Export size and format decisions, kept here so they can be unit tested. */
object ExportPlan {
    const val MAX_WIDTH = 1440
    const val MAX_HEIGHT = 3200
    const val MAX_BYTES = 3 * 1024 * 1024

    /**
     * The recipient's physical resolution when known, scaled down (keeping its
     * shape) to fit 1440×3200. Unknown targets use the design's own aspect at
     * 1440 wide.
     */
    fun size(targetWidth: Int?, targetHeight: Int?, designAspect: Float): Pair<Int, Int> {
        val w = targetWidth?.takeIf { it > 0 }
        val h = targetHeight?.takeIf { it > 0 }
        if (w == null || h == null) {
            val height = (MAX_WIDTH * designAspect).roundToInt()
            return if (height <= MAX_HEIGHT) MAX_WIDTH to height else (MAX_HEIGHT / designAspect).roundToInt() to MAX_HEIGHT
        }
        val factor = min(1f, min(MAX_WIDTH.toFloat() / w, MAX_HEIGHT.toFloat() / h))
        return max(1, (w * factor).roundToInt()) to max(1, (h * factor).roundToInt())
    }

    /** PNG keeps flat doodles crisp; photos compress far better as WebP. */
    fun usePng(scene: Scene): Boolean = !scene.hasPhotos
}

/** Bounding box of an element in design units (rotation included). */
fun Element.bounds(): Box = when (this) {
    is StrokeElement -> {
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (i in 0 until pointCount) {
            l = min(l, x(i)); t = min(t, y(i)); r = max(r, x(i)); b = max(b, y(i))
        }
        val pad = width / 2f
        if (pointCount == 0) Box(0f, 0f, 0f, 0f) else Box(l - pad, t - pad, r + pad, b + pad)
    }
    is Placed -> rotatedBox(x, y, boxWidth, boxHeight, rotation)
}

private fun rotatedBox(cx: Float, cy: Float, w: Float, h: Float, degrees: Float): Box {
    val rad = Math.toRadians(degrees.toDouble())
    val c = abs(cos(rad)).toFloat()
    val s = abs(sin(rad)).toFloat()
    val halfW = (w * c + h * s) / 2f
    val halfH = (w * s + h * c) / 2f
    return Box(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
}

/** The top-most movable element under a point, for selection. Strokes are not selectable. */
fun Scene.placedAt(x: Float, y: Float, slop: Float = 0.02f): Placed? =
    elements.asReversed().filterIsInstance<Placed>().firstOrNull { e ->
        val b = e.bounds()
        Box(b.left - slop, b.top - slop, b.right + slop, b.bottom + slop).contains(x, y)
    }

/** Distance between two points. */
fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    return sqrt(dx * dx + dy * dy)
}
