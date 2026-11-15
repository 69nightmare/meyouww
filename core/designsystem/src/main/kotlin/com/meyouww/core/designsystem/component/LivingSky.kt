package com.meyouww.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shadow
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.LocalSky
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.theme.MoonShape
import com.meyouww.core.designsystem.theme.SkyColors
import com.meyouww.core.designsystem.theme.SkyMath
import com.meyouww.core.designsystem.theme.SkyPalette
import com.meyouww.core.designsystem.theme.SkyPhase
import com.meyouww.core.designsystem.theme.SkyState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Living Sky behind every screen: a sky that blends through dawn, day, golden hour, dusk
 * and night with the clock; the sun travelling its arc by day and the moon by night, in its
 * real shape for the date; stars after sunset; clouds drifting; hearts floating up. All of it
 * is decoration (TalkBack skips it) and only the drawing is redone as it moves.
 */

/** True inside a card (its own background), false on the sky. */
val LocalOnCard = staticCompositionLocalOf { false }

/**
 * Living Sky: a soft glow in the sky's colour behind text that sits right on the sky, so it stays
 * readable where the sun, the moon or a cloud passes behind it. Null elsewhere (and in cards).
 */
@Composable
fun skyHalo(): Shadow? {
    if (!MeyouwwTheme.crayon.living || LocalOnCard.current) return null
    return Shadow(color = MaterialTheme.colorScheme.background, offset = Offset.Zero, blurRadius = 18f)
}

/** Living Sky: things on the sky bob gently, each a little out of step ([index]). Elsewhere it does nothing. */
@Composable
fun Modifier.skyBob(index: Int = 0, amount: Dp = 4.dp): Modifier {
    val sky = LocalSky.current ?: return this
    return this.graphicsLayer { translationY = -amount.toPx() * pingPong(sky.millis, 4_200L, index * 1_300L) }
}

/** Living Sky: a puffy cloud for Whity to sit on, along the bottom of this box. Elsewhere it does nothing. */
@Composable
fun Modifier.skyCloudSeat(): Modifier {
    val sky = LocalSky.current ?: return this
    val c = sky.colors
    return this.drawWithCache {
        val w = size.width * 0.96f
        val h = w * 0.22f
        val left = (size.width - w) / 2f
        val top = size.height - h - size.height * 0.03f
        val cloud = cloudShape(w, h)
        val face = Brush.verticalGradient(0f to Color(c.cloud), 1f to Color(c.cloudBelly), startY = -h * 0.8f, endY = h)
        val shade = Color(c.shadow)
        onDrawBehind {
            translate(left + c.sunX.dp.toPx(), top + 12.dp.toPx()) { drawPath(cloud, shade, alpha = 0.5f) }
            translate(left, top) { drawPath(cloud, face) }
        }
    }
}

/** Living Sky: three little hearts circling round this box (a friend's photo). Elsewhere it does nothing. */
@Composable
fun Modifier.skyHeartOrbit(): Modifier {
    val sky = LocalSky.current ?: return this
    val color = Color(sky.colors.heart)
    return this.drawWithCache {
        val heart = skyHeart(14.dp.toPx())
        val rx = size.width / 2f
        val ry = size.height / 2.4f
        onDrawBehind {
            val turn = cycle(sky.millis, 9_000L)
            for (i in 0 until 3) {
                val a = 2 * PI * (turn + i / 3f)
                val x = size.width / 2f + rx * cos(a).toFloat()
                val y = size.height / 2f + ry * sin(a).toFloat()
                // Behind the photo on the far side of the circle, in front on the near side.
                translate(x, y) { drawPath(heart, color, alpha = if (sin(a) < 0) 0.55f else 0.95f) }
            }
        }
    }
}

/** A smooth back-and-forth between 0 and 1 over [periodMs]. */
internal fun pingPong(ms: Long, periodMs: Long, offsetMs: Long = 0): Float {
    val p = (((ms + offsetMs) % periodMs + periodMs) % periodMs).toFloat() / periodMs
    return (1f - cos(2 * PI * p).toFloat()) / 2f
}

/** Where a loop of [periodMs] is, 0 to 1. */
internal fun cycle(ms: Long, periodMs: Long, offsetMs: Long = 0): Float =
    (((ms + offsetMs) % periodMs + periodMs) % periodMs).toFloat() / periodMs

/** A heart about [size] wide, centred on (0, 0). */
internal fun skyHeart(size: Float): Path {
    val s = size / 2f
    return Path().apply {
        moveTo(0f, s * 0.95f)
        cubicTo(-s * 1.25f, s * 0.05f, -s * 0.85f, -s * 1.05f, 0f, -s * 0.45f)
        cubicTo(s * 0.85f, -s * 1.05f, s * 1.25f, s * 0.05f, 0f, s * 0.95f)
        close()
    }
}

/**
 * The lit part of the moon, radius [r], centred on (0, 0): the outer edge is half the disc and
 * the terminator is half an ellipse, so it is a thin crescent, a half, a gibbous or full.
 */
internal fun moonLit(r: Float, moon: MoonShape): Path = Path().apply {
    val disc = Rect(-r, -r, r, r)
    moveTo(0f, -r)
    arcTo(disc, -90f, if (moon.outerOnRight) 180f else -180f, false)
    val rx = (r * moon.innerWidth).toFloat()
    if (rx < 0.5f) {
        lineTo(0f, -r)
    } else {
        arcTo(Rect(-rx, -r, rx, r), 90f, if (moon.innerOnLeft) 180f else -180f, false)
    }
    close()
}

/** The sun's colour and glow at each phase. */
private fun sunColors(phase: SkyPhase): Pair<Color, Color> = when (phase) {
    SkyPhase.Dawn -> Color(0xFFFFE9B8) to Color(0x8CFFDCAA)
    SkyPhase.Day -> Color(0xFFFFF3B0) to Color(0xB3FFF6BE)
    SkyPhase.Gold -> Color(0xFFFFD27A) to Color(0x99FFBE6E)
    SkyPhase.Dusk -> Color(0xFFFFB48A) to Color(0x80FF9682)
    SkyPhase.Night -> Color(0xFFFFF0C9) to Color(0x33FFF0C9)
}

/** Clouds as in the design: left edge, top and width in design units (a 390-wide phone), drift period, alpha. */
private class CloudSpot(val left: Float, val top: Float, val width: Float, val periodMs: Long, val reverse: Boolean, val alpha: Float)

private val CLOUDS = listOf(
    CloudSpot(-34f, 122f, 150f, 24_000, false, 1f),
    CloudSpot(304f, 70f, 108f, 32_000, true, 0.92f),
    CloudSpot(280f, 300f, 190f, 40_000, false, 0.7f),
)

/** Hearts floating up from near the bottom: x as a fraction, delay, size. */
private val RISING = listOf(Triple(0.10f, 0L, 1f), Triple(0.36f, 3_200L, 0.7f), Triple(0.64f, 6_100L, 0.85f), Triple(0.88f, 8_000L, 0.6f))

/** Stars in the top half: x and y as fractions, size in dp, gold or white. */
private val SKY_STARS = List(34) { i ->
    val x = (i * 0.618034f + 0.11f) % 1f
    val y = (i * 0.381966f + 0.05f * (i % 4)) % 0.55f
    Triple(Offset(x, y), if (i % 5 == 0) 1.9f else 1.1f, i % 4 == 1)
}

/** A puffy cloud: a pill with two round bumps on top, [w] wide and [h] tall, top-left at (0, 0) of the pill. */
private fun cloudShape(w: Float, h: Float): Path = Path().apply {
    addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(h / 2f)))
    addOval(Rect(Offset(w * 0.14f, -h * 0.86f), Size(w * 0.46f, h * 1.6f)))
    addOval(Rect(Offset(w * 0.51f, -h * 0.52f), Size(w * 0.34f, h * 1.16f)))
}

/** Paints the Living Sky. Read by the theme's backdrop when that look is on. */
internal fun Modifier.livingSkyBackdrop(sky: SkyState): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val u = w / 390f // one design unit
    val palettes = listOf(SkyPalette.dawn, SkyPalette.day, SkyPalette.gold, SkyPalette.dusk, SkyPalette.night)
    fun brush(c: SkyColors) = Brush.verticalGradient(0f to Color(c.top), 0.5f to Color(c.middle), 1f to Color(c.bottom))
    val base = brush(SkyPalette.night)
    val brushes = palettes.map(::brush)
    val clouds = CLOUDS.map { cloudShape(it.width * u, 34f * u) }
    val heart = skyHeart(18f * u)
    val moonR = 26f * u
    val lit = moonLit(moonR, sky.moon)
    val weights = FloatArray(5)
    val (sunFace, sunGlow) = sunColors(sky.phase)
    val colors = sky.colors
    val cloudBrushes = CLOUDS.map { Brush.verticalGradient(0f to Color(colors.cloud), 1f to Color(colors.cloudBelly), startY = -17f * u, endY = 34f * u) }
    val cloudShadow = Color(colors.shadow)
    val heartColor = Color(colors.heart)
    onDrawBehind {
        val minute = sky.minute
        val ms = sky.millis
        drawRect(base)
        SkyMath.layers(minute, weights)
        for (i in 0 until 5) if (weights[i] > 0.003f) drawRect(brushes[i], alpha = weights[i])

        // Stars: faint at dusk, bright at night, twinkling.
        val daylight = maxOf(weights[0], weights[1], weights[2])
        val nightish = if (weights[0] + weights[1] + weights[2] + weights[3] == 0f) 1f else weights[4]
        val starAlpha = (1f - daylight) * (0.3f + 0.45f * nightish)
        if (starAlpha > 0.02f) {
            SKY_STARS.forEachIndexed { i, (at, r, gold) ->
                val twinkle = 0.55f + 0.45f * pingPong(ms, 2_800L + (i % 5) * 400L, i * 377L)
                drawCircle(if (gold) Color(0xFFFFE3A3) else Color.White, r.dp.toPx(), Offset(at.x * w, at.y * h), alpha = (starAlpha * twinkle).coerceIn(0f, 1f))
            }
        }

        // The sun on its arc.
        val sun = SkyMath.sun(minute)
        if (sun.alpha > 0.01f) {
            val c = Offset(sun.x * w, sun.y * w)
            drawCircle(Brush.radialGradient(listOf(sunGlow, Color.Transparent), center = c, radius = 100f * u), 100f * u, c, alpha = sun.alpha)
            drawCircle(Color.White.copy(alpha = 0.14f), 50f * u, c, alpha = sun.alpha)
            drawCircle(sunFace, 36f * u, c, alpha = sun.alpha)
        }
        // The moon on its arc, in tonight's shape.
        val moon = SkyMath.moon(minute)
        if (moon.alpha > 0.01f) {
            val c = Offset(moon.x * w, moon.y * w)
            drawCircle(Brush.radialGradient(listOf(Color(0x4DFFF0C9), Color.Transparent), center = c, radius = moonR * 2.2f), moonR * 2.2f, c, alpha = moon.alpha)
            drawCircle(Color.White.copy(alpha = 0.10f), moonR, c, alpha = moon.alpha)
            drawCircle(Color(0x59FFF0C9), moonR, c, alpha = moon.alpha, style = Stroke(1.5f * u))
            translate(c.x, c.y) { drawPath(lit, Color(0xFFFFF0C9), alpha = moon.alpha) }
        }

        // Clouds drifting slowly, each with a soft shadow that follows the sun.
        CLOUDS.forEachIndexed { i, spot ->
            val swing = pingPong(ms, spot.periodMs * 2)
            val drift = (-26f + 56f * (if (spot.reverse) 1f - swing else swing)) * u
            translate(spot.left * u + drift, spot.top * u) {
                translate(colors.sunX * u * 2f, 12f * u) { drawPath(clouds[i], cloudShadow, alpha = 0.35f * spot.alpha) }
                drawPath(clouds[i], cloudBrushes[i], alpha = spot.alpha)
            }
        }

        // Hearts floating up.
        if (ms > 0) {
            RISING.forEach { (x, delay, scale) ->
                val p = cycle(ms, 10_000L, delay)
                val a = when {
                    p < 0.14f -> p / 0.14f * 0.8f
                    else -> 0.8f * (1f - (p - 0.14f) / 0.86f)
                }
                val k = scale * (0.6f + 0.4f * p)
                withTransform({
                    translate(x * w + 22f * u * p, h - 90f * u - 520f * u * p)
                    scale(k, k, Offset.Zero)
                }) { drawPath(heart, heartColor, alpha = a.coerceIn(0f, 1f)) }
            }
        }
    }
}

/**
 * A Living Sky 3D face: a soft shadow that falls with the sun, an edge underneath ([depth]
 * deep, in [edge]) and the face itself, lit from above with a soft belly. The face can be
 * pushed down into its edge by [push] (a press).
 */
fun Modifier.skySurface(
    fill: Color,
    edge: Color,
    shadow: Color,
    sunX: Float,
    cornerRadius: Dp = 28.dp,
    depth: Dp = 3.dp,
    lift: Dp = 14.dp,
    blur: Dp = 22.dp,
    push: Dp = 0.dp,
    outline: Color = Color.Transparent,
    strokeWidth: Dp = 0.dp,
    belly: Boolean = true,
): Modifier = this.drawWithCache {
    val w = size.width
    val hgt = size.height
    val r = cornerRadius.toPx().coerceAtMost(minOf(w, hgt) / 2f)
    val d = (depth - push).toPx().coerceAtLeast(0f)
    val p = push.toPx()
    val faceRect = RoundRect(0f, 0f, w, hgt, CornerRadius(r))
    val faceShape = Path().apply { addRoundRect(faceRect) }
    val lightTop = Color.White.copy(alpha = if (fill.luminanceApprox() < 0.25f) 0.14f else 0.75f)
    val bellyBrush = Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.06f))
    val stroke = Stroke(strokeWidth.toPx())
    val sx = sunX.dp.toPx()
    val dy = (lift - push * 2).toPx().coerceAtLeast(lift.toPx() * 0.35f)
    val spread = blur.toPx()
    onDrawBehind {
        if (shadow.alpha > 0f && fill.alpha > 0f) {
            val layers = 5
            for (i in 0 until layers) {
                val k = (i + 1f) / layers
                val grow = spread * k
                drawRoundRect(
                    color = shadow,
                    topLeft = Offset(sx - grow / 2f, dy - grow / 2f + d * 0.5f),
                    size = Size(w + grow, hgt + grow * 0.6f),
                    cornerRadius = CornerRadius(r + grow / 2f),
                    alpha = (1f - k * 0.75f) / layers * 1.6f,
                )
            }
        }
        if (d > 0f && edge.alpha > 0f) translate(0f, d + p) { drawPath(faceShape, edge) }
        translate(0f, p) {
            if (fill.alpha > 0f) {
                drawPath(faceShape, fill)
                clipPath(faceShape) {
                    drawRect(lightTop, topLeft = Offset(0f, 0f), size = Size(w, 2.dp.toPx()))
                    if (belly) drawRect(bellyBrush)
                }
            }
            if (stroke.width > 0f && outline.alpha > 0f) drawPath(faceShape, outline, style = stroke)
        }
    }
}

/** A quick brightness check (no gamma), enough to choose a highlight. */
private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
