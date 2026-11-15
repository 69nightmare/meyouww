package com.meyouww.core.designsystem.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * "Living Sky", the third look: every screen sits in a small sky that follows the clock.
 * This file holds the parts that need no Compose (so they are unit-tested on the JVM): the
 * five phases of the day, where the sun and the moon are, the moon's real shape for the
 * date, the colours of each phase, and the person's choices for this look.
 */

/** The five phases of the day. [mid] is the minute shown when a person holds one phase. */
enum class SkyPhase(val mid: Int) {
    Dawn(375),
    Day(735),
    Gold(1035),
    Dusk(1150),
    Night(1320),
    ;

    /** Dusk and night are dark: dark cards with light text. */
    val dark: Boolean get() = this == Dusk || this == Night
}

/** How much moves in the Living Sky look. */
enum class SkyMotion {
    /** Clouds drift, hearts float, Whity breathes, looks around and wags. */
    Full,

    /** The scenery holds still; taps and screen changes still move. */
    Calm,

    /** Nothing moves (as with the phone's own "remove animations"). */
    Off,
}

/** Where the sun or moon is: [x] as a fraction of the screen width, [y] in screen widths from the top. */
class SkyOrb(val x: Float, val y: Float, val alpha: Float)

/** The moon on a night: [age] in days since the new moon, [lit] the lit fraction (0 to 1). */
class MoonShape(val age: Double, val lit: Double, val waxing: Boolean) {
    /** The lit side's outer edge runs round the right of the disc (waxing, as seen from the north). */
    val outerOnRight: Boolean get() = waxing

    /** The terminator (the line between lit and dark) bulges to the left. */
    val innerOnLeft: Boolean get() = (lit < 0.5) != waxing

    /** How wide the terminator ellipse is, as a fraction of the radius. */
    val innerWidth: Double get() = abs(1 - 2 * lit)
}

object SkyMath {
    /** Days from one new moon to the next. */
    const val SYNODIC = 29.530588853

    /** A known new moon: 6 January 2000, 18:14 UTC. */
    const val NEW_MOON_MS = 947_182_440_000L

    /** The phase at [minute] of the day (0 to 1439). */
    fun phaseAt(minute: Int): SkyPhase = when {
        minute < 300 -> SkyPhase.Night
        minute < 480 -> SkyPhase.Dawn
        minute < 960 -> SkyPhase.Day
        minute < 1110 -> SkyPhase.Gold
        minute < 1200 -> SkyPhase.Dusk
        else -> SkyPhase.Night
    }

    private fun ramp(m: Float, a: Float, b: Float): Float = when {
        m <= a -> 0f
        m >= b -> 1f
        else -> (m - a) / (b - a)
    }

    /**
     * How strongly the dawn, day, golden-hour, dusk and night skies show at [minute], painted
     * in that order over a night sky. One fades into the next, so the sky never jumps.
     */
    fun layers(minute: Float, out: FloatArray = FloatArray(5)): FloatArray {
        out[0] = ramp(minute, 270f, 330f) * (1 - ramp(minute, 640f, 641f))
        out[1] = ramp(minute, 435f, 525f) * (1 - ramp(minute, 1100f, 1101f))
        out[2] = ramp(minute, 915f, 1005f) * (1 - ramp(minute, 1215f, 1216f))
        out[3] = ramp(minute, 1080f, 1140f) * (1 - ramp(minute, 1330f, 1331f))
        out[4] = ramp(minute, 1170f, 1230f)
        return out
    }

    /** A point on the arc across the sky, [progress] 0 at one edge and 1 at the other. */
    private fun arc(progress: Float): SkyOrb {
        val q = progress.coerceIn(-0.06f, 1.06f)
        val x = (-20f + q * 430f) / 390f
        val y = (240f - sin(PI * q).toFloat() * 206f) / 390f
        val alpha = minOf((progress + 0.03f) / 0.05f, (1.03f - progress) / 0.05f).coerceIn(0f, 1f)
        return SkyOrb(x, y, alpha)
    }

    /** The sun rises on the left at 6:00, crosses overhead and sets on the right at 6:30 pm. */
    fun sun(minute: Float): SkyOrb = arc((minute - 360f) / 750f)

    /** The moon rises on the left as the sun sets (6:45 pm) and crosses until dawn (6:15 am). */
    fun moon(minute: Float): SkyOrb {
        val since = if (minute >= 1125f) minute - 1125f else minute + 315f
        val progress = since / 690f
        val orb = arc(if (progress > 1.55f) -0.06f else progress)
        return SkyOrb(orb.x, orb.y, arc(progress).alpha)
    }

    /** The moon's real shape at [epochMillis]: worked out from the date alone, no internet or location. */
    fun moonAt(epochMillis: Long): MoonShape {
        val days = (epochMillis - NEW_MOON_MS) / 86_400_000.0
        val age = ((days % SYNODIC) + SYNODIC) % SYNODIC
        val lit = (1 - cos(2 * PI * age / SYNODIC)) / 2
        return MoonShape(age, lit, age < SYNODIC / 2)
    }
}

/**
 * One phase's colours, as raw ARGB so the contrast rules can be checked on the JVM.
 * Text on the sky ([onSky], [soft], [link]) passes 4.5:1 on all three sky colours; text on
 * cards ([ink], [soft]) passes on [card]; [onAccent] passes on [accent].
 */
class SkyColors(
    val top: Long,
    val middle: Long,
    val bottom: Long,
    val onSky: Long,
    /** Secondary text, readable on the sky and on cards. */
    val soft: Long,
    /** Text links and text buttons, readable on the sky and on cards. */
    val link: Long,
    val card: Long,
    /** The edge under a card or button (the 3D depth). */
    val cardEdge: Long,
    val ink: Long,
    /** Control outlines: 3:1 on cards. */
    val edge: Long,
    val line: Long,
    val accent: Long,
    val accentDeep: Long,
    val onAccent: Long,
    /** The soft shadow, tinted by the light of the hour (ARGB with alpha). */
    val shadow: Long,
    /** Which way shadows fall, in dp: right in the morning, left in the evening. */
    val sunX: Float,
    val cloud: Long,
    val cloudBelly: Long,
    val heart: Long,
    val field: Long,
)

object SkyPalette {
    val dawn = SkyColors(
        top = 0xFFB9C8F2, middle = 0xFFF7C3D2, bottom = 0xFFFFE0BF,
        onSky = 0xFF3A2A32, soft = 0xFF5A4650, link = 0xFF7E1F45,
        card = 0xFFFFFBF8, cardEdge = 0xFFEFD3CE, ink = 0xFF3A2A32, edge = 0xFF9A8590, line = 0xFFF3E1DA,
        accent = 0xFFC93D66, accentDeep = 0xFF8F2346, onAccent = 0xFFFFFFFF,
        shadow = 0x428C465A, sunX = 6f,
        cloud = 0xFFFFFFFF, cloudBelly = 0xFFFFE3DE, heart = 0xFFF28BA8, field = 0xFFFFFFFF,
    )
    val day = SkyColors(
        top = 0xFF8FCBFA, middle = 0xFFC6E6FF, bottom = 0xFFEAF6FF,
        onSky = 0xFF2B2530, soft = 0xFF4A4458, link = 0xFF7A1E44,
        card = 0xFFFFFFFF, cardEdge = 0xFFCFDFF2, ink = 0xFF3A2A32, edge = 0xFF9A8590, line = 0xFFE8EEF6,
        accent = 0xFFC7386B, accentDeep = 0xFF962350, onAccent = 0xFFFFFFFF,
        shadow = 0x3D325A96, sunX = 0f,
        cloud = 0xFFFFFFFF, cloudBelly = 0xFFE3F1FF, heart = 0xFFF7A1BB, field = 0xFFFFFFFF,
    )
    val gold = SkyColors(
        top = 0xFF8FB4EE, middle = 0xFFFFCFA6, bottom = 0xFFFFB58C,
        onSky = 0xFF2B2530, soft = 0xFF45394A, link = 0xFF7A2219,
        card = 0xFFFFFAF2, cardEdge = 0xFFEFD5B6, ink = 0xFF3A2A32, edge = 0xFF9A8590, line = 0xFFF3E4D2,
        accent = 0xFFC2463C, accentDeep = 0xFF8E2A22, onAccent = 0xFFFFFFFF,
        shadow = 0x4796501E, sunX = -6f,
        cloud = 0xFFFFF6EA, cloudBelly = 0xFFFFD9BC, heart = 0xFFF08A8A, field = 0xFFFFFFFF,
    )
    val dusk = SkyColors(
        top = 0xFF3F3588, middle = 0xFF6E4FA8, bottom = 0xFF8A4F95,
        onSky = 0xFFFFFFFF, soft = 0xFFEEE6FF, link = 0xFFFFE3F6,
        card = 0xFF3A2E5E, cardEdge = 0xFF251D42, ink = 0xFFFFF3EC, edge = 0xFFA497BE, line = 0xFF4A3D72,
        accent = 0xFFEBA9F2, accentDeep = 0xFFA765BA, onAccent = 0xFF33263A,
        shadow = 0x5C281460, sunX = -3f,
        // Clouds stay darker than white text, so a title over a cloud still reads.
        cloud = 0xFF5E4C9E, cloudBelly = 0xFF4C3D8A, heart = 0xFFFFB3C9, field = 0xFF2E2550,
    )
    val night = SkyColors(
        top = 0xFF14103A, middle = 0xFF2A2063, bottom = 0xFF4B2F78,
        onSky = 0xFFFFF3EC, soft = 0xFFD5C8D6, link = 0xFFFF8FB1,
        card = 0xFF2B2547, cardEdge = 0xFF1A1533, ink = 0xFFFFF3EC, edge = 0xFF9C90B4, line = 0xFF3B3356,
        accent = 0xFFFF8FB1, accentDeep = 0xFFB85A7C, onAccent = 0xFF3A2A32,
        shadow = 0x80000000, sunX = 0f,
        cloud = 0xFF3B3468, cloudBelly = 0xFF2B2550, heart = 0xFFFF8FB1, field = 0xFF1F1A38,
    )

    fun of(phase: SkyPhase): SkyColors = when (phase) {
        SkyPhase.Dawn -> dawn
        SkyPhase.Day -> day
        SkyPhase.Gold -> gold
        SkyPhase.Dusk -> dusk
        SkyPhase.Night -> night
    }
}

/** The person's choices for the Living Sky look, kept on this phone only. */
data class SkySettings(
    /** Follow the clock (true) or keep [held] all day. */
    val followTime: Boolean = true,
    val held: SkyPhase = SkyPhase.Day,
    val motion: SkyMotion = SkyMotion.Full,
)

/** Remembers [SkySettings] (a tiny private preference, never sent anywhere). Changes show at once. */
object SkyStore {
    private const val FILE = "meyouww_look"
    private const val FOLLOW = "sky_follow"
    private const val HELD = "sky_held"
    private const val MOTION = "sky_motion"
    private var flow: MutableStateFlow<SkySettings>? = null

    fun state(context: Context): StateFlow<SkySettings> = holder(context)

    fun update(context: Context, change: (SkySettings) -> SkySettings) {
        val next = change(holder(context).value)
        runCatching {
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putBoolean(FOLLOW, next.followTime)
                .putString(HELD, next.held.name)
                .putString(MOTION, next.motion.name)
                .apply()
        }
        holder(context).value = next
    }

    @Synchronized
    private fun holder(context: Context): MutableStateFlow<SkySettings> =
        flow ?: MutableStateFlow(read(context)).also { flow = it }

    private fun read(context: Context): SkySettings = runCatching {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        SkySettings(
            followTime = prefs.getBoolean(FOLLOW, true),
            held = SkyPhase.entries.firstOrNull { it.name == prefs.getString(HELD, null) } ?: SkyPhase.Day,
            motion = SkyMotion.entries.firstOrNull { it.name == prefs.getString(MOTION, null) } ?: SkyMotion.Full,
        )
    }.getOrNull() ?: SkySettings()
}
