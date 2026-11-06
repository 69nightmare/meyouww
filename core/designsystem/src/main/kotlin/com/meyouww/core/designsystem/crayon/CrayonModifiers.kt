package com.meyouww.core.designsystem.crayon

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Turns wobble samples into a smooth closed path (quadratic curves through midpoints). */
fun List<OutlinePoint>.toSmoothPath(): Path {
    val path = Path()
    if (isEmpty()) return path
    val n = size
    val first = this[0]
    val last = this[n - 1]
    path.moveTo((last.x + first.x) / 2f, (last.y + first.y) / 2f)
    for (i in 0 until n) {
        val p = this[i]
        val next = this[(i + 1) % n]
        path.quadraticTo(p.x, p.y, (p.x + next.x) / 2f, (p.y + next.y) / 2f)
    }
    path.close()
    return path
}

/**
 * Paints a filled shape with a hand-drawn crayon outline, an optional comic
 * offset shadow and an optional waxy grain. The path is cached per size, so the
 * outline is computed once and never shimmers.
 *
 * The outline doubles as the accessibility boundary: pale fills alone do not
 * reach 3:1 against the paper background, the ink outline does.
 */
fun Modifier.crayonSurface(
    fill: Color,
    outline: Color,
    seed: Int,
    cornerRadius: Dp = 18.dp,
    strokeWidth: Dp = 2.5.dp,
    wobble: Dp = 0.8.dp,
    shadow: Color? = null,
    shadowOffsetX: Dp = 2.dp,
    shadowOffsetY: Dp = 3.dp,
    grain: ImageBitmap? = null,
    grainAlpha: Float = 0.08f,
): Modifier = this.drawWithCache {
    val sw = strokeWidth.toPx()
    val amp = wobble.toPx()
    val outlinePath = Wobble.roundedRect(
        width = size.width,
        height = size.height,
        radius = cornerRadius.toPx(),
        amplitude = amp,
        seed = seed,
        step = 12.dp.toPx(),
        inset = sw / 2f + amp,
    ).toSmoothPath()
    val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val grainBrush = grain?.let { ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)) }
    val shadowOffset = Offset(shadowOffsetX.toPx(), shadowOffsetY.toPx())
    onDrawBehind {
        if (shadow != null) {
            translate(shadowOffset.x, shadowOffset.y) { drawPath(outlinePath, shadow) }
        }
        if (fill.alpha > 0f) drawPath(outlinePath, fill)
        if (grainBrush != null && fill.alpha > 0f) {
            drawPath(outlinePath, grainBrush, alpha = grainAlpha, blendMode = BlendMode.Multiply)
        }
        drawPath(outlinePath, outline, style = stroke)
    }
}
