package com.meyouww.core.designsystem.pet

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Whity's animated reactions (made by scripts/whity_reactions.py from her peeking sticker),
 * each with a still pose for when animations are off.
 */
enum class WhityReaction(@param:RawRes val animation: Int, @param:DrawableRes val still: Int) {
    /** Idle: her eyes look left, right and up, with a blink. */
    Watch(R.raw.whity_react_watch, R.drawable.whity_react_watch_still),

    /** A tap: a happy squash-and-bounce with closed smiling eyes and hearts. */
    Tap(R.raw.whity_react_tap, R.drawable.whity_react_tap_still),

    /** Being stroked: eyes shut, a little sway, blushing, lots of hearts. */
    Pet(R.raw.whity_react_pet, R.drawable.whity_react_pet_still),

    /** A treat: a cookie flies in, nom nom, then heart eyes. */
    Treat(R.raw.whity_react_treat, R.drawable.whity_react_treat_still),

    /** Too many taps at once: spiral eyes and circling stars. */
    Dizzy(R.raw.whity_react_dizzy, R.drawable.whity_react_dizzy_still),

    /** Night, or a low mood: she dozes with floating Zzz. */
    Sleepy(R.raw.whity_react_sleepy, R.drawable.whity_react_sleepy_still),

    /** Something lovely (new messages): heart eyes and rising hearts. */
    Love(R.raw.whity_react_love, R.drawable.whity_react_love_still),
}

/** Width over height of every reaction's picture. */
const val WHITY_ASPECT = 330f / 247f

/** How long a still pose stands in for a one-off reaction when animations are off. */
private const val STILL_HOLD_MS = 1_400L

/**
 * Plays [reaction]: over and over when [loop], otherwise once and then [onFinished]. Changing
 * [playKey] replays it from the start. Shows the still pose while the animation loads, when
 * the phone's animations are off, and on Android 8 (which can't play animated WebP).
 */
@Composable
fun AnimatedWhity(
    reaction: WhityReaction,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    playKey: Int = 0,
    onFinished: () -> Unit = {},
) {
    if (!LocalReducedMotion.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PlayingWhity(reaction, modifier, loop, playKey, onFinished)
    } else {
        val finished by rememberUpdatedState(onFinished)
        Image(painterResource(reaction.still), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
        if (!loop) {
            LaunchedEffect(reaction, playKey) {
                delay(STILL_HOLD_MS)
                finished()
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.P)
@Composable
private fun PlayingWhity(reaction: WhityReaction, modifier: Modifier, loop: Boolean, playKey: Int, onFinished: () -> Unit) {
    val context = LocalContext.current
    val finished by rememberUpdatedState(onFinished)
    // The painter on screen; the old one stays until the next has loaded, so nothing flickers.
    var shown by remember { mutableStateOf<DrawablePainter?>(null) }
    LaunchedEffect(reaction, playKey, loop) {
        val drawable = withContext(Dispatchers.IO) { runCatching { decode(context, reaction.animation) }.getOrNull() }
        if (drawable !is AnimatedImageDrawable) {
            shown = null
            if (!loop) {
                delay(STILL_HOLD_MS)
                finished()
            }
            return@LaunchedEffect
        }
        drawable.repeatCount = if (loop) AnimatedImageDrawable.REPEAT_INFINITE else 0
        val ended = CompletableDeferred<Unit>()
        val callback = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(d: Drawable?) {
                ended.complete(Unit)
            }
        }
        drawable.registerAnimationCallback(callback)
        shown = DrawablePainter(drawable)
        drawable.start()
        try {
            if (!loop) {
                ended.await()
                finished()
            }
            awaitCancellation()
        } finally {
            drawable.unregisterAnimationCallback(callback)
            drawable.stop()
        }
    }
    val painter = shown
    if (painter != null) {
        Image(painter, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Image(painterResource(reaction.still), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.P)
private fun decode(context: Context, @RawRes id: Int): Drawable =
    ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, id))

/** Draws an Android drawable in Compose and redraws whenever it moves to its next frame. */
private class DrawablePainter(private val drawable: Drawable) : Painter() {
    private var frame by mutableIntStateOf(0)

    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            frame++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            MAIN.postAtTime(what, `when`)
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            MAIN.removeCallbacks(what)
        }
    }

    init {
        drawable.callback = callback
    }

    override val intrinsicSize: Size
        get() = Size(drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())

    override fun DrawScope.onDraw() {
        frame // read, so a new frame redraws
        drawIntoCanvas { canvas ->
            drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
            drawable.draw(canvas.nativeCanvas)
        }
    }

    private companion object {
        val MAIN = Handler(Looper.getMainLooper())
    }
}
