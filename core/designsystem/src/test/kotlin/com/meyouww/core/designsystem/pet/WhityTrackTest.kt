package com.meyouww.core.designsystem.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhityTrackTest {
    // Two frames: 100 ms at 0, then 300 ms at 10 (and a second number, 1 then 3).
    private val track = WhityTrack(floatArrayOf(100f, 0f, 1f, 300f, 10f, 3f), stride = 3)
    private val out = FloatArray(2)

    @Test
    fun `it eases from one frame into the next`() {
        track.at(50f, loop = false, out = out)
        assertThat(out[0]).isWithin(1e-4f).of(5f)
        assertThat(out[1]).isWithin(1e-4f).of(2f)
        assertThat(track.totalMs).isEqualTo(400f)
    }

    @Test
    fun `played once it holds the last frame`() {
        track.at(250f, loop = false, out = out)
        assertThat(out[0]).isEqualTo(10f)
        track.at(10_000f, loop = false, out = out)
        assertThat(out[0]).isEqualTo(10f)
        assertThat(out[1]).isEqualTo(3f)
    }

    @Test
    fun `looping it eases back to the start and goes round again`() {
        track.at(250f, loop = true, out = out)
        assertThat(out[0]).isWithin(1e-4f).of(5f)
        track.at(450f, loop = true, out = out)
        assertThat(out[0]).isWithin(1e-4f).of(5f)
    }

    @Test
    fun `the real tracks are whole and settle where they started`() {
        for (motion in listOf(Tracks.tap, Tracks.pet, Tracks.treat, Tracks.dizzy, Tracks.sleepy, Tracks.love, Tracks.treatPath)) {
            assertThat(motion.frames).isGreaterThan(1)
            assertThat(motion.totalMs).isGreaterThan(0f)
        }
        val pose = FloatArray(5)
        // A tap ends back in her ordinary pose.
        Tracks.tap.at(Tracks.tap.totalMs, loop = false, out = pose)
        assertThat(pose.toList()).containsExactly(1f, 1f, 0f, 0f, 0f).inOrder()
        // The treat starts off to the side and is gone by the end.
        val spot = FloatArray(3)
        Tracks.treatPath.frame(0, spot)
        assertThat(spot[2]).isGreaterThan(0f)
        Tracks.treatPath.at(Tracks.treatPath.totalMs, loop = false, out = spot)
        assertThat(spot[2]).isEqualTo(0f)
        Tracks.treatPath.frame(WhityTracks.TREAT_STILL_FRAME, spot)
        assertThat(spot[2]).isGreaterThan(0f)
    }
}
