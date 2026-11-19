package com.meyouww.core.designsystem.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * What the Living Sky look shows right now. [phase] changes a few times a day; [minute] and
 * [millis] change all the time, so read them only while drawing (never while composing), and
 * only the drawing is redone.
 */
@Stable
class SkyState internal constructor(
    val phase: SkyPhase,
    val colors: SkyColors,
    private val minuteState: State<Float>,
    private val clock: State<Long>,
    /** True when the scenery and Whity may move by themselves (Motion: Full, no battery saver). */
    val ambient: Boolean,
    val moon: MoonShape,
) {
    /** The minute of the day the sky shows (0 to 1440), with seconds as a fraction. */
    val minute: Float get() = minuteState.value

    /** A clock for looping animations, in milliseconds. It stands still when nothing should move. */
    val millis: Long get() = clock.value
}

/** The Living Sky's state, or null in the other looks. */
val LocalSky = staticCompositionLocalOf<SkyState?> { null }

private fun nowMinute(): Float {
    val t = LocalTime.now()
    return t.hour * 60f + t.minute + t.second / 60f
}

private fun lerpArgb(a: Long, b: Long, f: Float): Long {
    fun ch(v: Long, shift: Int) = ((v shr shift) and 0xFF).toFloat()
    fun mix(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * f).toLong().coerceIn(0, 255) shl shift
    return mix(24) or mix(16) or mix(8) or mix(0)
}

private fun SkyColors.towards(to: SkyColors, f: Float): SkyColors {
    if (f >= 1f) return to
    fun m(a: Long, b: Long) = lerpArgb(a, b, f)
    return SkyColors(
        top = m(top, to.top), middle = m(middle, to.middle), bottom = m(bottom, to.bottom),
        onSky = m(onSky, to.onSky), soft = m(soft, to.soft), link = m(link, to.link),
        card = m(card, to.card), cardEdge = m(cardEdge, to.cardEdge), ink = m(ink, to.ink),
        edge = m(edge, to.edge), line = m(line, to.line),
        accent = m(accent, to.accent), accentDeep = m(accentDeep, to.accentDeep), onAccent = m(onAccent, to.onAccent),
        shadow = m(shadow, to.shadow), sunX = sunX + (to.sunX - sunX) * f,
        cloud = m(cloud, to.cloud), cloudBelly = m(cloudBelly, to.cloudBelly), heart = m(heart, to.heart), field = m(field, to.field),
        cardSoft = m(cardSoft, to.cardSoft), cardLink = m(cardLink, to.cardLink),
    )
}

/** The crayon colours of a phase: its card, ink and accent on the light or the night base. */
internal fun skyCrayon(c: SkyColors, dark: Boolean): CrayonColors = (if (dark) DarkCrayon else LightCrayon).copy(
    outline = Color(c.line),
    shadow = Color(c.shadow),
    textSoft = Color(c.cardSoft),
    link = Color(c.cardLink),
    focus = Color(c.accent),
    red = Color(c.accent),
    onRed = Color(c.onAccent),
    berryDeep = Color(c.accentDeep),
    world = Color(c.middle),
    hardShadow = Color(c.cardEdge),
    edge = Color(c.edge),
    line = Color(c.line),
    card = Color(c.card),
    dot = Color.Transparent,
    isDark = dark,
    living = true,
    sunX = c.sunX,
)

/** Material's colours for a phase: text on the sky, cards, and dark-berry text buttons that read on both. */
internal fun skyScheme(c: SkyColors, dark: Boolean): ColorScheme = (if (dark) DarkScheme else LightScheme).copy(
    primary = Color(c.cardLink),
    onPrimary = if (dark) Color(c.onAccent) else Color.White,
    background = Color(c.middle),
    onBackground = Color(c.onSky),
    surface = Color(c.card),
    onSurface = Color(c.ink),
    onSurfaceVariant = Color(c.cardSoft),
    surfaceContainerLowest = Color(c.card),
    surfaceContainerLow = Color(c.card),
    surfaceContainer = Color(c.card),
    surfaceContainerHigh = Color(c.card),
    surfaceContainerHighest = Color(c.card),
    outline = Color(c.edge),
    outlineVariant = Color(c.line),
)

/**
 * Living Sky after dark: daytime colours for what sits on a light pastel card, so soft text,
 * links and quiet buttons on it stay readable. Elsewhere it changes nothing.
 */
@Composable
fun LightSkyScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCrayonColors provides skyCrayon(SkyPalette.day, false)) {
        MaterialTheme(
            colorScheme = skyScheme(SkyPalette.day, false),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/** Everything the theme needs for the Living Sky look this moment. */
internal class SkyLook(val state: SkyState, val crayon: CrayonColors, val scheme: ColorScheme, val reducedMotion: Boolean)

/**
 * Works out the Living Sky for now: the phase from the clock (or the one the person holds),
 * colours that blend over a second and a half when the phase changes, and a shared animation
 * clock that runs only when things may move.
 */
@Composable
internal fun rememberSkyLook(systemReducedMotion: Boolean, fixed: SkyPhase? = null): SkyLook {
    val context = LocalContext.current
    val saved by remember(context) { SkyStore.state(context) }.collectAsState()
    val settings = if (fixed != null) saved.copy(followTime = false, held = fixed) else saved
    val live = remember { mutableFloatStateOf(nowMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            live.floatValue = nowMinute()
        }
    }
    val held = remember(settings.held) { mutableFloatStateOf(settings.held.mid.toFloat()) }
    val minuteState: State<Float> = if (settings.followTime) live else held
    // Only the phase recomposes the theme; the minute is read while drawing.
    var phase by remember { mutableStateOf(SkyMath.phaseAt(minuteState.value.toInt())) }
    LaunchedEffect(settings.followTime, settings.held) {
        while (true) {
            phase = SkyMath.phaseAt(minuteState.value.toInt())
            delay(5_000)
        }
    }

    // Blend into the new phase's colours.
    val target = SkyPalette.of(phase)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val blend = remember { Animatable(1f) }
    val reducedMotion = systemReducedMotion || settings.motion == SkyMotion.Off
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        from = from.towards(to, blend.value)
        to = target
        blend.snapTo(0f)
        if (reducedMotion) blend.snapTo(1f) else blend.animateTo(1f, tween(1_500))
    }
    val colors = from.towards(to, blend.value)
    // Light or dark text and cards swap halfway through the blend.
    val dark = isDarkColors(if (blend.value < 0.5f) from else to)

    val powerSave = rememberPowerSave(context)
    val ambient = settings.motion == SkyMotion.Full && !reducedMotion && !powerSave
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(ambient) {
        if (!ambient) {
            clock.longValue = 0L
            return@LaunchedEffect
        }
        var start = -1L
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                if (start < 0) start = now
                // About 30 frames a second is smooth for slow loops and kinder to the battery.
                if (now - last >= 32) {
                    last = now
                    clock.longValue = now - start
                }
            }
        }
    }
    val moon = remember(phase) { SkyMath.moonAt(System.currentTimeMillis()) }
    val state = remember(phase, colors, ambient, minuteState, moon) { SkyState(phase, colors, minuteState, clock, ambient, moon) }
    return SkyLook(state, skyCrayon(colors, dark), skyScheme(colors, dark), reducedMotion)
}

private fun isDarkColors(c: SkyColors): Boolean = Palette.luminance(c.card) < 0.2

private fun isPowerSave(context: Context): Boolean =
    runCatching { (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode }.getOrDefault(false)

/** Battery saver on? Loops stop while it is. */
@Composable
private fun rememberPowerSave(context: Context): Boolean {
    var on by remember(context) { mutableStateOf(isPowerSave(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                on = isPowerSave(context)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return on
}
