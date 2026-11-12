package com.meyouww.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
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

val LocalCrayonColors = staticCompositionLocalOf { LightCrayon }

/** Tileable paper grain for fills. The pastel theme provides none. */
val LocalCrayonGrain = staticCompositionLocalOf<ImageBitmap?> { null }

/** True when the system "remove animations" setting is on (animator scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

object MeyouwwTheme {
    val crayon: CrayonColors
        @Composable get() = LocalCrayonColors.current
}

/**
 * The app theme: "Pastel Whity". Dynamic colour is intentionally off: the palette is the
 * brand. Light by default (the owner's choice); the night palette keeps dark mode working
 * for previews and a possible future switch.
 */
@Composable
fun MeyouwwTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalCrayonColors provides if (darkTheme) DarkCrayon else LightCrayon,
        // The pastel look is smooth: no waxy grain over fills.
        LocalCrayonGrain provides null,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = MeyouwwTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
