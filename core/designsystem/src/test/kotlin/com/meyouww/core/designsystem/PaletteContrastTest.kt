package com.meyouww.core.designsystem

import com.google.common.truth.Truth.assertWithMessage
import com.meyouww.core.designsystem.theme.Palette
import org.junit.Test

/** WCAG contrast rules from the design brief, verified by calculation. */
class PaletteContrastTest {

    private fun hex(v: Long) = "#%06X".format(v and 0xFFFFFF)

    private fun assertAtLeast(fg: Long, bg: Long, min: Double) {
        val ratio = Palette.contrast(fg, bg)
        assertWithMessage("${hex(fg)} on ${hex(bg)} = %.2f, needs %.1f".format(ratio, min))
            .that(ratio).isAtLeast(min)
    }

    @Test
    fun `ink text is readable on every light fill`() {
        Palette.lightFills.forEach { assertAtLeast(Palette.Ink, it, 7.0) }
    }

    @Test
    fun `secondary text passes on every light fill`() {
        Palette.lightFills.forEach { assertAtLeast(Palette.InkSoft, it, 4.5) }
    }

    @Test
    fun `semantic colours pass on every light fill`() {
        listOf(Palette.Error, Palette.Success, Palette.Warning, Palette.Link, Palette.Focus).forEach { c ->
            Palette.lightFills.forEach { assertAtLeast(c, it, 4.5) }
        }
    }

    @Test
    fun `primary button white text passes`() {
        assertAtLeast(Palette.White, Palette.CrayonRed, 4.5)
        assertAtLeast(Palette.CrayonRed, Palette.Paper, 4.5)
    }

    @Test
    fun `red text is readable on the sky and on paper`() {
        assertAtLeast(Palette.InkRed, Palette.Sky, 4.5)
        assertAtLeast(Palette.InkRed, Palette.Paper, 4.5)
        assertAtLeast(Palette.White, Palette.InkRed, 4.5)
    }

    @Test
    fun `crayon outline gives every fill 3 to 1 non-text contrast`() {
        Palette.lightFills.forEach { assertAtLeast(Palette.Ink, it, 3.0) }
        listOf(Palette.NightBackground, Palette.NightSurface, Palette.NightSurfaceHigh).forEach {
            assertAtLeast(Palette.Chalk, it, 3.0)
        }
    }

    @Test
    fun `dark theme text passes`() {
        val darks = listOf(Palette.NightBackground, Palette.NightSurface, Palette.NightSurfaceHigh)
        darks.forEach {
            assertAtLeast(Palette.MoonText, it, 7.0)
            assertAtLeast(Palette.MoonSoft, it, 4.5)
            assertAtLeast(Palette.NightRed, it, 4.5)
            listOf(Palette.NightError, Palette.NightSuccess, Palette.NightWarning, Palette.NightLink).forEach { c ->
                assertAtLeast(c, it, 4.5)
            }
        }
        assertAtLeast(Palette.Ink, Palette.NightRed, 4.5)
    }

    @Test
    fun `white text on light fills is never acceptable`() {
        // Documents the "never white on pastels" rule: these all fail.
        listOf(Palette.SunnyYellow, Palette.SkyBlue, Palette.GrassGreen, Palette.Peach).forEach {
            val ratio = Palette.contrast(Palette.White, it)
            assertWithMessage("white on ${hex(it)} unexpectedly passes").that(ratio).isLessThan(4.5)
        }
    }

    @Test
    fun `crayon box has at least 16 colours`() {
        assertWithMessage("crayon box").that(Palette.crayonBox.toSet().size).isAtLeast(16)
    }
}
