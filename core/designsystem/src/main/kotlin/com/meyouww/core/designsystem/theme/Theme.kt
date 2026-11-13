package com.meyouww.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import com.meyouww.core.designsystem.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.unit.dp

private fun c(argb: Long) = Color(argb)

/**
 * Colours outside Material's scheme that every component needs. The pastel fills
 * ([yellow], [blue], [green], …) keep their hue in both themes and always take
 * [onFill] (cocoa) text.
 */
@Immutable
data class CrayonColors(
    /** The gentle rim around cards, chips, bubbles and avatars. Decoration, not a boundary. */
    val outline: Color,
    /** The soft lift under cards and sheets. */
    val shadow: Color,
    val textSoft: Color,
    val success: Color,
    val warning: Color,
    val link: Color,
    /** The focus ring and text cursor. */
    val focus: Color,
    /** Butter. */
    val yellow: Color,
    /** Cloud blue. */
    val blue: Color,
    /** Mint. */
    val green: Color,
    /** Melon. */
    val tangerine: Color,
    /** Apricot. */
    val peach: Color,
    /** Strawberry milk. */
    val bubblegum: Color,
    /** Lavender. */
    val lilac: Color,
    /** Berry: the main-action fill. Put [onRed] text on it. */
    val red: Color,
    /** The world behind every screen: the milk page (light) or the night page (dark). */
    val world: Color,
    /** The small offset shadow under round buttons and chips. */
    val hardShadow: Color,
    /** Text colour to use on top of any of the pastel fills above. */
    val onFill: Color,
    val isDark: Boolean,
    /** Text on [red]. */
    val onRed: Color = Color.White,
    /** The deeper berry a main button presses into; berry text on pastels. */
    val berryDeep: Color = c(Palette.BerryDeep),
    /** Outlines that must be seen: text fields, quiet buttons, unchecked switches (3:1). */
    val edge: Color = c(Palette.Edge),
    /** Hairlines between rows. */
    val line: Color = c(Palette.Line),
    /** The white sticker card (light) or night card (dark). */
    val card: Color = c(Palette.Sticker),
    /** The faint dots on the page. */
    val dot: Color = c(Palette.Dot),
    /** Background and text for errors. */
    val errorFill: Color = c(Palette.ErrorFill),
    val onError: Color = c(Palette.Error),
    /** The classic crayon look: ink outlines, comic shadows and a wobble on everything. */
    val classic: Boolean = false,
) {
    /** The deeper shade a pastel [fill] presses into, for soft buttons and chips. */
    fun pressShadowFor(fill: Color): Color {
        if (fill == red) return berryDeep
        val match = Palette.pastelShadows.firstOrNull { (light, _) -> c(light) == fill }
        return match?.let { c(it.second) } ?: hardShadow
    }
}

private val LightCrayon = CrayonColors(
    outline = c(Palette.SoftOutline),
    shadow = c(Palette.Cocoa).copy(alpha = 0.08f),
    textSoft = c(Palette.CocoaSoft),
    success = c(Palette.Success),
    warning = c(Palette.Warning),
    link = c(Palette.Berry),
    focus = c(Palette.Berry),
    yellow = c(Palette.Butter),
    blue = c(Palette.Cloud),
    green = c(Palette.Mint),
    tangerine = c(Palette.Melon),
    peach = c(Palette.Apricot),
    bubblegum = c(Palette.Strawberry),
    lilac = c(Palette.Lavender),
    red = c(Palette.Berry),
    world = c(Palette.Milk),
    hardShadow = c(Palette.Cocoa).copy(alpha = 0.12f),
    onFill = c(Palette.Cocoa),
    isDark = false,
)

private val DarkCrayon = LightCrayon.copy(
    outline = c(Palette.NightLine),
    shadow = Color.Black.copy(alpha = 0.30f),
    world = c(Palette.NightBackground),
    hardShadow = Color.Black.copy(alpha = 0.35f),
    textSoft = c(Palette.MoonSoft),
    success = c(Palette.NightSuccess),
    warning = c(Palette.NightWarning),
    link = c(Palette.NightRed),
    focus = c(Palette.NightRed),
    red = c(Palette.NightRed),
    onRed = c(Palette.Cocoa),
    berryDeep = c(Palette.NightBerryDeep),
    edge = c(Palette.Chalk),
    line = c(Palette.NightLine),
    card = c(Palette.NightSurface),
    dot = c(Palette.NightDot),
    errorFill = c(Palette.NightErrorFill),
    onError = c(Palette.NightError),
    isDark = true,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = c(Palette.Berry),
    onPrimary = c(Palette.White),
    primaryContainer = c(Palette.Strawberry),
    onPrimaryContainer = c(Palette.Cocoa),
    secondary = c(Palette.Butter),
    onSecondary = c(Palette.Cocoa),
    secondaryContainer = c(Palette.Strawberry),
    onSecondaryContainer = c(Palette.Cocoa),
    tertiary = c(Palette.Cloud),
    onTertiary = c(Palette.Cocoa),
    tertiaryContainer = c(Palette.Cloud),
    onTertiaryContainer = c(Palette.Cocoa),
    background = c(Palette.Milk),
    onBackground = c(Palette.Cocoa),
    surface = c(Palette.Sticker),
    onSurface = c(Palette.Cocoa),
    surfaceVariant = c(Palette.Strawberry),
    onSurfaceVariant = c(Palette.CocoaSoft),
    surfaceContainerLowest = c(Palette.Sticker),
    surfaceContainerLow = c(Palette.Sticker),
    surfaceContainer = c(Palette.Sticker),
    surfaceContainerHigh = c(Palette.Sticker),
    surfaceContainerHighest = c(Palette.Milk),
    outline = c(Palette.Edge),
    outlineVariant = c(Palette.Line),
    error = c(Palette.Error),
    onError = c(Palette.White),
    errorContainer = c(Palette.ErrorFill),
    onErrorContainer = c(Palette.Error),
    inverseSurface = c(Palette.Cocoa),
    inverseOnSurface = c(Palette.Milk),
    inversePrimary = c(Palette.NightRed),
    scrim = c(Palette.Cocoa),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = c(Palette.NightRed),
    onPrimary = c(Palette.Cocoa),
    primaryContainer = c(Palette.NightRed),
    onPrimaryContainer = c(Palette.Cocoa),
    secondary = c(Palette.Butter),
    onSecondary = c(Palette.Cocoa),
    secondaryContainer = c(Palette.Strawberry),
    onSecondaryContainer = c(Palette.Cocoa),
    tertiary = c(Palette.Cloud),
    onTertiary = c(Palette.Cocoa),
    tertiaryContainer = c(Palette.Cloud),
    onTertiaryContainer = c(Palette.Cocoa),
    background = c(Palette.NightBackground),
    onBackground = c(Palette.MoonText),
    surface = c(Palette.NightSurface),
    onSurface = c(Palette.MoonText),
    surfaceVariant = c(Palette.NightSurfaceHigh),
    onSurfaceVariant = c(Palette.MoonSoft),
    surfaceContainerLowest = c(Palette.NightBackground),
    surfaceContainerLow = c(Palette.NightSurface),
    surfaceContainer = c(Palette.NightSurface),
    surfaceContainerHigh = c(Palette.NightSurfaceHigh),
    surfaceContainerHighest = c(Palette.NightSurfaceHigh),
    outline = c(Palette.Chalk),
    outlineVariant = c(Palette.NightLine),
    error = c(Palette.NightError),
    onError = c(Palette.Cocoa),
    errorContainer = c(Palette.NightErrorFill),
    onErrorContainer = c(Palette.NightError),
    inverseSurface = c(Palette.MoonText),
    inverseOnSurface = c(Palette.Cocoa),
    inversePrimary = c(Palette.Berry),
    scrim = Color.Black,
)

// The classic "Crayon Kasukabe" look, exactly as up to v0.24.0.
private val ClassicLightCrayon = CrayonColors(
    outline = c(Palette.Classic.Ink),
    shadow = c(Palette.Classic.Ink).copy(alpha = 0.2f),
    textSoft = c(Palette.Classic.InkSoft),
    success = c(Palette.Classic.Success),
    warning = c(Palette.Classic.Warning),
    link = c(Palette.Classic.Link),
    focus = c(Palette.Classic.Focus),
    yellow = c(Palette.Classic.SunnyYellow),
    blue = c(Palette.Classic.SkyBlue),
    green = c(Palette.Classic.GrassGreen),
    tangerine = c(Palette.Classic.Tangerine),
    peach = c(Palette.Classic.Peach),
    bubblegum = c(Palette.Classic.Bubblegum),
    lilac = c(Palette.Classic.Lilac),
    red = c(Palette.Classic.CrayonRed),
    world = c(Palette.Classic.Sky),
    hardShadow = c(Palette.Classic.Ink),
    onFill = c(Palette.Classic.Ink),
    isDark = false,
    onRed = Color.White,
    berryDeep = c(Palette.Classic.Ink),
    edge = c(Palette.Classic.Ink),
    line = c(Palette.Classic.Ink).copy(alpha = 0.2f),
    card = c(Palette.Classic.Paper),
    dot = Color.Transparent,
    errorFill = c(Palette.Classic.Bubblegum),
    onError = c(Palette.Classic.Error),
    classic = true,
)

private val ClassicDarkCrayon = ClassicLightCrayon.copy(
    outline = c(Palette.Classic.Chalk),
    shadow = Color.Black.copy(alpha = 0.35f),
    world = c(Palette.Classic.NightBackground),
    hardShadow = Color.Black.copy(alpha = 0.55f),
    textSoft = c(Palette.Classic.MoonSoft),
    success = c(Palette.NightSuccess),
    warning = c(Palette.NightWarning),
    link = c(Palette.NightLink),
    focus = c(Palette.Classic.Lilac),
    red = c(Palette.Classic.NightRed),
    onRed = c(Palette.Classic.Ink),
    berryDeep = Color.Black.copy(alpha = 0.55f),
    edge = c(Palette.Classic.Chalk),
    line = c(Palette.Classic.Chalk).copy(alpha = 0.3f),
    card = c(Palette.Classic.NightSurface),
    errorFill = c(Palette.Classic.NightSurfaceHigh),
    onError = c(Palette.NightError),
    isDark = true,
)

private val ClassicLightScheme: ColorScheme = lightColorScheme(
    primary = c(Palette.Classic.InkRed),
    onPrimary = c(Palette.White),
    primaryContainer = c(Palette.Classic.Bubblegum),
    onPrimaryContainer = c(Palette.Classic.Ink),
    secondary = c(Palette.Classic.SunnyYellow),
    onSecondary = c(Palette.Classic.Ink),
    secondaryContainer = c(Palette.Classic.SunnyYellow),
    onSecondaryContainer = c(Palette.Classic.Ink),
    tertiary = c(Palette.Classic.SkyBlue),
    onTertiary = c(Palette.Classic.Ink),
    tertiaryContainer = c(Palette.Classic.SkyBlue),
    onTertiaryContainer = c(Palette.Classic.Ink),
    background = c(Palette.Classic.Sky),
    onBackground = c(Palette.Classic.Ink),
    surface = c(Palette.Classic.Paper),
    onSurface = c(Palette.Classic.Ink),
    surfaceVariant = c(Palette.Classic.Peach),
    onSurfaceVariant = c(Palette.Classic.InkSoft),
    surfaceContainerLowest = c(Palette.Classic.Surface),
    surfaceContainerLow = c(Palette.Classic.Paper),
    surfaceContainer = c(Palette.Classic.Paper),
    surfaceContainerHigh = c(Palette.Classic.Paper),
    surfaceContainerHighest = c(Palette.Classic.Peach),
    outline = c(Palette.Classic.Ink),
    outlineVariant = c(Palette.Classic.InkSoft),
    error = c(Palette.Classic.Error),
    onError = c(Palette.White),
    errorContainer = c(Palette.Classic.Bubblegum),
    onErrorContainer = c(Palette.Classic.Error),
    inverseSurface = c(Palette.Classic.Ink),
    inverseOnSurface = c(Palette.Classic.Paper),
    inversePrimary = c(Palette.Classic.NightRed),
    scrim = Color.Black,
)

private val ClassicDarkScheme: ColorScheme = darkColorScheme(
    primary = c(Palette.Classic.NightRed),
    onPrimary = c(Palette.Classic.Ink),
    primaryContainer = c(Palette.Classic.NightRed),
    onPrimaryContainer = c(Palette.Classic.Ink),
    secondary = c(Palette.Classic.SunnyYellow),
    onSecondary = c(Palette.Classic.Ink),
    secondaryContainer = c(Palette.Classic.SunnyYellow),
    onSecondaryContainer = c(Palette.Classic.Ink),
    tertiary = c(Palette.Classic.SkyBlue),
    onTertiary = c(Palette.Classic.Ink),
    tertiaryContainer = c(Palette.Classic.SkyBlue),
    onTertiaryContainer = c(Palette.Classic.Ink),
    background = c(Palette.Classic.NightBackground),
    onBackground = c(Palette.Classic.MoonText),
    surface = c(Palette.Classic.NightSurface),
    onSurface = c(Palette.Classic.MoonText),
    surfaceVariant = c(Palette.Classic.NightSurfaceHigh),
    onSurfaceVariant = c(Palette.Classic.MoonSoft),
    surfaceContainerLowest = c(Palette.Classic.NightBackground),
    surfaceContainerLow = c(Palette.Classic.NightSurface),
    surfaceContainer = c(Palette.Classic.NightSurface),
    surfaceContainerHigh = c(Palette.Classic.NightSurfaceHigh),
    surfaceContainerHighest = c(Palette.Classic.NightSurfaceHigh),
    outline = c(Palette.Classic.Chalk),
    outlineVariant = c(Palette.Classic.MoonSoft),
    error = c(Palette.NightError),
    onError = c(Palette.Classic.Ink),
    errorContainer = c(Palette.Classic.NightSurfaceHigh),
    onErrorContainer = c(Palette.NightError),
    inverseSurface = c(Palette.Classic.MoonText),
    inverseOnSurface = c(Palette.Classic.Ink),
    inversePrimary = c(Palette.Classic.CrayonRed),
    scrim = Color.Black,
)

/** The two looks a person can pick in Settings. */
enum class MeyouwwLook {
    /** Strawberry-milk pastels and white sticker cards (the default). */
    Pastel,

    /** The earlier crayon look: sky blue, cream paper and crayon ink. */
    Crayon,
}

/**
 * Remembers the chosen look on this phone (a tiny private preference, never sent anywhere).
 * Every screen's theme follows it, so a change shows at once.
 */
object LookStore {
    private const val FILE = "meyouww_look"
    private const val KEY = "look"
    private var flow: MutableStateFlow<MeyouwwLook>? = null

    fun state(context: Context): StateFlow<MeyouwwLook> = holder(context)

    fun set(context: Context, look: MeyouwwLook) {
        runCatching {
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, look.name).apply()
        }
        holder(context).value = look
    }

    @Synchronized
    private fun holder(context: Context): MutableStateFlow<MeyouwwLook> =
        flow ?: MutableStateFlow(read(context)).also { flow = it }

    private fun read(context: Context): MeyouwwLook = runCatching {
        val saved = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        MeyouwwLook.entries.firstOrNull { it.name == saved }
    }.getOrNull() ?: MeyouwwLook.Pastel
}

val LocalCrayonColors = staticCompositionLocalOf { LightCrayon }

/** Tileable waxy grain for fills in the crayon look. The pastel look provides none. */
val LocalCrayonGrain = staticCompositionLocalOf<ImageBitmap?> { null }

/** True when the system "remove animations" setting is on (animator scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

object MeyouwwTheme {
    val crayon: CrayonColors
        @Composable get() = LocalCrayonColors.current
}

/**
 * The app theme: "Pastel Whity" by default, or the earlier crayon look when the person picked
 * it in Settings ([look] null: follow that choice). Dynamic colour is intentionally off: the
 * palette is the brand. Light by default (the owner's choice); the night palettes keep dark
 * mode working for previews and a possible future switch.
 */
@Composable
fun MeyouwwTheme(
    darkTheme: Boolean = false,
    look: MeyouwwLook? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val chosen by remember(context) { LookStore.state(context) }.collectAsState()
    val crayonLook = (look ?: chosen) == MeyouwwLook.Crayon
    // The crayon look draws waxy grain over its fills; the pastel look is smooth.
    val grain = if (crayonLook) ImageBitmap.imageResource(R.drawable.crayon_grain) else null
    CompositionLocalProvider(
        LocalCrayonColors provides when {
            crayonLook && darkTheme -> ClassicDarkCrayon
            crayonLook -> ClassicLightCrayon
            darkTheme -> DarkCrayon
            else -> LightCrayon
        },
        LocalCrayonGrain provides grain,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = when {
                crayonLook && darkTheme -> ClassicDarkScheme
                crayonLook -> ClassicLightScheme
                darkTheme -> DarkScheme
                else -> LightScheme
            },
            typography = if (crayonLook) MeyouwwClassicTypography else MeyouwwTypography,
            shapes = if (crayonLook) {
                Shapes(
                    extraSmall = RoundedCornerShape(8.dp),
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(18.dp),
                    large = RoundedCornerShape(24.dp),
                    extraLarge = RoundedCornerShape(28.dp),
                )
            } else {
                Shapes(
                    extraSmall = RoundedCornerShape(10.dp),
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(20.dp),
                    large = RoundedCornerShape(24.dp),
                    extraLarge = RoundedCornerShape(32.dp),
                )
            },
            content = content,
        )
    }
}
