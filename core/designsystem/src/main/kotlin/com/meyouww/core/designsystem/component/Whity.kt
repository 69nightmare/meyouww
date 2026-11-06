package com.meyouww.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.theme.LocalReducedMotion

/** The Whity sticker pack, provided by the app owner (see ASSETS.md). */
enum class WhitySticker(@param:DrawableRes val drawable: Int, @param:StringRes val label: Int) {
    Hi(R.drawable.whity_hi, R.string.whity_hi),
    Love(R.drawable.whity_love, R.string.whity_love),
    LoveYou(R.drawable.whity_love_you, R.string.whity_love_you),
    Sleepy(R.drawable.whity_sleepy, R.string.whity_sleepy),
    Bear(R.drawable.whity_bear, R.string.whity_bear),
    Pat(R.drawable.whity_pat, R.string.whity_pat),
    Confused(R.drawable.whity_confused, R.string.whity_confused),
    Cool(R.drawable.whity_cool, R.string.whity_cool),
    Peek(R.drawable.whity_peek, R.string.whity_peek),
    Frog(R.drawable.whity_frog, R.string.whity_frog),
    NotToday(R.drawable.whity_not_today, R.string.whity_not_today),
    Wink(R.drawable.whity_wink, R.string.whity_wink),
    Burrito(R.drawable.whity_burrito, R.string.whity_burrito),
    Cheeks(R.drawable.whity_cheeks, R.string.whity_cheeks),
    Flowers(R.drawable.whity_flowers, R.string.whity_flowers),
    Butterfly(R.drawable.whity_butterfly, R.string.whity_butterfly),
    Hoodie(R.drawable.whity_hoodie, R.string.whity_hoodie),
    Tired(R.drawable.whity_tired, R.string.whity_tired),
    Teddy(R.drawable.whity_teddy, R.string.whity_teddy),
}

/**
 * A Whity sticker. Pass [decorative] = true when the sticker only repeats what
 * nearby text says, so TalkBack does not read it twice.
 */
@Composable
fun Sticker(
    sticker: WhitySticker,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    decorative: Boolean = false,
) {
    Image(
        painter = painterResource(sticker.drawable),
        contentDescription = if (decorative) null else stringResource(sticker.label),
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

/** Whity bouncing while something loads. Holds still when animations are off. */
@Composable
fun WhityLoading(
    message: String,
    modifier: Modifier = Modifier,
    sticker: WhitySticker = WhitySticker.Hi,
) {
    val reducedMotion = LocalReducedMotion.current
    val bounce = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "whity-bounce")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bounce",
        )
        value
    }
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sticker(
            sticker = sticker,
            decorative = true,
            size = 110.dp,
            modifier = Modifier.graphicsLayer {
                translationY = -18.dp.toPx() * bounce
                // squash at the bottom of the bounce, stretch at the top
                scaleX = 1.06f - 0.08f * bounce
                scaleY = 0.94f + 0.08f * bounce
            },
        )
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
