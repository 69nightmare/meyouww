package com.meyouww.core.designsystem.pet

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.theme.MeyouwwTextStyles
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** Makes Whity react from outside (a treat given, a message arrived). A new [id] plays it again. */
data class WhityCue(val reaction: WhityReaction, val id: Long = System.nanoTime())

/** Taps this close together make her dizzy. */
private const val DIZZY_TAPS = 6
private const val DIZZY_WINDOW_MS = 2_500L

/** One stroke counts as one pet; stroking on counts again after the bliss has played. */
private const val PET_REPEAT_MS = 2_400L

/** When a chatty Whity says something by herself after you arrive. */
private const val CHAT_AFTER_MS = 1_500L

/**
 * Whity, the app's pet, sitting on a screen. Tap her for a happy bounce, stroke her to pet her
 * (her eyes close, she blushes and the phone purrs), hold her for her corner (mood, treat jar,
 * streak and outfits). Lots of quick taps make her dizzy. At night, or when her mood is low,
 * she naps. When animations are off she changes poses without moving. She wears her outfit
 * (or the day's festival one) and munches the day's treat.
 *
 * [width] sets her size; [idle] replaces what she does when nothing is happening (say, heart
 * eyes for new messages); a new [cue] makes her react; [corner] = false inside her corner
 * itself; [description] adds to what TalkBack says about her. A [chatty] Whity says something
 * in a speech bubble now and then (and when tapped), including [next], what's coming up on her
 * screen. [onHold], when given, is called instead of opening her corner here (for a Whity who
 * is about to leave, so the corner outlives her). She hides herself when the owner turned her
 * off, except inside her corner.
 */
@Composable
fun WhityBuddy(
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    idle: WhityReaction? = null,
    cue: WhityCue? = null,
    corner: Boolean = true,
    description: String? = null,
    chatty: Boolean = false,
    next: String? = null,
    onHold: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val pet by remember(context) { WhityPetStore.state(context) }.collectAsState()
    // Her mood drifts, the treat jar refills and the streak moves on with time: bring them up to now.
    LaunchedEffect(Unit) { WhityPetStore.update(context) }
    if (corner && !pet.shown) return

    val haptics = LocalHapticFeedback.current
    var playing by remember { mutableStateOf<WhityReaction?>(null) }
    var playKey by remember { mutableIntStateOf(0) }
    var showCorner by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf<WhityLine?>(null) }
    val taps = remember { ArrayDeque<Long>() }
    val upcoming by rememberUpdatedState(next)
    fun play(reaction: WhityReaction) {
        playing = reaction
        playKey++
    }

    // Only cues that arrive while she's here: coming back to a screen doesn't replay an old one.
    var seenCue by remember { mutableStateOf(cue?.id) }
    LaunchedEffect(cue) {
        if (cue != null && cue.id != seenCue) {
            seenCue = cue.id
            play(cue.reaction)
        }
    }
    if (chatty) {
        LaunchedEffect(Unit) {
            delay(CHAT_AFTER_MS)
            if (bubble == null) WhityPetStore.talk(context, upcoming)?.let { bubble = it }
        }
    }

    val tap by rememberUpdatedState {
        val now = SystemClock.uptimeMillis()
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > DIZZY_WINDOW_MS) taps.removeFirst()
        if (taps.size >= DIZZY_TAPS) {
            taps.clear()
            play(WhityReaction.Dizzy)
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
        } else {
            play(WhityReaction.Tap)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            val after = WhityPetStore.update(context) { WhityRules.pet(it, WhityPetState.TAP_LIFT) }
            if (bubble == null) {
                if (corner && onHold == null && after.hints < WhityPetState.HINT_TIMES) {
                    WhityPetStore.update(context) { it.copy(hints = it.hints + 1) }
                    bubble = WhityLine.HoldHint
                } else if (chatty) {
                    bubble = WhityPetStore.talk(context, upcoming, force = true)
                }
            }
        }
    }
    val stroke by rememberUpdatedState {
        play(WhityReaction.Pet)
        WhityPetStore.update(context) { WhityRules.pet(it) }
    }
    val openCorner by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        bubble = null
        val hold = onHold
        if (hold != null) hold() else showCorner = true
    }

    val today = LocalDate.now()
    val hour = LocalTime.now().hour
    val resting = idle ?: if (hour >= 22 || hour < 6 || pet.level == WhityMood.Sleepy) WhityReaction.Sleepy else WhityReaction.Watch
    val describe = stringResource(R.string.whity_pet_description, stringResource(moodLabel(pet.level))) +
        description?.let { ". $it" }.orEmpty()
    val petLabel = stringResource(R.string.whity_pet_action)
    val cornerLabel = stringResource(R.string.whity_corner_open)

    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(WHITY_ASPECT)
            .pointerInput(Unit) {
                val startAfter = 36.dp.toPx()
                val purrEvery = 26.dp.toPx()
                var travelled = 0f
                var purred = 0f
                var lastPet = 0L
                detectDragGestures(
                    onDragStart = {
                        travelled = 0f
                        purred = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        travelled += amount.getDistance()
                        val now = SystemClock.uptimeMillis()
                        if (travelled >= startAfter && now - lastPet > PET_REPEAT_MS) {
                            lastPet = now
                            stroke()
                        }
                        if (travelled - purred >= purrEvery) {
                            purred = travelled
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    },
                )
            }
            .pointerInput(corner) {
                detectTapGestures(
                    onTap = { tap() },
                    onLongPress = if (corner) {
                        { openCorner() }
                    } else {
                        null
                    },
                )
            }
            .semantics {
                contentDescription = describe
                role = Role.Button
                onClick(label = petLabel) {
                    tap()
                    true
                }
                if (corner) {
                    onLongClick(label = cornerLabel) {
                        openCorner()
                        true
                    }
                }
            },
    ) {
        val now = playing
        AnimatedWhity(
            reaction = now ?: resting,
            loop = now == null,
            playKey = if (now == null) 0 else playKey,
            outfit = WhityRules.outfitOn(pet, today),
            treat = WhityTreat.on(today),
            onFinished = { playing = null },
            modifier = Modifier.fillMaxSize(),
        )
        bubble?.let { line ->
            val words = line.words()
            LaunchedEffect(line) {
                delay(bubbleMs(words))
                bubble = null
            }
            Popup(popupPositionProvider = remember { Above() }, properties = PopupProperties(focusable = false)) {
                WhityBubble(words, onClick = { bubble = null })
            }
        }
    }
    if (showCorner) WhityCorner(onDismiss = { showCorner = false })
}

/** How long a bubble with [words] stays up: long enough to read, never for long. */
internal fun bubbleMs(words: String): Long = (2_400L + words.length * 55L).coerceAtMost(7_000L)

/** Whity's speech bubble: a crayon card with a little tail pointing down at her. Tapping it closes it. */
@Composable
fun WhityBubble(words: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val crayon = MeyouwwTheme.crayon
    val outline = crayon.outline
    val fill = MaterialTheme.colorScheme.surface
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Whity's own voice: her handwriting on a white sticker with a hand-drawn edge.
        Text(
            words,
            style = if (crayon.classic) MaterialTheme.typography.labelLarge else MeyouwwTextStyles.handwritten.copy(fontSize = 19.sp, lineHeight = 22.sp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 230.dp)
                .crayonSurface(
                    fill = fill,
                    outline = outline,
                    seed = 77,
                    cornerRadius = 18.dp,
                    strokeWidth = if (crayon.classic) 2.dp else 1.5.dp,
                    wobble = 0.6.dp,
                    shadow = if (crayon.classic) crayon.hardShadow else crayon.shadow,
                    shadowOffsetX = if (crayon.classic) 2.dp else 0.dp,
                    shadowOffsetY = 3.dp,
                )
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        // The tail, tucked under the card's border.
        Box(
            Modifier
                .offset(y = (-2).dp)
                .size(width = 18.dp, height = 10.dp)
                .drawBehind {
                    val tail = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width / 2, size.height)
                        lineTo(size.width, 0f)
                    }
                    drawPath(tail, fill)
                    drawPath(tail, outline, style = Stroke(width = 1.5.dp.toPx()))
                    drawLine(fill, Offset(1.5.dp.toPx(), 0f), Offset(size.width - 1.5.dp.toPx(), 0f), strokeWidth = 2.5.dp.toPx())
                },
        )
    }
}

/** Places a popup just above its anchor (into Whity's empty headroom), kept on screen. */
private class Above : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top + anchorBounds.height / 5 - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@StringRes
internal fun moodLabel(mood: WhityMood): Int = when (mood) {
    WhityMood.Sleepy -> R.string.whity_mood_sleepy
    WhityMood.Cosy -> R.string.whity_mood_cosy
    WhityMood.Happy -> R.string.whity_mood_happy
    WhityMood.OverTheMoon -> R.string.whity_mood_over
}

@StringRes
internal fun moodTip(mood: WhityMood): Int = when (mood) {
    WhityMood.Sleepy -> R.string.whity_tip_sleepy
    WhityMood.Cosy -> R.string.whity_tip_cosy
    WhityMood.Happy -> R.string.whity_tip_happy
    WhityMood.OverTheMoon -> R.string.whity_tip_over
}
