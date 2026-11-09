package com.meyouww.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette
import kotlin.math.PI
import kotlin.math.sin

/*
 * The Kuchhu-chan site's world, for Compose: a sky full of soft clouds (or a starry
 * night), washi-tape labels and party bunting. Decoration only: none of it is read
 * out by TalkBack, and text never sits on the busy parts.
 */

/** Big soft glows as on the site (x, y as fractions of the screen; radius in dp; white alpha). */
private val CLOUDS = listOf(
    Triple(Offset(0.12f, 0.06f), 70f, 0.40f),
    Triple(Offset(0.90f, 0.15f), 52f, 0.33f),
    Triple(Offset(0.76f, 0.93f), 95f, 0.22f),
    Triple(Offset(0.05f, 0.62f), 44f, 0.18f),
)

/** Puffy little clouds drifting across the sky (x, y as fractions; width in dp; white alpha). */
private val PUFFS = listOf(
    Triple(Offset(0.70f, 0.05f), 92f, 0.55f),
    Triple(Offset(0.18f, 0.24f), 70f, 0.42f),
    Triple(Offset(0.86f, 0.40f), 80f, 0.38f),
    Triple(Offset(0.10f, 0.47f), 58f, 0.34f),
    Triple(Offset(0.62f, 0.58f), 64f, 0.30f),
    Triple(Offset(0.25f, 0.78f), 86f, 0.36f),
    Triple(Offset(0.92f, 0.72f), 54f, 0.32f),
    Triple(Offset(0.48f, 0.90f), 72f, 0.30f),
)

/** Tiny crayon hearts scattered about (x, y as fractions; size in dp; tilt in degrees). */
private val HEARTS = listOf(
    Triple(Offset(0.42f, 0.11f), 14f, -12f),
    Triple(Offset(0.93f, 0.27f), 12f, 10f),
    Triple(Offset(0.06f, 0.35f), 16f, -6f),
    Triple(Offset(0.55f, 0.34f), 10f, 14f),
    Triple(Offset(0.80f, 0.51f), 15f, -10f),
    Triple(Offset(0.34f, 0.55f), 11f, 8f),
    Triple(Offset(0.08f, 0.74f), 13f, 12f),
    Triple(Offset(0.68f, 0.71f), 12f, -14f),
    Triple(Offset(0.88f, 0.86f), 16f, 6f),
    Triple(Offset(0.20f, 0.93f), 12f, -8f),
)

/** A cloud: a flat-bottomed bunch of circles, as one shape so the overlaps don't darken. */
private fun cloudPath(center: Offset, width: Float): Path {
    val r = width / 4f
    val base = center.y + r * 0.35f
    return Path().apply {
        addOval(Rect(Offset(center.x - r * 1.9f, base - r * 1.25f), Size(r * 1.5f, r * 1.25f)))
        addOval(Rect(Offset(center.x - r * 1.0f, base - r * 2.1f), Size(r * 2.0f, r * 2.1f)))
        addOval(Rect(Offset(center.x + r * 0.35f, base - r * 1.55f), Size(r * 1.55f, r * 1.55f)))
        addRoundRect(
            RoundRect(
                left = center.x - r * 1.9f, top = base - r * 0.8f, right = center.x + r * 1.9f, bottom = base,
                radiusX = r * 0.4f, radiusY = r * 0.4f,
            ),
        )
    }
}

/** A heart about [size] wide, pointing down, centred on (0, 0). */
private fun heartPath(size: Float): Path {
    val s = size / 2f
    return Path().apply {
        moveTo(0f, s * 0.95f)
        cubicTo(-s * 1.25f, s * 0.05f, -s * 0.85f, -s * 1.05f, 0f, -s * 0.45f)
        cubicTo(s * 0.85f, -s * 1.05f, s * 1.25f, s * 0.05f, 0f, s * 0.95f)
        close()
    }
}

/** Stars for the night sky: x, y and size in dp; every few are gold. */
private val STARS = List(28) { i ->
    val x = ((i * 0.618034f) % 1f)
    val y = ((i * 0.381966f + 0.07f * (i % 3)) % 0.7f)
    Triple(Offset(x, y), if (i % 5 == 0) 2.2f else 1.3f, i % 4 == 1)
}

/**
 * Paints the world behind a screen: sky blue with clouds and paper grain by day,
 * an indigo night with stars, a moon and a sunset glow by night.
 */
@Composable
fun Modifier.kasukabeBackdrop(): Modifier {
    val crayon = MeyouwwTheme.crayon
    val grain = LocalCrayonGrain.current
    val dark = crayon.isDark
    val world = crayon.world
    return this.drawWithCache {
        val w = size.width
        val h = size.height
        val grainBrush = grain?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
        val night = Brush.verticalGradient(
            0f to Color(0xFF1B1240),
            0.55f to Color(Palette.NightBackground),
            0.85f to Color(0xFF3A2275),
            1f to Color(Palette.NightGlow),
        )
        // Shapes are built once per screen size, not on every frame.
        val scale = (w / 400.dp.toPx()).coerceIn(0.8f, 1.6f)
        val puffs = PUFFS.map { (at, width, a) -> cloudPath(Offset(at.x * w, at.y * h), width.dp.toPx() * scale) to a }
        val hearts = HEARTS.map { (at, across, tilt) -> Triple(heartPath(across.dp.toPx() * scale), Offset(at.x * w, at.y * h), tilt) }
        val heartLine = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
        onDrawBehind {
            if (dark) {
                drawRect(night)
                STARS.forEach { (at, r, gold) ->
                    drawCircle(
                        color = if (gold) Color(0xFFFFD75E) else Color.White,
                        radius = r.dp.toPx(),
                        center = Offset(at.x * w, at.y * h),
                        alpha = 0.85f,
                    )
                }
                // The moon from the site's night page.
                drawCircle(Color(0xFFFFF6C9), radius = 30.dp.toPx(), center = Offset(0.86f * w, 0.24f * h), alpha = 0.9f)
            } else {
                drawRect(world)
                CLOUDS.forEach { (at, r, a) ->
                    drawCircle(Color.White, radius = r.dp.toPx() * scale, center = Offset(at.x * w, at.y * h), alpha = a)
                }
                // Kept faint so text and cards on top stay easy to read.
                puffs.forEach { (cloud, a) -> drawPath(cloud, Color.White, alpha = a) }
                hearts.forEach { (heart, at, tilt) ->
                    withTransform({
                        translate(at.x, at.y)
                        rotate(tilt, pivot = Offset.Zero)
                    }) {
                        drawPath(heart, Color(0xFFF7A8C0), alpha = 0.35f)
                        drawPath(heart, Color(0xFFE77FA0), alpha = 0.45f, style = heartLine)
                    }
                }
            }
            if (grainBrush != null) drawRect(grainBrush, alpha = if (dark) 0.05f else 0.1f, blendMode = BlendMode.Multiply)
        }
    }
}

/**
 * A strip of washi tape with words on it, slightly crooked, like the site's
 * "The seal is broken" label. Pink by default; yellow, sky and mint also fit.
 */
@Composable
fun TapeLabel(
    text: String,
    modifier: Modifier = Modifier,
    tape: Color = Color(0xFFF7A8C0),
    tilt: Float = -1.5f,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    isHeading: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .rotate(tilt)
            .drawWithCache {
                val w = size.width
                val h = size.height
                val notch = 4.dp.toPx()
                val teeth = 5
                // Torn ends: a little zigzag at each side.
                val outline = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    for (i in 1..teeth) {
                        val y = h * i / teeth
                        lineTo(if (i % 2 == 1) w - notch else w, y)
                    }
                    lineTo(0f, h)
                    for (i in teeth - 1 downTo 0) {
                        val y = h * i / teeth
                        lineTo(if (i % 2 == 1) notch else 0f, y)
                    }
                    close()
                }
                val stripe = 7.dp.toPx()
                onDrawBehind {
                    drawPath(outline, tape.copy(alpha = 0.92f))
                    clipPath(outline) {
                        // Diagonal paper stripes, like printed washi tape.
                        var x = -h
                        while (x < w + h) {
                            drawLine(Color.White.copy(alpha = 0.35f), Offset(x, h), Offset(x + h, 0f), strokeWidth = stripe * 0.45f)
                            x += stripe
                        }
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .then(if (isHeading) Modifier.semantics { heading() } else Modifier),
    ) {
        Text(text, style = style, color = Color(Palette.Ink), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Party bunting across the top of a screen: little crayon pennants on a sagging string. */
@Composable
fun Bunting(modifier: Modifier = Modifier, height: Dp = 34.dp) {
    val outline = MeyouwwTheme.crayon.outline
    val colors = listOf(
        Color(Palette.BrightRed),
        Color(Palette.SunnyYellow),
        Color(0xFF62B6EC),
        Color(0xFF3D9A48),
        Color(0xFFF59AB5),
    )
    Canvas(modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val flag = 30.dp.toPx()
        val gap = 8.dp.toPx()
        val count = ((w + gap) / (flag + gap)).toInt().coerceAtLeast(3)
        val sag = 8.dp.toPx()
        val top = 3.dp.toPx()
        fun yAt(x: Float) = top + sag * sin(PI * (x / w)).toFloat()
        val string = Path().apply {
            moveTo(0f, yAt(0f))
            var x = 0f
            while (x <= w) {
                lineTo(x, yAt(x))
                x += 6f
            }
        }
        drawPath(string, outline, style = Stroke(width = 2.dp.toPx()))
        val step = w / count
        for (i in 0 until count) {
            val cx = step * i + step / 2f
            val left = cx - flag / 2f
            val right = cx + flag / 2f
            val tri = Path().apply {
                moveTo(left, yAt(left))
                lineTo(right, yAt(right))
                lineTo(cx, yAt(cx) + flag * 0.95f)
                close()
            }
            drawPath(tri, colors[i % colors.size])
            drawPath(tri, outline, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
        }
    }
}
