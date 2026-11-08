package com.meyouww.core.designsystem.theme

/**
 * The "Crayon Kasukabe" palette as raw ARGB values, kept free of Compose types so
 * the contrast rules can be unit-tested on the JVM (see PaletteContrastTest).
 *
 * Taken from the Kuchhu-chan birthday site: a sky-blue world with soft white clouds,
 * cream paper cards outlined in crayon ink, and loud crayon red, sunny yellow,
 * bubblegum pink and grass green.
 */
object Palette {
    // Light theme: "Crayon Kasukabe"
    const val Sky = 0xFF8FD0F5            // the world behind every screen
    const val Paper = 0xFFFFFCF2          // cards and sheets
    const val Surface = 0xFFFFFFFF        // inputs, ghost buttons
    const val CrayonRed = 0xFFD8392B      // big red buttons (white text 4.6:1)
    const val InkRed = 0xFF962018         // red text and Material accents, readable on sky and paper
    const val BrightRed = 0xFFE23D2D      // decoration only, never behind text
    const val SunnyYellow = 0xFFFFC72C    // secondary buttons, highlights
    const val SkyBlue = 0xFF7CCBF5        // tertiary fill, received chat bubbles
    const val GrassGreen = 0xFF8BD46A     // success backgrounds
    const val Tangerine = 0xFFFFA94D      // highlights
    const val Peach = 0xFFFFD9BE          // soft backgrounds
    const val Bubblegum = 0xFFF8A5BF      // love notes, sent chat bubbles, washi tape
    const val Lilac = 0xFFCDB8F5          // accents
    const val Ink = 0xFF231B1E            // text, outlines, icons, button shadows
    const val InkSoft = 0xFF45383D        // secondary text
    const val White = 0xFFFFFFFF

    // Semantic text and icon colours (at least 4.5:1 on every light fill above)
    const val Error = 0xFF8A1A14
    const val Success = 0xFF0F5029
    const val Warning = 0xFF663A00
    const val Link = 0xFF164A80
    const val Focus = 0xFF4A21A0

    // Dark theme: "Starry Kasukabe" (the site's night sky: indigo with stars)
    const val NightBackground = 0xFF241848
    const val NightSurface = 0xFF2E2160
    const val NightSurfaceHigh = 0xFF3B2C78
    const val NightGlow = 0xFF8A3F8F      // the sunset band at the bottom of the night sky (decoration)
    const val MoonText = 0xFFFFF4E6
    const val MoonSoft = 0xFFD9C7CE
    const val Chalk = 0xFFF6E7D8          // crayon outlines in dark mode
    const val NightRed = 0xFFFF7A70       // primary in dark mode (ink text on it)
    const val NightError = 0xFFFFB4AB
    const val NightSuccess = 0xFF8FDDB5
    const val NightWarning = 0xFFFFC97A
    const val NightLink = 0xFF9ECBFF

    /** Fills used behind Ink text in either theme. */
    val lightFills = listOf(Sky, Paper, Surface, SunnyYellow, SkyBlue, GrassGreen, Tangerine, Peach, Bubblegum, Lilac)

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
