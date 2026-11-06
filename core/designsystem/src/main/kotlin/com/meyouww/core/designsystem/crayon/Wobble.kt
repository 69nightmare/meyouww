package com.meyouww.core.designsystem.crayon

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A point plus the outward normal of the outline at that point. */
data class OutlinePoint(val x: Float, val y: Float)

/**
 * Generates the slightly wobbly, hand-drawn outline of a rounded rectangle.
 *
 * The wobble comes from a seeded random sequence that is smoothed, so the same
 * seed always produces the same outline. That keeps outlines from shimmering
 * between frames and lets each component keep "its" crayon line.
 *
 * Pure Kotlin with no Android types, so it is unit-tested on the JVM.
 */
object Wobble {

    /**
     * @param width outline box width in px.
     * @param height outline box height in px.
     * @param radius corner radius in px (clamped to half the shorter side).
     * @param amplitude maximum perpendicular wobble in px.
     * @param seed stable per-component seed.
     * @param step approximate distance between samples in px.
     * @param inset how far the outline sits inside the box (use stroke/2 + amplitude to stay in bounds).
     */
    fun roundedRect(
        width: Float,
        height: Float,
        radius: Float,
        amplitude: Float,
        seed: Int,
        step: Float = 14f,
        inset: Float = 0f,
    ): List<OutlinePoint> {
        val w = width - 2 * inset
        val h = height - 2 * inset
        if (w <= 0f || h <= 0f) return emptyList()
        val r = max(0f, min(radius, min(w, h) / 2f))
        val straightX = w - 2 * r
        val straightY = h - 2 * r
        val arc = (PI.toFloat() / 2f) * r
        val perimeter = 2 * straightX + 2 * straightY + 4 * arc
        val count = max(12, (perimeter / max(step, 2f)).toInt())

        // Smoothed seeded noise in [-1, 1]: average of neighbours, wrapping around.
        val random = Random(seed)
        val raw = FloatArray(count) { random.nextFloat() * 2f - 1f }
        val smooth = FloatArray(count) { i ->
            (raw[(i - 1 + count) % count] + 2 * raw[i] + raw[(i + 1) % count]) / 4f
        }

        return List(count) { i ->
            val s = perimeter * i / count
            val (px, py, nx, ny) = pointAt(s, w, h, r, straightX, straightY, arc)
            val o = smooth[i] * amplitude
            OutlinePoint(inset + px + nx * o, inset + py + ny * o)
        }
    }

    private data class Sample(val x: Float, val y: Float, val nx: Float, val ny: Float)

    /** Walks the perimeter clockwise, starting where the top edge leaves the top-left corner. */
    private fun pointAt(
        distance: Float,
        w: Float,
        h: Float,
        r: Float,
        sx: Float,
        sy: Float,
        arc: Float,
    ): Sample {
        var d = distance
        // top edge
        if (d <= sx) return Sample(r + d, 0f, 0f, -1f)
        d -= sx
        // top-right corner
        if (d <= arc) return corner(w - r, r, r, -90f + 90f * (d / arc))
        d -= arc
        // right edge
        if (d <= sy) return Sample(w, r + d, 1f, 0f)
        d -= sy
        // bottom-right corner
        if (d <= arc) return corner(w - r, h - r, r, 90f * (d / arc))
        d -= arc
        // bottom edge
        if (d <= sx) return Sample(w - r - d, h, 0f, 1f)
        d -= sx
        // bottom-left corner
        if (d <= arc) return corner(r, h - r, r, 90f + 90f * (d / arc))
        d -= arc
        // left edge
        if (d <= sy) return Sample(0f, h - r - d, -1f, 0f)
        d -= sy
        // top-left corner
        return corner(r, r, r, 180f + 90f * (min(d, arc) / max(arc, 0.0001f)))
    }

    private fun corner(cx: Float, cy: Float, r: Float, degrees: Float): Sample {
        val t = degrees * PI.toFloat() / 180f
        val c = cos(t)
        val s = sin(t)
        return Sample(cx + r * c, cy + r * s, c, s)
    }
}
