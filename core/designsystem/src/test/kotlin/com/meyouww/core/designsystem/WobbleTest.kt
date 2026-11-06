package com.meyouww.core.designsystem

import com.google.common.truth.Truth.assertThat
import com.meyouww.core.designsystem.crayon.Wobble
import org.junit.Test

class WobbleTest {

    @Test
    fun `same seed gives the identical outline so it never shimmers`() {
        val a = Wobble.roundedRect(300f, 120f, 24f, 2f, seed = 42)
        val b = Wobble.roundedRect(300f, 120f, 24f, 2f, seed = 42)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `different seeds give different outlines`() {
        val a = Wobble.roundedRect(300f, 120f, 24f, 2f, seed = 1)
        val b = Wobble.roundedRect(300f, 120f, 24f, 2f, seed = 2)
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `outline stays inside the box when inset by stroke plus amplitude`() {
        val stroke = 6f
        val amp = 2.5f
        for (seed in 0 until 50) {
            val pts = Wobble.roundedRect(200f, 80f, 30f, amp, seed, inset = stroke / 2 + amp)
            assertThat(pts).isNotEmpty()
            pts.forEach {
                assertThat(it.x).isAtLeast(stroke / 2 - 0.01f)
                assertThat(it.x).isAtMost(200f - stroke / 2 + 0.01f)
                assertThat(it.y).isAtLeast(stroke / 2 - 0.01f)
                assertThat(it.y).isAtMost(80f - stroke / 2 + 0.01f)
            }
        }
    }

    @Test
    fun `zero amplitude traces the plain rounded rectangle`() {
        val pts = Wobble.roundedRect(100f, 100f, 0f, 0f, seed = 7, step = 10f)
        fun near(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.001f
        pts.forEach { p ->
            val onEdge = near(p.x, 0f) || near(p.x, 100f) || near(p.y, 0f) || near(p.y, 100f)
            assertThat(onEdge).isTrue()
        }
    }

    @Test
    fun `degenerate sizes produce no outline`() {
        assertThat(Wobble.roundedRect(0f, 10f, 4f, 1f, 1)).isEmpty()
        assertThat(Wobble.roundedRect(10f, 10f, 4f, 1f, 1, inset = 6f)).isEmpty()
    }

    @Test
    fun `huge radius is clamped to a pill`() {
        val pts = Wobble.roundedRect(200f, 40f, 999f, 0f, seed = 3)
        pts.forEach {
            assertThat(it.x).isAtLeast(-0.01f)
            assertThat(it.x).isAtMost(200.01f)
            assertThat(it.y).isAtLeast(-0.01f)
            assertThat(it.y).isAtMost(40.01f)
        }
    }
}
