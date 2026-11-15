package com.meyouww.core.designsystem

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.meyouww.core.designsystem.theme.Palette
import com.meyouww.core.designsystem.theme.SkyMath
import com.meyouww.core.designsystem.theme.SkyPalette
import com.meyouww.core.designsystem.theme.SkyPhase
import org.junit.Test
import java.time.Instant

/** The Living Sky look: phases, the sun and moon's paths, the moon's real shape and readable colours. */
class SkyTest {

    @Test
    fun `phases follow the clock`() {
        assertThat(SkyMath.phaseAt(0)).isEqualTo(SkyPhase.Night)
        assertThat(SkyMath.phaseAt(5 * 60)).isEqualTo(SkyPhase.Dawn)
        assertThat(SkyMath.phaseAt(8 * 60)).isEqualTo(SkyPhase.Day)
        assertThat(SkyMath.phaseAt(16 * 60)).isEqualTo(SkyPhase.Gold)
        assertThat(SkyMath.phaseAt(18 * 60 + 30)).isEqualTo(SkyPhase.Dusk)
        assertThat(SkyMath.phaseAt(20 * 60)).isEqualTo(SkyPhase.Night)
        assertThat(SkyMath.phaseAt(1439)).isEqualTo(SkyPhase.Night)
        SkyPhase.entries.forEach { assertThat(SkyMath.phaseAt(it.mid)).isEqualTo(it) }
    }

    @Test
    fun `the sky blends one phase into the next`() {
        assertThat(SkyMath.layers(735f).toList()).containsExactly(0f, 1f, 0f, 0f, 0f).inOrder()
        assertThat(SkyMath.layers(120f).sum()).isEqualTo(0f) // the night sky underneath
        assertThat(SkyMath.layers(1290f)[4]).isEqualTo(1f)
        val dusk = SkyMath.layers(1110f)
        assertThat(dusk[3]).isGreaterThan(0f)
        assertThat(dusk[2]).isGreaterThan(0f)
    }

    @Test
    fun `the sun crosses by day and the moon by night`() {
        assertThat(SkyMath.sun(0f).alpha).isEqualTo(0f)
        assertThat(SkyMath.sun(735f).alpha).isEqualTo(1f)
        // Rises on the left, highest at midday, sets on the right.
        assertThat(SkyMath.sun(400f).x).isLessThan(SkyMath.sun(1000f).x)
        assertThat(SkyMath.sun(735f).y).isLessThan(SkyMath.sun(400f).y)
        assertThat(SkyMath.moon(0f).alpha).isEqualTo(1f)
        assertThat(SkyMath.moon(735f).alpha).isEqualTo(0f)
        assertThat(SkyMath.moon(1200f).x).isLessThan(SkyMath.moon(300f).x)
    }

    private fun moonOn(iso: String) = SkyMath.moonAt(Instant.parse(iso).toEpochMilli())

    @Test
    fun `the moon has its real shape`() {
        assertThat(moonOn("2024-04-08T18:21:00Z").lit).isLessThan(0.01) // new moon (the eclipse)
        assertThat(moonOn("2025-09-07T18:09:00Z").lit).isGreaterThan(0.99) // full moon
        // 14 Nov 2026, 8 pm in India: a waxing crescent about a quarter lit, lit on the right.
        val tonight = moonOn("2026-11-14T14:30:00Z")
        assertThat(tonight.waxing).isTrue()
        assertThat(tonight.lit).isWithin(0.06).of(0.23)
        assertThat(tonight.outerOnRight).isTrue()
        assertThat(tonight.innerOnLeft).isFalse()
        // A waning gibbous bulges the other way.
        val waning = moonOn("2025-09-10T00:00:00Z")
        assertThat(waning.waxing).isFalse()
        assertThat(waning.innerOnLeft).isFalse()
        assertThat(waning.outerOnRight).isFalse()
    }

    private fun hex(v: Long) = "#%06X".format(v and 0xFFFFFF)

    private fun assertAtLeast(what: String, fg: Long, bg: Long, min: Double) {
        val ratio = Palette.contrast(fg, bg)
        assertWithMessage("$what: ${hex(fg)} on ${hex(bg)} = %.2f, needs %.1f".format(ratio, min)).that(ratio).isAtLeast(min)
    }

    @Test
    fun `text is readable on the sky and on cards in every phase`() {
        SkyPhase.entries.forEach { phase ->
            val c = SkyPalette.of(phase)
            listOf(c.top, c.middle, c.bottom).forEach { sky ->
                assertAtLeast("$phase text on the sky", c.onSky, sky, 4.5)
                assertAtLeast("$phase soft text on the sky", c.soft, sky, 4.5)
                assertAtLeast("$phase links on the sky", c.link, sky, 4.5)
            }
            assertAtLeast("$phase text on cards", c.ink, c.card, 7.0)
            assertAtLeast("$phase soft text on cards", c.soft, c.card, 4.5)
            assertAtLeast("$phase links on cards", c.link, c.card, 4.5)
            assertAtLeast("$phase main button text", c.onAccent, c.accent, 4.5)
            assertAtLeast("$phase control outlines", c.edge, c.card, 3.0)
        }
    }

    @Test
    fun `clouds at dusk stay darker than the white text over them`() {
        assertAtLeast("dusk text over a cloud", SkyPalette.dusk.onSky, SkyPalette.dusk.cloud, 4.5)
        assertAtLeast("night text over a cloud", SkyPalette.night.onSky, SkyPalette.night.cloud, 4.5)
    }
}
