package com.meyouww.core.designsystem.theme

/**
 * The "Crayon Town" palette as raw ARGB values, kept free of Compose types so
 * the contrast rules can be unit-tested on the JVM (see PaletteContrastTest).
 *
 * Bold kid's-crayon colours on cream paper: crayon red, sunny yellow, sky blue
 * and grass green, outlined in near-black crayon ink.
 */
object Palette {
    // Light theme: "Crayon Town"
    const val Paper = 0xFFFFF6E0          // main background
    const val Surface = 0xFFFFFDF7        // cards
    const val CrayonRed = 0xFFCC2E26      // primary fill (white text 5.3:1)
    const val BrightRed = 0xFFE8382F      // decoration only, never behind text
    const val SunnyYellow = 0xFFFFD23F    // secondary fill
    const val SkyBlue = 0xFF7CCBF5        // tertiary fill, received chat bubbles
    const val GrassGreen = 0xFF8BD46A     // success backgrounds
    const val Tangerine = 0xFFFFA94D      // highlights
    const val Peach = 0xFFFFD9BE          // soft backgrounds
    const val Bubblegum = 0xFFFFB3C1      // love notes, sent chat bubbles
    const val Lilac = 0xFFCDB8F5          // accents
    const val Ink = 0xFF231B1E            // text, outlines, icons
    const val InkSoft = 0xFF4E4146        // secondary text
    const val White = 0xFFFFFFFF

    // Semantic text and icon colours (at least 4.5:1 on every light fill above)
    const val Error = 0xFF8A1A14
    const val Success = 0xFF0F5029
    const val Warning = 0xFF663A00
    const val Link = 0xFF164A80
    const val Focus = 0xFF4A21A0

    // Dark theme: "Night Crayon"
    const val NightBackground = 0xFF1A1517
    const val NightSurface = 0xFF251E21
    const val NightSurfaceHigh = 0xFF33292D
    const val MoonText = 0xFFFFF4E6
    const val MoonSoft = 0xFFD9C7CE
    const val Chalk = 0xFFF6E7D8          // crayon outlines in dark mode
    const val NightRed = 0xFFFF7A70       // primary in dark mode (ink text on it)
    const val NightError = 0xFFFFB4AB
    const val NightSuccess = 0xFF8FDDB5
    const val NightWarning = 0xFFFFC97A
    const val NightLink = 0xFF9ECBFF

    /** Fills used behind Ink text in either theme. */
    val lightFills = listOf(Paper, Surface, SunnyYellow, SkyBlue, GrassGreen, Tangerine, Peach, Bubblegum, Lilac)

    /** Crayon box for drawing (not UI): at least 16 colours. */
    val crayonBox = listOf(
        BrightRed, Tangerine, SunnyYellow, GrassGreen, SkyBlue, 0xFF2F6FDE, 0xFF8E4FD6, Bubblegum,
        Peach, 0xFF8B5A3C, Ink, White, 0xFF9A9A9A, 0xFFF3D2B3, 0xFFD9A47F, 0xFFA8714F, 0xFF6B4430, Lilac,
    )

    // WCAG 2.x relative luminance and contrast ratio.
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
