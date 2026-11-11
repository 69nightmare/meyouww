package com.meyouww.core.designsystem.pet

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Makes Whity react from outside (a treat given, a message arrived). A new [id] plays it again. */
data class WhityCue(val reaction: WhityReaction, val id: Long = System.nanoTime())

/** Taps this close together make her dizzy. */
private const val DIZZY_TAPS = 6
private const val DIZZY_WINDOW_MS = 2_500L

/** One stroke counts as one pet; stroking on counts again after the bliss has played. */
private const val PET_REPEAT_MS = 2_400L
private const val HINT_MS = 2_800L

/**
 * Whity, the app's pet, sitting on a screen. Tap her for a happy bounce, stroke her to pet her
 * (her eyes close, she blushes and the phone purrs), hold her for her corner (mood and treat
 * jar). Lots of quick taps make her dizzy. At night, or when her mood is low, she naps. When
 * animations are off she changes poses without moving.
 *
 * [width] sets her size; [idle] replaces what she does when nothing is happening (say, heart
 * eyes for new messages); a new [cue] makes her react; [corner] = false inside her corner
 * itself; [description] adds to what TalkBack says about her. She hides herself when the owner
 * turned her off, except inside her corner.
 */
@Composable
fun WhityBuddy(
    modifier: Modifier = Modifier,
    width: Dp = 88.dp,
    idle: WhityReaction? = null,
    cue: WhityCue? = null,
    corner: Boolean = true,
    description: String? = null,
) {
    val context = LocalContext.current
    val pet by remember(context) { WhityPetStore.state(context) }.collectAsState()
    // Her mood drifts and the treat jar refills with time: bring both up to now.
    LaunchedEffect(Unit) { WhityPetStore.update(context) }
    if (corner && !pet.shown) return

    val haptics = LocalHapticFeedback.current
    var playing by remember { mutableStateOf<WhityReaction?>(null) }
    var playKey by remember { mutableIntStateOf(0) }
    var showCorner by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    val taps = remember { ArrayDeque<Long>() }
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
    LaunchedEffect(hint) {
        if (hint) {
            delay(HINT_MS)
            hint = false
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
            if (corner && after.hints < WhityPetState.HINT_TIMES) {
                WhityPetStore.update(context) { it.copy(hints = it.hints + 1) }
                hint = true
            }
        }
    }
    val stroke by rememberUpdatedState {
        play(WhityReaction.Pet)
        WhityPetStore.update(context) { WhityRules.pet(it) }
    }
    val openCorner by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        showCorner = true
    }

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
            onFinished = { playing = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (hint) {
            Popup(popupPositionProvider = remember { Above() }, properties = PopupProperties(focusable = false)) {
                val crayon = MeyouwwTheme.crayon
                Text(
                    stringResource(R.string.whity_hint_hold),
                    style = MaterialTheme.typography.labelLarge,
                    color = crayon.onFill,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .crayonSurface(
                            fill = crayon.yellow,
                            outline = crayon.outline,
                            seed = 77,
                            cornerRadius = 14.dp,
                            strokeWidth = 2.dp,
                            shadow = crayon.hardShadow,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
    if (showCorner) WhityCorner(onDismiss = { showCorner = false })
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
