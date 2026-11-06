package com.meyouww.core.render

import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import com.meyouww.core.scene.Background
import com.meyouww.core.scene.Element
import com.meyouww.core.scene.PhotoElement
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneTransform
import com.meyouww.core.scene.ShapeElement
import com.meyouww.core.scene.ShapeKind
import com.meyouww.core.scene.StickerElement
import com.meyouww.core.scene.StrokeElement
import com.meyouww.core.scene.TextElement
import com.meyouww.core.scene.Tool
import java.util.Random
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Draws a [Scene] onto any Android canvas through a [SceneTransform]. The studio,
 * the recipient preview and the exported wallpaper all call this, so what you see
 * is what they get.
 *
 * Everything is mapped to pixels by hand (no canvas scaling), so text, blur and
 * line widths render crisply at any size.
 */
class SceneRenderer(private val assets: RenderAssets) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val shaderMatrix = Matrix()
    private val grainShader by lazy { BitmapShader(assets.grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val clear = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    /**
     * @param hidden element IDs to skip (for example the one being dragged, drawn separately).
     * @param live a stroke still being drawn, on top of everything.
     */
    fun draw(
        canvas: Canvas,
        scene: Scene,
        t: SceneTransform,
        hidden: Set<String> = emptySet(),
        live: StrokeElement? = null,
    ) {
        drawBackground(canvas, scene, t)
        // Elements share one layer so the eraser removes ink but never the background.
        val layer = canvas.saveLayer(null, null)
        for (e in scene.elements) if (e.id !in hidden) drawElement(canvas, e, t)
        if (live != null) drawStroke(canvas, live, t)
        canvas.restoreToCount(layer)
    }

    fun drawElement(canvas: Canvas, e: Element, t: SceneTransform) {
        when (e) {
            is StrokeElement -> drawStroke(canvas, e, t)
            is ShapeElement -> drawShape(canvas, e, t)
            is TextElement -> drawText(canvas, e, t)
            is StickerElement -> drawSticker(canvas, e, t)
            is PhotoElement -> drawPhoto(canvas, e, t)
        }
    }

    // ---------------------------------------------------------------- background

    private fun drawBackground(canvas: Canvas, scene: Scene, t: SceneTransform) {
        val l = t.toPxX(0f)
        val top = t.toPxY(0f)
        val r = t.toPxX(1f)
        val b = t.toPxY(scene.aspect)
        reset()
        when (val bg = scene.background) {
            is Background.Solid -> {
                paint.color = bg.color
                canvas.drawRect(l, top, r, b, paint)
            }
            is Background.Gradient -> gradient(canvas, l, top, r, b, bg.top, bg.bottom)
            is Background.Preset -> Presets.draw(canvas, bg.id, scene.aspect, t, paint, path)
        }
    }

    private fun gradient(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, top: Int, bottom: Int) {
        paint.shader = LinearGradient(0f, t, 0f, b, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, b, paint)
        paint.shader = null
    }

    // ---------------------------------------------------------------- strokes

    private fun drawStroke(canvas: Canvas, s: StrokeElement, t: SceneTransform) {
        val n = s.pointCount
        if (n == 0) return
        val base = max(1f, s.width * t.scale)
        val opacity = s.opacity.coerceIn(0.05f, 1f)
        reset()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (s.tool) {
            Tool.Eraser -> {
                paint.xfermode = clear
                paint.strokeWidth = base
                canvas.drawPath(smoothPath(s, t), paint)
            }
            Tool.Highlighter -> withAlpha(canvas, 0.4f * opacity) {
                paint.color = opaque(s.color)
                paint.strokeCap = Paint.Cap.SQUARE
                paint.strokeWidth = base * 1.6f
                canvas.drawPath(smoothPath(s, t), paint)
            }
            Tool.Watercolor -> withAlpha(canvas, 0.5f * opacity) {
                paint.color = opaque(s.color)
                paint.strokeWidth = base * 1.4f
                paint.maskFilter = BlurMaskFilter(base * 0.35f, BlurMaskFilter.Blur.NORMAL)
                canvas.drawPath(smoothPath(s, t), paint)
                paint.maskFilter = null
                paint.alpha = 140
                paint.strokeWidth = base * 0.7f
                canvas.drawPath(smoothPath(s, t), paint)
            }
            Tool.Neon -> withAlpha(canvas, opacity) {
                paint.color = opaque(s.color)
                paint.strokeWidth = base * 1.5f
                paint.maskFilter = BlurMaskFilter(base * 0.9f, BlurMaskFilter.Blur.NORMAL)
                val p = smoothPath(s, t)
                canvas.drawPath(p, paint)
                paint.maskFilter = null
                paint.color = mix(s.color, Color.WHITE, 0.65f)
                paint.strokeWidth = base * 0.45f
                canvas.drawPath(p, paint)
            }
            Tool.Pencil, Tool.Crayon, Tool.Marker -> withAlpha(canvas, opacity) {
                paint.color = opaque(s.color)
                if (s.tool != Tool.Marker) {
                    // Grain scales with the design, so preview and export look the same.
                    val k = t.scale / GRAIN_REFERENCE_WIDTH
                    shaderMatrix.setScale(k, k)
                    grainShader.setLocalMatrix(shaderMatrix)
                    paint.shader = grainShader
                    paint.colorFilter = PorterDuffColorFilter(opaque(s.color), PorterDuff.Mode.SRC_IN)
                }
                val (minF, rangeF) = when (s.tool) {
                    Tool.Pencil -> 0.3f to 0.4f
                    Tool.Crayon -> 0.55f to 0.65f
                    else -> 0.8f to 0.3f
                }
                if (n == 1) {
                    paint.strokeWidth = base * (minF + rangeF * s.pressure(0))
                    canvas.drawPoint(t.toPxX(s.x(0)), t.toPxY(s.y(0)), paint)
                }
                for (i in 1 until n) {
                    val p = (s.pressure(i - 1) + s.pressure(i)) / 2f
                    paint.strokeWidth = base * (minF + rangeF * p)
                    canvas.drawLine(t.toPxX(s.x(i - 1)), t.toPxY(s.y(i - 1)), t.toPxX(s.x(i)), t.toPxY(s.y(i)), paint)
                }
            }
        }
        reset()
    }

    /** Quadratic curves through the midpoints: smooth without overshooting. */
    private fun smoothPath(s: StrokeElement, t: SceneTransform): Path {
        path.reset()
        val n = s.pointCount
        path.moveTo(t.toPxX(s.x(0)), t.toPxY(s.y(0)))
        if (n == 1) {
            path.lineTo(t.toPxX(s.x(0)) + 0.01f, t.toPxY(s.y(0)))
            return path
        }
        for (i in 1 until n) {
            val px = t.toPxX(s.x(i - 1))
            val py = t.toPxY(s.y(i - 1))
            val cx = t.toPxX(s.x(i))
            val cy = t.toPxY(s.y(i))
            path.quadTo(px, py, (px + cx) / 2f, (py + cy) / 2f)
        }
        path.lineTo(t.toPxX(s.x(n - 1)), t.toPxY(s.y(n - 1)))
        return path
    }

    // ---------------------------------------------------------------- shapes

    private fun drawShape(canvas: Canvas, e: ShapeElement, t: SceneTransform) {
        val cx = t.toPxX(e.x)
        val cy = t.toPxY(e.y)
        val hw = e.w * t.scale / 2f
        val hh = e.h * t.scale / 2f
        reset()
        paint.color = e.color
        paint.strokeWidth = max(1f, e.strokeWidth * t.scale)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        val lineLike = e.kind == ShapeKind.Line || e.kind == ShapeKind.Arrow
        paint.style = if (e.filled && !lineLike) Paint.Style.FILL else Paint.Style.STROKE
        canvas.save()
        canvas.rotate(e.rotation, cx, cy)
        val l = cx - kotlin.math.abs(hw)
        val r = cx + kotlin.math.abs(hw)
        val top = cy - kotlin.math.abs(hh)
        val b = cy + kotlin.math.abs(hh)
        when (e.kind) {
            ShapeKind.Rectangle -> {
                rect.set(l, top, r, b)
                val corner = minOf(r - l, b - top) * 0.12f
                canvas.drawRoundRect(rect, corner, corner, paint)
            }
            ShapeKind.Oval -> {
                rect.set(l, top, r, b)
                canvas.drawOval(rect, paint)
            }
            ShapeKind.Heart -> canvas.drawPath(heart(l, top, r, b), paint)
            ShapeKind.Star -> canvas.drawPath(star(cx, cy, minOf(r - l, b - top) / 2f), paint)
            ShapeKind.Line -> canvas.drawLine(cx - hw, cy - hh, cx + hw, cy + hh, paint)
            ShapeKind.Arrow -> {
                val x1 = cx - hw
                val y1 = cy - hh
                val x2 = cx + hw
                val y2 = cy + hh
                canvas.drawLine(x1, y1, x2, y2, paint)
                val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                val head = paint.strokeWidth * 4f
                for (side in listOf(-1, 1)) {
                    val a = angle + Math.PI + side * Math.PI / 7
                    canvas.drawLine(x2, y2, x2 + (head * cos(a)).toFloat(), y2 + (head * sin(a)).toFloat(), paint)
                }
            }
        }
        canvas.restore()
    }

    private fun heart(l: Float, t: Float, r: Float, b: Float): Path {
        val w = r - l
        val h = b - t
        path.reset()
        path.moveTo(l + w / 2f, b)
        path.cubicTo(l - w * 0.1f, t + h * 0.55f, l + w * 0.05f, t - h * 0.1f, l + w / 2f, t + h * 0.28f)
        path.cubicTo(r - w * 0.05f, t - h * 0.1f, r + w * 0.1f, t + h * 0.55f, l + w / 2f, b)
        path.close()
        return path
    }

    private fun star(cx: Float, cy: Float, radius: Float): Path {
        path.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) radius else radius * 0.45f
            val a = -Math.PI / 2 + i * Math.PI / 5
            val x = cx + (rr * cos(a)).toFloat()
            val y = cy + (rr * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    // ---------------------------------------------------------------- text

    private fun drawText(canvas: Canvas, e: TextElement, t: SceneTransform) {
        val size = max(1f, e.size * t.scale)
        val cx = t.toPxX(e.x)
        val cy = t.toPxY(e.y)
        val lines = e.text.lines()
        reset()
        paint.typeface = assets.typeface(e.font)
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        val lineHeight = size * 1.2f
        val firstBaseline = cy - (lines.size - 1) * lineHeight / 2f + size * 0.35f
        canvas.save()
        canvas.rotate(e.rotation, cx, cy)
        if (e.outline) {
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = size * 0.16f
            paint.color = if (luminance(e.color) > 0.6f) OUTLINE_DARK else Color.WHITE
            lines.forEachIndexed { i, line -> canvas.drawText(line, cx, firstBaseline + i * lineHeight, paint) }
        }
        paint.style = Paint.Style.FILL
        paint.color = e.color
        lines.forEachIndexed { i, line -> canvas.drawText(line, cx, firstBaseline + i * lineHeight, paint) }
        canvas.restore()
    }

    // ---------------------------------------------------------------- stickers and photos

    private fun drawSticker(canvas: Canvas, e: StickerElement, t: SceneTransform) {
        val cx = t.toPxX(e.x)
        val cy = t.toPxY(e.y)
        val half = e.size * t.scale / 2f
        canvas.save()
        canvas.rotate(e.rotation, cx, cy)
        val emoji = e.emoji
        if (emoji != null) {
            reset()
            paint.textSize = half * 1.7f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(emoji, cx, cy + half * 0.6f, paint)
        } else {
            assets.sticker(e.sticker)?.let { d ->
                d.setBounds((cx - half).toInt(), (cy - half).toInt(), (cx + half).toInt(), (cy + half).toInt())
                d.draw(canvas)
            }
        }
        canvas.restore()
    }

    private fun drawPhoto(canvas: Canvas, e: PhotoElement, t: SceneTransform) {
        val bitmap = assets.photo(e.file) ?: return
        val cx = t.toPxX(e.x)
        val cy = t.toPxY(e.y)
        val hw = e.width * t.scale / 2f
        val hh = e.width * e.photoAspect * t.scale / 2f
        reset()
        paint.alpha = (e.opacity.coerceIn(0f, 1f) * 255).toInt()
        if (e.brightness != 0f) {
            val shift = e.brightness.coerceIn(-1f, 1f) * 80f
            paint.colorFilter = ColorMatrixColorFilter(
                ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, shift, 0f, 1f, 0f, 0f, shift, 0f, 0f, 1f, 0f, shift, 0f, 0f, 0f, 1f, 0f)),
            )
        }
        canvas.save()
        canvas.rotate(e.rotation, cx, cy)
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
        canvas.drawBitmap(bitmap, null, rect, paint)
        canvas.restore()
        reset()
    }

    // ---------------------------------------------------------------- helpers

    private inline fun withAlpha(canvas: Canvas, alpha: Float, block: () -> Unit) {
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        if (a >= 255) {
            block()
            return
        }
        val save = canvas.saveLayerAlpha(null, a)
        block()
        canvas.restoreToCount(save)
    }

    private fun reset() {
        paint.reset()
        paint.flags = Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG
    }

    private companion object {
        const val GRAIN_REFERENCE_WIDTH = 1080f
        val OUTLINE_DARK = Color.rgb(35, 27, 30)

        fun opaque(color: Int): Int = color or (0xFF shl 24)

        fun mix(a: Int, b: Int, f: Float): Int = Color.rgb(
            (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
            (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
            (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt(),
        )

        fun luminance(color: Int): Float =
            (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
    }
}

/** The bundled "Doodle Town" backgrounds, drawn in code (original art, no files). */
internal object Presets {
    fun draw(canvas: Canvas, id: String, aspect: Float, t: SceneTransform, paint: Paint, path: Path) {
        val l = t.toPxX(0f)
        val top = t.toPxY(0f)
        val r = t.toPxX(1f)
        val b = t.toPxY(aspect)
        fun x(v: Float) = t.toPxX(v)
        fun y(v: Float) = t.toPxY(v * aspect)
        fun s(v: Float) = v * t.scale
        fun grad(c1: Int, c2: Int, from: Float = top, to: Float = b) {
            paint.shader = LinearGradient(0f, from, 0f, to, c1, c2, Shader.TileMode.CLAMP)
            canvas.drawRect(l, top, r, b, paint)
            paint.shader = null
        }
        fun cloud(cx: Float, cy: Float, size: Float) {
            paint.color = Color.argb(240, 255, 255, 255)
            canvas.drawCircle(x(cx), y(cy), s(size * 0.5f), paint)
            canvas.drawCircle(x(cx - size * 0.45f), y(cy) + s(size * 0.12f), s(size * 0.35f), paint)
            canvas.drawCircle(x(cx + size * 0.45f), y(cy) + s(size * 0.12f), s(size * 0.38f), paint)
        }
        val random = Random(id.hashCode().toLong())
        paint.style = Paint.Style.FILL
        when (id) {
            "night" -> {
                grad(Color.rgb(31, 42, 92), Color.rgb(74, 63, 140))
                repeat(46) {
                    paint.color = Color.argb(150 + random.nextInt(100), 255, 248, 220)
                    canvas.drawCircle(x(random.nextFloat()), y(random.nextFloat() * 0.8f), s(0.003f + random.nextFloat() * 0.006f), paint)
                }
                paint.color = Color.rgb(255, 243, 176)
                canvas.drawCircle(x(0.74f), y(0.14f), s(0.11f), paint)
                paint.color = Color.rgb(46, 52, 112)
                canvas.drawCircle(x(0.79f), y(0.12f), s(0.095f), paint)
            }
            "meadow" -> {
                grad(Color.rgb(207, 240, 255), Color.rgb(255, 246, 224), top, y(0.65f))
                paint.color = Color.rgb(142, 208, 129)
                path.reset()
                path.moveTo(l, y(0.68f))
                path.quadTo(x(0.3f), y(0.58f), x(0.6f), y(0.68f))
                path.quadTo(x(0.85f), y(0.74f), r, y(0.64f))
                path.lineTo(r, b)
                path.lineTo(l, b)
                path.close()
                canvas.drawPath(path, paint)
                paint.color = Color.rgb(108, 191, 106)
                path.reset()
                path.moveTo(l, y(0.8f))
                path.quadTo(x(0.5f), y(0.7f), r, y(0.82f))
                path.lineTo(r, b)
                path.lineTo(l, b)
                path.close()
                canvas.drawPath(path, paint)
                repeat(30) {
                    paint.color = if (random.nextBoolean()) Color.rgb(255, 143, 177) else Color.rgb(255, 216, 77)
                    canvas.drawCircle(x(random.nextFloat()), y(0.74f + random.nextFloat() * 0.24f), s(0.012f), paint)
                }
                cloud(0.3f, 0.12f, 0.2f)
            }
            "candy" -> {
                grad(Color.rgb(255, 214, 232), Color.rgb(255, 233, 199))
                paint.color = Color.argb(110, 255, 255, 255)
                var yy = 0.04f
                var row = 0
                while (yy < aspect) {
                    var xx = if (row % 2 == 0) 0.06f else 0.12f
                    while (xx < 1f) {
                        canvas.drawCircle(x(xx), t.toPxY(yy), s(0.022f), paint)
                        xx += 0.12f
                    }
                    yy += 0.1f
                    row++
                }
            }
            "sea" -> {
                grad(Color.rgb(143, 211, 255), Color.rgb(60, 158, 231))
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s(0.008f)
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.argb(90, 255, 255, 255)
                var yy = 0.35f
                while (yy < 1f) {
                    path.reset()
                    path.moveTo(l, y(yy))
                    var xx = 0f
                    while (xx < 1f) {
                        path.quadTo(x(xx + 0.05f), y(yy) - s(0.025f), x(xx + 0.1f), y(yy))
                        path.quadTo(x(xx + 0.15f), y(yy) + s(0.025f), x(xx + 0.2f), y(yy))
                        xx += 0.2f
                    }
                    canvas.drawPath(path, paint)
                    yy += 0.09f
                }
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(255, 216, 77)
                canvas.drawCircle(x(0.2f), y(0.1f), s(0.08f), paint)
            }
            else -> { // "sky"
                grad(Color.rgb(189, 231, 255), Color.rgb(255, 246, 224))
                paint.color = Color.rgb(255, 216, 77)
                canvas.drawCircle(x(0.8f), y(0.1f), s(0.12f), paint)
                cloud(0.25f, 0.2f, 0.22f)
                cloud(0.72f, 0.36f, 0.17f)
                cloud(0.35f, 0.58f, 0.14f)
            }
        }
    }
}
