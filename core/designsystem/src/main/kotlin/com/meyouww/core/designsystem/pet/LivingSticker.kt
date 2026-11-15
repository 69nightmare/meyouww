package com.meyouww.core.designsystem.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.cycle
import com.meyouww.core.designsystem.component.pingPong
import com.meyouww.core.designsystem.component.skyHeart
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.LocalSky
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** True where holding Whity may open her corner (signed in). The app turns it on for its main screens. */
val LocalWhityCorner = staticCompositionLocalOf { false }

/** How a whole sticker moves while idle. */
internal enum class BodyLoop { PeekUp, Stretch, Nod, Wiggle, Tilt, Wave, Beat, Hop, Snooze, Breathe }

/** What a moving part does: ears twitch, tails wag, paws wave, drawn hearts pulse, things bob or tap. */
internal enum class PartKind { Ear, Tail, Paw, Pulse, Bob, Tap }

/** One moving part: a polygon (x, y pairs, in %) that turns round its joint ([pivotX], [pivotY]) by up to [amount] degrees. */
internal class RigPart(val kind: PartKind, val polygon: FloatArray, val pivotX: Float, val pivotY: Float, val amount: Float)

/** One eye that glances round and blinks: centre and radius in % of the width; [lid] is the fur colour above it. */
internal class RigEye(val x: Float, val y: Float, val r: Float, val lid: Long)

/** Everything that moves on one sticker, and where her hearts (or Zzz) come from. */
internal class WhityRig(
    val loop: BodyLoop,
    val emitX: Float,
    val emitY: Float,
    val sleepy: Boolean,
    val parts: List<RigPart>,
    val holes: List<FloatArray>,
    val eyes: List<RigEye> = emptyList(),
)

/** A sticker without its own rig still breathes, with hearts floating from the top. */
private val PLAIN_RIG = WhityRig(BodyLoop.Breathe, emitX = 50f, emitY = 10f, sleepy = false, parts = emptyList(), holes = emptyList())

/** Keyframes: [points] are (position 0..1, value) pairs; eased between them. */
private fun keys(p: Float, vararg points: Float): Float {
    var i = 0
    while (i + 3 < points.size && p > points[i + 2]) i += 2
    val (p0, v0) = points[i] to points[i + 1]
    if (i + 3 >= points.size) return v0
    val (p1, v1) = points[i + 2] to points[i + 3]
    if (p <= p0) return v0
    val t = ((p - p0) / (p1 - p0)).coerceIn(0f, 1f)
    val e = t * t * (3 - 2 * t)
    return v0 + (v1 - v0) * e
}

/** Where (in % of the sticker) and how (scale x, scale y, turn, lift as a fraction of height) the whole body is at [ms]. */
private fun body(loop: BodyLoop, ms: Long, out: FloatArray) {
    out[0] = 1f; out[1] = 1f; out[2] = 0f; out[3] = 0f
    when (loop) {
        BodyLoop.PeekUp -> {
            val k = pingPong(ms, 4_600)
            out[3] = 0.03f - 0.07f * k
            out[2] = -1.2f * k
        }
        BodyLoop.Stretch -> {
            val p = cycle(ms, 4_200)
            val k = keys(p, 0f, 0f, 0.45f, 1f, 0.6f, 1f, 1f, 0f)
            out[0] = 1f + 0.06f * k; out[1] = 1f - 0.06f * k
        }
        BodyLoop.Nod -> {
            val k = pingPong(ms, 3_800)
            out[2] = -3f + 6f * k
            out[3] = -0.025f * k
        }
        BodyLoop.Wiggle -> out[2] = keys(cycle(ms, 3_400), 0f, 0f, 0.08f, -4f, 0.16f, 4f, 0.24f, -4f, 0.32f, 4f, 0.4f, -4f, 0.46f, 0f, 1f, 0f)
        BodyLoop.Tilt -> out[2] = -2.5f + 5f * pingPong(ms, 4_400)
        BodyLoop.Wave -> out[2] = keys(cycle(ms, 3_000), 0f, 0f, 0.1f, -5f, 0.2f, 4f, 0.3f, -5f, 0.38f, 4f, 0.44f, 0f, 1f, 0f)
        BodyLoop.Beat -> {
            val s = keys(cycle(ms, 1_900), 0f, 1f, 0.12f, 1.08f, 0.22f, 1.01f, 0.3f, 1.06f, 0.4f, 1f, 1f, 1f)
            out[0] = s; out[1] = s
        }
        BodyLoop.Hop -> {
            val p = cycle(ms, 2_800)
            out[0] = keys(p, 0f, 1f, 0.1f, 1.06f, 0.3f, 0.97f, 0.46f, 1.04f, 0.58f, 1f, 1f, 1f)
            out[1] = keys(p, 0f, 1f, 0.1f, 0.92f, 0.3f, 1.05f, 0.46f, 0.95f, 0.58f, 1f, 1f, 1f)
            out[3] = keys(p, 0f, 0f, 0.1f, 0f, 0.3f, -0.1f, 0.46f, 0f, 1f, 0f)
        }
        BodyLoop.Snooze -> {
            val k = pingPong(ms, 5_200)
            out[0] = 1f + 0.03f * k; out[1] = 1f + 0.05f * k
        }
        BodyLoop.Breathe -> {
            val k = pingPong(ms, 4_000)
            out[0] = 1f + 0.035f * k; out[1] = 1f + 0.035f * k
        }
    }
}

/** How far a part has turned (degrees), grown (scale) or moved (fraction of the height) at [ms]. */
private fun partAt(part: RigPart, index: Int, ms: Long): Float = when (part.kind) {
    PartKind.Ear -> {
        val a = part.amount
        keys(cycle(ms, 4_200, index * 180L), 0f, 0f, 0.58f, 0f, 0.64f, a, 0.72f, -0.6f * a, 0.8f, 0.5f * a, 0.9f, 0f, 1f, 0f)
    }
    PartKind.Tail -> -part.amount + 2 * part.amount * pingPong(ms, 920)
    PartKind.Paw -> {
        val a = part.amount
        keys(cycle(ms, 2_600, index * 120L), 0f, 0f, 0.1f, a, 0.2f, -0.7f * a, 0.3f, a, 0.4f, -0.7f * a, 0.52f, 0f, 1f, 0f)
    }
    PartKind.Pulse -> keys(cycle(ms, 1_700, index * 350L), 0f, 1f, 0.14f, 1.17f, 0.26f, 1.04f, 0.34f, 1.12f, 0.42f, 1f, 1f, 1f)
    PartKind.Bob -> -0.03f * pingPong(ms, 2_600)
    PartKind.Tap -> keys(cycle(ms, 3_200), 0f, 0f, 0.66f, 0f, 0.74f, -0.03f, 0.81f, 0f, 0.88f, -0.03f, 0.95f, 0f, 1f, 0f)
}

/** Eyes glance right, left and up, then back, blinking twice: (x, y) as fractions of the eye, and how closed the lid is. */
private fun eyesAt(ms: Long, out: FloatArray) {
    val p = cycle(ms, 6_400)
    out[0] = keys(p, 0f, 0f, 0.12f, 0f, 0.17f, 0.2f, 0.32f, 0.2f, 0.37f, -0.2f, 0.5f, -0.2f, 0.55f, 0.04f, 0.68f, 0.04f, 0.73f, 0f, 1f, 0f)
    out[1] = keys(p, 0f, 0f, 0.12f, 0f, 0.17f, 0.02f, 0.32f, 0.02f, 0.37f, 0.04f, 0.5f, 0.04f, 0.55f, -0.14f, 0.68f, -0.14f, 0.73f, 0f, 1f, 0f)
    out[2] = keys(p, 0f, 0f, 0.34f, 0f, 0.352f, 1f, 0.365f, 0f, 0.84f, 0f, 0.852f, 1f, 0.865f, 0f, 1f, 0f)
}

private fun polygon(points: FloatArray, left: Float, top: Float, w: Float, h: Float): Path = Path().apply {
    for (i in points.indices step 2) {
        val x = left + points[i] / 100f * w
        val y = top + points[i + 1] / 100f * h
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A small Z for a sleepy sticker's floating Zzz, [s] across, centred on (0, 0). */
private fun zee(s: Float): Path = Path().apply {
    moveTo(-s / 2, -s / 2)
    lineTo(s / 2, -s / 2)
    lineTo(-s / 2, s / 2)
    lineTo(s / 2, s / 2)
}

/**
 * A Whity sticker that is alive (the Living Sky look): her ears twitch, her tail wags, her paws
 * wave, her eyes glance about and blink, and hearts (or Zzz) float up, each sticker in its own
 * way. With [touch] she can be petted: a tap makes her hop with a burst of hearts, and holding her
 * fills a ring and opens her corner (where [LocalWhityCorner] allows it). Everything stands
 * still when motion is off; she still reacts to taps then, without moving.
 */
@Composable
fun LivingSticker(
    sticker: WhitySticker,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    decorative: Boolean = false,
    touch: Boolean = decorative && size >= 64.dp,
) {
    val sky = LocalSky.current
    val bitmap = ImageBitmap.imageResource(sticker.drawable)
    val rig = WHITY_RIGS[sticker] ?: PLAIN_RIG
    val reduced = LocalReducedMotion.current
    val accent = MeyouwwTheme.crayon.red
    val hop = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val cornerAllowed = LocalWhityCorner.current
    var corner by remember { mutableStateOf(false) }
    val label = if (decorative) null else stringResource(sticker.label)
    val petLabel = stringResource(R.string.whity_pet_action)
    val cornerLabel = stringResource(R.string.whity_corner_open)
    val interaction = remember { MutableInteractionSource() }
    val holdMs = LocalViewConfiguration.current.longPressTimeoutMillis

    if (touch && cornerAllowed) {
        // The ring fills while she is held, and drains if let go early.
        LaunchedEffect(interaction) {
            interaction.interactions.collect { event ->
                when (event) {
                    is PressInteraction.Press -> scope.launch { ring.snapTo(0f); ring.animateTo(1f, tween(holdMs.toInt(), easing = LinearEasing)) }
                    is PressInteraction.Release, is PressInteraction.Cancel -> scope.launch { ring.animateTo(0f, tween(180)) }
                }
            }
        }
    }

    val touchModifier = if (touch) {
        Modifier
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = petLabel,
                onLongClickLabel = if (cornerAllowed) cornerLabel else null,
                // The long press buzzes by itself.
                onLongClick = if (cornerAllowed) {
                    { corner = true }
                } else {
                    null
                },
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    WhityPetStore.update(context) { WhityRules.pet(it, WhityPetState.TAP_LIFT) }
                    scope.launch {
                        hop.snapTo(0f)
                        if (!reduced) hop.animateTo(1f, tween(800, easing = LinearEasing))
                    }
                },
            )
            .semantics { contentDescription = label ?: petLabel }
    } else if (label != null) {
        Modifier.semantics {
            contentDescription = label
            role = Role.Image
        }
    } else {
        Modifier
    }

    Box(
        modifier
            .size(size)
            .then(touchModifier)
            .drawWithCache {
                val w = this.size.width
                val h = this.size.height
                val iw = bitmap.width.toFloat()
                val ih = bitmap.height.toFloat()
                val fit = minOf(w / iw, h / ih)
                val dw = iw * fit
                val dh = ih * fit
                val left = (w - dw) / 2f
                val top = (h - dh) / 2f
                val dst = IntOffset(left.roundToInt(), top.roundToInt())
                val dstSize = IntSize(dw.roundToInt(), dh.roundToInt())
                val holes = Path().apply {
                    fillType = PathFillType.NonZero
                    rig.holes.forEach { addPath(polygon(it, left, top, dw, dh)) }
                }
                val parts = rig.parts.map { polygon(it.polygon, left, top, dw, dh) }
                val heartSize = maxOf(dw * 0.1f, 9.dp.toPx())
                val heart = skyHeart(heartSize)
                val zz = zee(heartSize * 0.8f)
                val ringPath = Path().apply {
                    val g = 6.dp.toPx()
                    addRoundRect(RoundRect(left - g, top - g, left + dw + g, top + dh + g, CornerRadius(22.dp.toPx())))
                }
                val ringMeasure = PathMeasure().apply { setPath(ringPath, false) }
                val ringPiece = Path()
                val ringStroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                val pose = FloatArray(4)
                val eyes = FloatArray(3)
                val small = dw < 56.dp.toPx()
                fun DrawScope.picture() = drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), dst, dstSize, filterQuality = FilterQuality.Medium)
                onDrawBehind {
                    val ms = if (reduced) 0L else sky?.millis ?: 0L
                    body(rig.loop, ms, pose)
                    // A tap: squash, jump, land.
                    val k = hop.value
                    val hopSx = keys(k, 0f, 1f, 0.05f, 1.07f, 0.35f, 0.96f, 0.7f, 1.03f, 1f, 1f)
                    val hopSy = keys(k, 0f, 1f, 0.05f, 0.9f, 0.35f, 1.07f, 0.7f, 0.97f, 1f, 1f)
                    val hopLift = keys(k, 0f, 0f, 0.05f, 0f, 0.35f, -0.12f, 0.7f, 0f, 1f, 0f)
                    val pivot = Offset(left + dw / 2f, top + dh)
                    withTransform({
                        translate(0f, (pose[3] + hopLift) * dh)
                        rotate(pose[2], pivot)
                        scale(pose[0] * hopSx, pose[1] * hopSy, pivot)
                    }) {
                        if (rig.parts.isEmpty()) {
                            picture()
                        } else {
                            clipPath(holes, ClipOp.Difference) { picture() }
                            rig.parts.forEachIndexed { i, part ->
                                val v = partAt(part, i, ms)
                                val joint = Offset(left + part.pivotX / 100f * dw, top + part.pivotY / 100f * dh)
                                withTransform({
                                    when (part.kind) {
                                        PartKind.Ear, PartKind.Tail, PartKind.Paw -> rotate(v, joint)
                                        PartKind.Pulse -> scale(v, v, joint)
                                        PartKind.Bob, PartKind.Tap -> translate(0f, v * dh)
                                    }
                                }) { clipPath(parts[i]) { picture() } }
                            }
                            eyesAt(ms, eyes)
                            rig.eyes.forEach { eye ->
                                val c = Offset(left + eye.x / 100f * dw, top + eye.y / 100f * dh)
                                val r = eye.r / 100f * dw
                                val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                                clipPath(disc) {
                                    drawCircle(Color(0xFFEDE4DD), r, c)
                                    val iris = Offset(c.x + eyes[0] * 2.28f * r, c.y + eyes[1] * 2.28f * r)
                                    drawCircle(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF4A2F1E), Color(0xFF22140D), Color(0xFF0D0705)),
                                            center = Offset(iris.x - r * 0.18f, iris.y - r * 0.3f),
                                            radius = r * 1.14f,
                                        ),
                                        r * 1.14f,
                                        iris,
                                    )
                                    drawCircle(Color.White, r * 0.3f, Offset(iris.x - r * 0.3f, iris.y - r * 0.42f))
                                    val lidTop = c.y - r - 2.1f * r * (1f - eyes[2])
                                    drawRect(Color(eye.lid), Offset(c.x - r * 1.2f, lidTop - r * 0.1f), androidx.compose.ui.geometry.Size(r * 2.4f, r * 2.2f))
                                    if (eyes[2] > 0.05f) {
                                        drawRect(Color(0xCC462D1E), Offset(c.x - r * 1.2f, lidTop + r * 1.15f), androidx.compose.ui.geometry.Size(r * 2.4f, r * 0.3f))
                                    }
                                }
                            }
                        }
                    }
                    // Hearts (or Zzz) floating up while she's alive.
                    if (ms > 0 && !small) {
                        val ex = left + rig.emitX / 100f * dw
                        val ey = top + rig.emitY / 100f * dh
                        listOf(-16f to 0L, 0f to 1_500L, 15f to 2_900L).forEachIndexed { i, (dx, delay) ->
                            val p = cycle(ms, 4_400, delay)
                            val a = keys(p, 0f, 0f, 0.14f, 1f, 0.62f, 0.9f, 1f, 0f)
                            val drift = floatArrayOf(0.5f, -1.5f, 1.4f)[i] * heartSize * p
                            val s = 0.4f + 0.65f * p
                            withTransform({
                                translate(ex + dx / 100f * dw + drift, ey - 4.3f * heartSize * p)
                                scale(s, s, Offset.Zero)
                            }) {
                                if (rig.sleepy) {
                                    drawPath(zz, Color(0xFF3F6FA8), alpha = a, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                                } else {
                                    drawPath(heart, Color(0xFFFF86A6), alpha = a)
                                    drawPath(heart, Color(0xFFD8456F), alpha = a, style = Stroke(1.dp.toPx()))
                                }
                            }
                        }
                    }
                    // A tap's burst of hearts.
                    if (k > 0f && k < 1f) {
                        val from = Offset(left + dw / 2f, top + dh * 0.32f)
                        listOf(Offset(0f, -66f), Offset(-40f, -50f), Offset(36f, -56f)).forEachIndexed { i, to ->
                            val p = ((k - i * 0.1f) / 0.9f).coerceIn(0f, 1f)
                            if (p <= 0f) return@forEachIndexed
                            val a = keys(p, 0f, 0f, 0.25f, 1f, 1f, 0f)
                            val s = 0.4f + 0.95f * p
                            val bx = from.x + to.x.dp.toPx() * p
                            val by = from.y + to.y.dp.toPx() * p
                            withTransform({
                                translate(bx, by)
                                scale(s, s, Offset.Zero)
                            }) { drawPath(heart, Color(0xFFFF86A6), alpha = a) }
                        }
                    }
                    // Holding her: a ring fills round her, then her corner opens.
                    val held = ring.value
                    if (held > 0f) {
                        ringPiece.reset()
                        ringMeasure.getSegment(0f, ringMeasure.length * held, ringPiece, true)
                        drawPath(ringPiece, accent, style = ringStroke)
                    }
                }
            },
    )
    if (corner) WhityCorner(onDismiss = { corner = false })
}
