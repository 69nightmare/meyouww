package com.meyouww.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.imageResource
import com.meyouww.core.designsystem.R

private fun c(argb: Long) = Color(argb)

/** Colours outside Material's scheme that every crayon component needs. */
@Immutable
data class CrayonColors(
    val outline: Color,
    val shadow: Color,
    val textSoft: Color,
    val success: Color,
    val warning: Color,
    val link: Color,
    val focus: Color,
    val yellow: Color,
    val blue: Color,
    val green: Color,
    val tangerine: Color,
    val peach: Color,
    val bubblegum: Color,
    val lilac: Color,
    /** Text colour to use on top of any of the fills above (they keep their hue in both themes). */
    val onFill: Color,
    val isDark: Boolean,
)

private val LightCrayon = CrayonColors(
    outline = c(Palette.Ink),
    shadow = c(Palette.Ink).copy(alpha = 0.16f),
    textSoft = c(Palette.InkSoft),
    success = c(Palette.Success),
    warning = c(Palette.Warning),
    link = c(Palette.Link),
    focus = c(Palette.Focus),
    yellow = c(Palette.SunnyYellow),
    blue = c(Palette.SkyBlue),
    green = c(Palette.GrassGreen),
    tangerine = c(Palette.Tangerine),
    peach = c(Palette.Peach),
    bubblegum = c(Palette.Bubblegum),
    lilac = c(Palette.Lilac),
    onFill = c(Palette.Ink),
    isDark = false,
)

private val DarkCrayon = LightCrayon.copy(
    outline = c(Palette.Chalk),
    shadow = Color.Black.copy(alpha = 0.35f),
    textSoft = c(Palette.MoonSoft),
    success = c(Palette.NightSuccess),
    warning = c(Palette.NightWarning),
    link = c(Palette.NightLink),
    focus = c(Palette.Lilac),
    isDark = true,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = c(Palette.CrayonRed),
    onPrimary = c(Palette.White),
    primaryContainer = c(Palette.Bubblegum),
    onPrimaryContainer = c(Palette.Ink),
    secondary = c(Palette.SunnyYellow),
    onSecondary = c(Palette.Ink),
    secondaryContainer = c(Palette.SunnyYellow),
    onSecondaryContainer = c(Palette.Ink),
    tertiary = c(Palette.SkyBlue),
    onTertiary = c(Palette.Ink),
    tertiaryContainer = c(Palette.SkyBlue),
    onTertiaryContainer = c(Palette.Ink),
    background = c(Palette.Paper),
    onBackground = c(Palette.Ink),
    surface = c(Palette.Surface),
    onSurface = c(Palette.Ink),
    surfaceVariant = c(Palette.Peach),
    onSurfaceVariant = c(Palette.InkSoft),
    surfaceContainerLowest = c(Palette.Surface),
    surfaceContainerLow = c(Palette.Surface),
    surfaceContainer = c(Palette.Surface),
    surfaceContainerHigh = c(Palette.Paper),
    surfaceContainerHighest = c(Palette.Peach),
    outline = c(Palette.Ink),
    outlineVariant = c(Palette.InkSoft),
    error = c(Palette.Error),
    onError = c(Palette.White),
    errorContainer = c(Palette.Bubblegum),
    onErrorContainer = c(Palette.Error),
    inverseSurface = c(Palette.Ink),
    inverseOnSurface = c(Palette.Paper),
    inversePrimary = c(Palette.NightRed),
    scrim = Color.Black,
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = c(Palette.NightRed),
    onPrimary = c(Palette.Ink),
    primaryContainer = c(Palette.NightRed),
    onPrimaryContainer = c(Palette.Ink),
    secondary = c(Palette.SunnyYellow),
    onSecondary = c(Palette.Ink),
    secondaryContainer = c(Palette.SunnyYellow),
    onSecondaryContainer = c(Palette.Ink),
    tertiary = c(Palette.SkyBlue),
    onTertiary = c(Palette.Ink),
    tertiaryContainer = c(Palette.SkyBlue),
    onTertiaryContainer = c(Palette.Ink),
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
    outlineVariant = c(Palette.MoonSoft),
    error = c(Palette.NightError),
    onError = c(Palette.Ink),
    errorContainer = c(Palette.NightSurfaceHigh),
    onErrorContainer = c(Palette.NightError),
    inverseSurface = c(Palette.MoonText),
    inverseOnSurface = c(Palette.Ink),
    inversePrimary = c(Palette.CrayonRed),
    scrim = Color.Black,
)

val LocalCrayonColors = staticCompositionLocalOf { LightCrayon }

/** Tileable waxy crayon grain, drawn at low opacity over fills. */
val LocalCrayonGrain = staticCompositionLocalOf<ImageBitmap?> { null }

/** True when the system "remove animations" setting is on (animator scale 0). */
val LocalReducedMotion = staticCompositionLocalOf { false }

object MeyouwwTheme {
    val crayon: CrayonColors
        @Composable get() = LocalCrayonColors.current
}

/**
 * The app theme. Dynamic colour is intentionally off: the crayon palette is the
 * brand. Looks can later change through theme packs instead.
 */
@Composable
fun MeyouwwTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val grain = ImageBitmap.imageResource(R.drawable.crayon_grain)
    CompositionLocalProvider(
        LocalCrayonColors provides if (darkTheme) DarkCrayon else LightCrayon,
        LocalCrayonGrain provides grain,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = MeyouwwTypography,
            content = content,
        )
    }
}
