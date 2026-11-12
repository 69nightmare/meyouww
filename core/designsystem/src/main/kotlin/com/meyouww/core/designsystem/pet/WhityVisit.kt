package com.meyouww.core.designsystem.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/** How long after you arrive on a screen she pops up. */
private const val VISIT_AFTER_MS = 1_800L

/** She stays this much longer than her bubble. */
private const val VISIT_LINGER_MS = 1_200L

/**
 * Whity popping up from the bottom edge of a screen she doesn't live on, with something to
 * say, then ducking back down. [spot] names the screen; arriving on a new spot is a new visit
 * (null: she stays away). She comes only now and then (see [WhityTalk.mayTalk]), never while
 * the keyboard is up, and not at all when the owner turned her visits off or hid her. Tap her
 * bubble to send her away sooner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhityVisit(spot: Any?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val typing by rememberUpdatedState(WindowInsets.isImeVisible)
    var line by remember { mutableStateOf<WhityLine?>(null) }
    var visible by remember { mutableStateOf(false) }
    var corner by remember { mutableStateOf(false) }
    LaunchedEffect(spot) {
        visible = false
        if (spot == null) return@LaunchedEffect
        delay(VISIT_AFTER_MS)
        if (typing) return@LaunchedEffect
        line = WhityPetStore.talk(context) ?: return@LaunchedEffect
        visible = true
    }
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) fadeIn() else slideInVertically { it } + fadeIn(),
        exit = if (reduced) fadeOut() else slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        val said = line ?: return@AnimatedVisibility
        val words = said.words()
        LaunchedEffect(said) {
            delay(bubbleMs(words) + VISIT_LINGER_MS)
            visible = false
        }
        Column(horizontalAlignment = Alignment.Start) {
            WhityBubble(words, modifier = Modifier.padding(start = 6.dp), onClick = { visible = false })
            WhityBuddy(
                width = 108.dp,
                onHold = {
                    visible = false
                    corner = true
                },
            )
        }
    }
    if (corner) WhityCorner(onDismiss = { corner = false })
}
