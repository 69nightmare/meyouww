package com.meyouww.feature.studio

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.input.motionprediction.MotionEventPredictor
import com.meyouww.core.render.SceneRenderer
import com.meyouww.core.scene.SceneTransform
import com.meyouww.core.scene.ShapeElement
import com.meyouww.core.scene.StrokeBuilder
import com.meyouww.core.scene.StrokeElement
import com.meyouww.core.scene.Tool
import com.meyouww.core.scene.bounds
import com.meyouww.core.scene.placedAt

/** Crayon box colours with names for TalkBack. */
object Palette {
    data class PaletteColor(val color: Int, @param:StringRes val name: Int)

    val colors = listOf(
        PaletteColor(0xFFFF4B4B.toInt(), R.string.c_red),
        PaletteColor(0xFFFF9F43.toInt(), R.string.c_orange),
        PaletteColor(0xFFFFD84D.toInt(), R.string.c_yellow),
        PaletteColor(0xFF6CCB5F.toInt(), R.string.c_green),
        PaletteColor(0xFF4DB8FF.toInt(), R.string.c_sky),
        PaletteColor(0xFF3D6CFF.toInt(), R.string.c_blue),
        PaletteColor(0xFFA88BFF.toInt(), R.string.c_lilac),
        PaletteColor(0xFFFF8FB1.toInt(), R.string.c_pink),
        PaletteColor(0xFF8B5E3C.toInt(), R.string.c_brown),
        PaletteColor(0xFF231B1E.toInt(), R.string.c_black),
        PaletteColor(0xFF9E9E9E.toInt(), R.string.c_grey),
        PaletteColor(0xFFFFFFFF.toInt(), R.string.c_white),
    )
}

/**
 * The canvas. Committed work is cached in a bitmap; each frame only adds the
 * stroke in progress (plus predicted points, so the line keeps up with the pen).
 */
@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ClickableViewAccessibility")
@Composable
fun DrawingSurface(
    state: StudioUiState,
    renderer: SceneRenderer,
    onStroke: (StrokeElement) -> Unit,
    onShape: (Float, Float, Float, Float) -> Unit,
    onTextAt: () -> Unit,
    onSelect: (String?) -> Unit,
    onTransform: (dx: Float, dy: Float, zoom: Float, rotation: Float) -> Unit,
    newId: () -> String,
    modifier: Modifier = Modifier,
) {
    val scene = state.scene
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transform = remember(size, scene.aspect) {
        if (size.width == 0 || size.height == 0) null else SceneTransform.fit(scene.aspect, size.width.toFloat(), size.height.toFloat())
    }
    val view = LocalView.current
    val predictor = remember(view) { MotionEventPredictor.newInstance(view) }
    val builder = remember { StrokeBuilder() }
    var live by remember { mutableStateOf<StrokeElement?>(null) }
    // A finished stroke stays drawn on top until the cache has caught up (no flicker).
    var settling by remember { mutableStateOf<StrokeElement?>(null) }
    var liveShape by remember { mutableStateOf<ShapeElement?>(null) }
    var shapeStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var stylusSeen by remember { mutableStateOf(false) }

    // The cache is redrawn whenever the committed scene changes.
    val cache = remember(size) {
        if (size.width > 0 && size.height > 0) Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888) else null
    }
    DisposableEffect(cache) { onDispose { cache?.recycle() } }
    var cacheVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(cache, scene, transform) {
        val bmp = cache ?: return@LaunchedEffect
        val t = transform ?: return@LaunchedEffect
        val c = android.graphics.Canvas(bmp)
        c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        renderer.draw(c, scene, t)
        cacheVersion++
        settling = null
    }

    val drawTool = state.tool.drawTool
    val selectionPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFFFF4FA3.toInt()
            pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
    }
    val canvasLabel = stringResource(R.string.studio_canvas)

    val input = when {
        state.tool == StudioTool.Move -> Modifier
            .pointerInput(scene, transform) {
                detectTapGestures { offset ->
                    val t = transform ?: return@detectTapGestures
                    onSelect(scene.placedAt(t.toDesignX(offset.x), t.toDesignY(offset.y))?.id)
                }
            }
            .pointerInput(transform, state.selectedId) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    val t = transform ?: return@detectTransformGestures
                    onTransform(pan.x / t.scale, pan.y / t.scale, zoom, rotation)
                }
            }
        state.tool == StudioTool.Text -> Modifier.pointerInput(Unit) { detectTapGestures { onTextAt() } }
        else -> Modifier.pointerInteropFilter { event ->
            val t = transform ?: return@pointerInteropFilter false
            val tooling = event.getToolType(0)
            if (tooling == MotionEvent.TOOL_TYPE_STYLUS || tooling == MotionEvent.TOOL_TYPE_ERASER) stylusSeen = true
            // Palm-friendly: once a pen is used, resting fingers don't draw.
            if (stylusSeen && tooling == MotionEvent.TOOL_TYPE_FINGER && event.actionMasked == MotionEvent.ACTION_DOWN) {
                return@pointerInteropFilter false
            }
            fun pressureAt(p: Float) = if (tooling == MotionEvent.TOOL_TYPE_STYLUS) p else 0.6f
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    predictor.record(event)
                    if (drawTool != null) {
                        builder.clear()
                        builder.add(t.toDesignX(event.x), t.toDesignY(event.y), pressureAt(event.pressure))
                        live = liveStroke(builder.snapshot(), drawTool, state)
                    } else {
                        shapeStart = t.toDesignX(event.x) to t.toDesignY(event.y)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    predictor.record(event)
                    if (drawTool != null) {
                        for (h in 0 until event.historySize) {
                            builder.add(t.toDesignX(event.getHistoricalX(0, h)), t.toDesignY(event.getHistoricalY(0, h)), pressureAt(event.getHistoricalPressure(0, h)))
                        }
                        builder.add(t.toDesignX(event.x), t.toDesignY(event.y), pressureAt(event.pressure))
                        val points = builder.snapshot().toMutableList()
                        predictor.predict()?.let { predicted ->
                            points += t.toDesignX(predicted.x)
                            points += t.toDesignY(predicted.y)
                            points += pressureAt(predicted.pressure)
                            predicted.recycle()
                        }
                        live = liveStroke(points, drawTool, state)
                    } else {
                        shapeStart?.let { (sx, sy) ->
                            liveShape = ShapeElement(
                                "live", state.shape, (sx + t.toDesignX(event.x)) / 2f, (sy + t.toDesignY(event.y)) / 2f,
                                t.toDesignX(event.x) - sx, t.toDesignY(event.y) - sy, 0f, state.color, state.size, state.shapeFilled,
                            )
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (drawTool != null) {
                        builder.add(t.toDesignX(event.x), t.toDesignY(event.y), pressureAt(event.pressure))
                        builder.build(newId(), drawTool, state.color, state.size, state.opacity)?.let {
                            settling = it
                            onStroke(it)
                        }
                        builder.clear()
                        live = null
                    } else {
                        shapeStart?.let { (sx, sy) -> onShape(sx, sy, t.toDesignX(event.x), t.toDesignY(event.y)) }
                        shapeStart = null
                        liveShape = null
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                    // A second finger or a system gesture: drop the stroke rather than scribble.
                    builder.clear()
                    live = null
                    shapeStart = null
                    liveShape = null
                    true
                }
                else -> true
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .semantics { contentDescription = canvasLabel }
            .then(input),
    ) {
        // Reading the version makes this redraw whenever the cache is refreshed.
        if (cacheVersion < 0) return@Canvas
        val t = transform ?: return@Canvas
        drawIntoCanvas { c ->
            val native = c.nativeCanvas
            val stroke = live ?: settling
            if (stroke != null && stroke.tool == Tool.Eraser) {
                // Erasing needs the real layers, so redraw everything with the eraser applied.
                renderer.draw(native, scene, t, live = stroke)
            } else {
                cache?.let { native.drawBitmap(it, 0f, 0f, null) }
                if (stroke != null) renderer.drawElement(native, stroke, t)
            }
            liveShape?.let { renderer.drawElement(native, it, t) }
            state.selected?.let { sel ->
                val b = sel.bounds()
                native.drawRect(t.toPxX(b.left) - 8f, t.toPxY(b.top) - 8f, t.toPxX(b.right) + 8f, t.toPxY(b.bottom) + 8f, selectionPaint)
            }
        }
    }
}

private fun liveStroke(points: List<Float>, tool: Tool, state: StudioUiState) =
    StrokeElement("live", tool, state.color, state.size, state.opacity, points)

/** Shown in the tool bar. */
@Composable
fun toolLabel(tool: StudioTool): String = stringResource(tool.label)
