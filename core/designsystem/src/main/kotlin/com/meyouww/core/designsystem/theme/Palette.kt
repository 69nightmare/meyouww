package com.meyouww.core.designsystem.theme

/**
 * The "Pastel Whity" palette as raw ARGB values, kept free of Compose types so the
 * contrast rules can be unit-tested on the JVM (see PaletteContrastTest).
 *
 * A soft sticker journal: a strawberry-milk page with faint dots, white die-cut cards
 * like Whity's stickers, cocoa ink for text and six pastels that each mean something.
 * One berry pink marks the main action on a screen.
 *
 * The older crayon names (Sky, Paper, SunnyYellow, …) stay as aliases so every module
 * keeps compiling; they now hold the pastel values.
 */
object Palette {
    // Light theme: "Pastel Whity"
    const val Milk = 0xFFFFF7F2           // the page behind every screen
    const val Dot = 0xFFF2E1DA            // the faint journal dots on the page (decoration only)
    const val Sticker = 0xFFFFFFFF        // cards, sheets and inputs
    const val Cocoa = 0xFF3A2A32          // text and icons
    const val CocoaSoft = 0xFF6B5560      // secondary text
    const val Edge = 0xFF9A8590           // field and control outlines: 3:1 on cards and the page
    const val Line = 0xFFF0E2DC           // hairlines between rows (decoration only)
    const val SoftOutline = 0xFFE6D2D6    // the gentle rim around pastel cards and chips (decoration only)
    const val Berry = 0xFFC7386B          // the main action (white text 5:1), links and selected things
    const val BerryDeep = 0xFF962350      // the pressed shadow under Berry; berry text on pastels

    const val Strawberry = 0xFFFFD9E4     // love, your own chat bubbles, the selected tab
    const val Butter = 0xFFFFEDB0         // tips and warnings, best friends
    const val Mint = 0xFFCFF0DC           // success and done
    const val Cloud = 0xFFD3E8FF          // information and alarms
    const val Lavender = 0xFFE6DCFF       // tabs and Create
    const val Apricot = 0xFFFFDDCB        // friends
    const val Melon = 0xFFFFD8B0          // highlights

    // The deeper shade of each pastel: the soft shadow a pastel button presses into.
    const val StrawberryDeep = 0xFFF4AFC4
    const val ButterDeep = 0xFFF0CF6E
    const val MintDeep = 0xFF94D6AE
    const val CloudDeep = 0xFF9CC6F2
    const val LavenderDeep = 0xFFBDA9F2
    const val ApricotDeep = 0xFFF5B596
    const val MelonDeep = 0xFFF2B77A

    const val White = 0xFFFFFFFF

    // Older names, now pastel (kept so callers compile).
    const val Sky = Milk
    const val Paper = Sticker
    const val Surface = Sticker
    const val CrayonRed = Berry
    const val InkRed = Berry
    const val BrightRed = 0xFFF06A8F      // decoration only, never behind text
    const val SunnyYellow = Butter
    const val SkyBlue = Cloud
    const val GrassGreen = Mint
    const val Tangerine = Melon
    const val Peach = Apricot
    const val Bubblegum = Strawberry
    const val Lilac = Lavender
    const val Ink = Cocoa
    const val InkSoft = CocoaSoft

    // Semantic text and icon colours (at least 4.5:1 on every light fill above)
    const val Error = 0xFF8E1B1B
    const val ErrorFill = 0xFFFFE1DC
    const val Success = 0xFF145A32
    const val Warning = 0xFF663A00
    const val Link = 0xFF164A80
    const val Focus = BerryDeep

    // Dark theme: "Moonlit journal" (the same pastels on an indigo night page)
    const val NightBackground = 0xFF1E1A2B
    const val NightDot = 0xFF2C2640
    const val NightSurface = 0xFF2A2440
    const val NightSurfaceHigh = 0xFF352E50
    const val NightLine = 0xFF3B3356
    const val NightGlow = 0xFF4A2F5E       // a faint dusk band at the bottom (decoration)
    const val MoonText = 0xFFFFF3EC
    const val MoonSoft = 0xFFD5C8D6
    const val Chalk = 0xFF9C90B4           // control outlines in dark mode
    const val NightRed = 0xFFFF8FB1        // night berry: the main action (cocoa text on it)
    const val NightBerryDeep = 0xFFB85A7C
    const val NightError = 0xFFFFB4AB
    const val NightErrorFill = 0xFF4A2433
    const val NightSuccess = 0xFF8FDDB5
    const val NightWarning = 0xFFFFC97A
    const val NightLink = 0xFF9ECBFF

    /** Fills used behind Cocoa text in either theme. */
    val lightFills = listOf(Milk, Sticker, Strawberry, Butter, Mint, Cloud, Lavender, Apricot, Melon)

    /** The pastels and the deeper shade each one presses into. */
    val pastelShadows = listOf(
        Strawberry to StrawberryDeep,
        Butter to ButterDeep,
        Mint to MintDeep,
        Cloud to CloudDeep,
        Lavender to LavenderDeep,
        Apricot to ApricotDeep,
        Melon to MelonDeep,
    )

    /** Crayon box for drawing (not UI): at least 16 bright colours, unchanged by the pastel look. */
    val crayonBox = listOf(
        0xFFE23D2D, 0xFFFFA94D, 0xFFFFC72C, 0xFF8BD46A, 0xFF7CCBF5, 0xFF2F6FDE, 0xFF8E4FD6, 0xFFF8A5BF,
        0xFFFFD9BE, 0xFF8B5A3C, 0xFF231B1E, White, 0xFF9A9A9A, 0xFFF3D2B3, 0xFFD9A47F, 0xFFA8714F, 0xFF6B4430, 0xFFCDB8F5,
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
