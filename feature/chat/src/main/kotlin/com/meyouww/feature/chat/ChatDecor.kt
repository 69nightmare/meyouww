package com.meyouww.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.crayon.Wobble
import com.meyouww.core.designsystem.crayon.toSmoothPath
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette
import com.meyouww.core.model.RelationshipLevel

/*
 * The chat's look: crayon speech bubbles with a little tail and a comic shadow,
 * coloured by how close you are, over a sky with faint doodles (clouds for friends,
 * stars for best friends, hearts for a girlfriend / boyfriend).
 *
 * Every shape is built once per size in drawWithCache, so scrolling only replays
 * cached paths and stays smooth at 90 / 120 Hz.
 */

/** The space kept beside a bubble for its tail. */
val TAIL_WIDTH: Dp = 9.dp

/** Colours and doodles for one relationship level. */
@Immutable
data class ChatMood(
    /** Your bubbles, the level chip and the ring around their photo. */
    val accent: Color,
    /** Their bubbles. */
    val theirs: Color,
    /** Text on [theirs]. */
    val onTheirs: Color,
    val doodle: Doodle,
)

enum class Doodle { Cloud, Star, Heart }

@Composable
fun chatMood(level: RelationshipLevel?): ChatMood {
    val crayon = MeyouwwTheme.crayon
    val theirs = if (crayon.isDark) Color(Palette.NightSurfaceHigh) else Color(Palette.Paper)
    val onTheirs = if (crayon.isDark) Color(Palette.MoonText) else Color(Palette.Ink)
    return when (level) {
        RelationshipLevel.Partner -> ChatMood(crayon.bubblegum, theirs, onTheirs, Doodle.Heart)
        RelationshipLevel.BestFriend -> ChatMood(crayon.yellow, theirs, onTheirs, Doodle.Star)
        else -> ChatMood(crayon.lilac, theirs, onTheirs, Doodle.Cloud)
    }
}

/**
 * A crayon speech bubble: a slightly wobbly rounded box, a hard comic shadow and waxy
 * grain, with a curled tail at the bottom on the [start] (their) or end (your) side when
 * [tail] is true (only the last bubble in a run has one). [TAIL_WIDTH] is always kept
 * free on that side, so bubbles in a run line up; pad the content by it.
 */
fun Modifier.speechBubble(
    fill: Color,
    outline: Color,
    shadow: Color?,
    start: Boolean,
    tail: Boolean,
    seed: Int,
    grain: ImageBitmap?,
    radius: Dp = 20.dp,
): Modifier = this.drawWithCache {
    val sw = 2.4.dp.toPx()
    val amp = 0.6.dp.toPx()
    val tailW = TAIL_WIDTH.toPx()
    val bodyW = size.width - tailW
    val bodyLeft = if (start) tailW else 0f
    val body = Wobble.roundedRect(
        width = bodyW,
        height = size.height,
        radius = radius.toPx(),
        amplitude = amp,
        seed = seed,
        step = 14.dp.toPx(),
        inset = sw / 2f + amp,
    ).toSmoothPath().apply { translate(Offset(bodyLeft, 0f)) }
    val shape = when {
        !tail -> body
        else -> {
            val h = size.height
            val edge = if (start) bodyLeft + sw else size.width - tailW - sw
            val tip = if (start) sw / 2f else size.width - sw / 2f
            val inward = if (start) 1f else -1f
            val curl = Path().apply {
                moveTo(edge, h - 20.dp.toPx())
                // Down and out to the tip, then curl back under the bubble.
                quadraticTo(edge, h - 4.dp.toPx(), tip, h - sw)
                quadraticTo(edge + inward * 8.dp.toPx(), h - 2.dp.toPx(), edge + inward * 16.dp.toPx(), h - 6.dp.toPx())
                close()
            }
            Path.combine(PathOperation.Union, body, curl)
        }
    }
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val grainBrush = grain?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    val dx = 2.5.dp.toPx()
    val dy = 3.dp.toPx()
    onDrawBehind {
        if (shadow != null) translate(dx, dy) { drawPath(shape, shadow) }
        drawPath(shape, fill)
        if (grainBrush != null) drawPath(shape, grainBrush, alpha = 0.07f, blendMode = BlendMode.Multiply)
        drawPath(shape, outline, style = stroke)
    }
}

/** Where the faint doodles sit behind the chat (x, y as fractions; size in dp; tilt). */
private val DOODLES = listOf(
    floatArrayOf(0.08f, 0.10f, 26f, -12f),
    floatArrayOf(0.86f, 0.18f, 20f, 10f),
    floatArrayOf(0.20f, 0.36f, 16f, 18f),
    floatArrayOf(0.92f, 0.44f, 28f, -8f),
    floatArrayOf(0.06f, 0.60f, 22f, 6f),
    floatArrayOf(0.70f, 0.66f, 15f, -20f),
    floatArrayOf(0.30f, 0.84f, 24f, 12f),
    floatArrayOf(0.88f, 0.90f, 18f, -4f),
)

/** Faint crayon doodles behind the messages, in the mood's colour. Drawn once per size. */
fun Modifier.chatDoodles(mood: ChatMood, outline: Color): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height
    val paths = DOODLES.map { d -> Triple(Offset(d[0] * w, d[1] * h), doodlePath(mood.doodle, d[2].dp.toPx()), d[3]) }
    val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    onDrawBehind {
        paths.forEach { (at, path, tilt) -> drawDoodle(at, path, tilt, mood.accent, outline, stroke) }
    }
}

private fun DrawScope.drawDoodle(at: Offset, path: Path, tilt: Float, fill: Color, outline: Color, stroke: Stroke) {
    translate(at.x, at.y) {
        rotate(tilt, pivot = Offset.Zero) {
            drawPath(path, fill, alpha = 0.32f)
            drawPath(path, outline, alpha = 0.16f, style = stroke)
        }
    }
}

/** A doodle centred on (0, 0), about [s] px across. */
private fun doodlePath(kind: Doodle, s: Float): Path = if (kind == Doodle.Cloud) cloudPath(s / 2f) else Path().apply {
    val r = s / 2f
    when (kind) {
        Doodle.Heart -> {
            moveTo(0f, r * 0.9f)
            cubicTo(-r * 1.4f, -r * 0.1f, -r * 0.6f, -r * 1.1f, 0f, -r * 0.35f)
            cubicTo(r * 0.6f, -r * 1.1f, r * 1.4f, -r * 0.1f, 0f, r * 0.9f)
            close()
        }
        Doodle.Star -> {
            for (i in 0 until 10) {
                val angle = Math.toRadians((i * 36 - 90).toDouble())
                val rr = if (i % 2 == 0) r else r * 0.45f
                val x = (rr * Math.cos(angle)).toFloat()
                val y = (rr * Math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        Doodle.Cloud -> Unit
    }
}

/** Three puffs and a flat bottom, joined so the outline only goes round the outside. */
private fun cloudPath(r: Float): Path {
    fun puff(x: Float, y: Float, radius: Float) = Path().apply { addOval(Rect(Offset(x, y), radius)) }
    var cloud = Path().apply { addRoundRect(RoundRect(Rect(-r * 1.2f, -r * 0.1f, r * 1.2f, r * 0.5f), CornerRadius(r * 0.3f))) }
    listOf(puff(-r * 0.65f, -r * 0.05f, r * 0.5f), puff(0f, -r * 0.3f, r * 0.68f), puff(r * 0.65f, 0f, r * 0.48f)).forEach {
        cloud = Path.combine(PathOperation.Union, cloud, it)
    }
    return cloud
}
