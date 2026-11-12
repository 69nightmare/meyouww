package com.meyouww.core.designsystem.pet

/**
 * One of [WhityTracks]' frame-by-frame tracks: each frame is how long it shows (ms) followed by
 * [stride] - 1 numbers. Reads the numbers at any moment, easing from one frame into the next.
 */
internal class WhityTrack(private val values: FloatArray, private val stride: Int) {
    val frames: Int = values.size / stride
    val totalMs: Float = (0 until frames).fold(0f) { sum, i -> sum + values[i * stride] }

    /** The numbers [ms] into the track, into [out]: going round again when [loop], else holding the last frame. */
    fun at(ms: Float, loop: Boolean, out: FloatArray) {
        var t = ms.coerceAtLeast(0f)
        if (loop) {
            t %= totalMs
        } else if (t >= totalMs) {
            frame(frames - 1, out)
            return
        }
        var start = 0f
        for (i in 0 until frames) {
            val length = values[i * stride]
            if (t < start + length) {
                val f = (t - start) / length
                val j = when {
                    i + 1 < frames -> i + 1
                    loop -> 0
                    else -> i
                }
                for (k in 1 until stride) {
                    val a = values[i * stride + k]
                    out[k - 1] = a + (values[j * stride + k] - a) * f
                }
                return
            }
            start += length
        }
        frame(frames - 1, out)
    }

    /** The numbers of frame [index], into [out]. */
    fun frame(index: Int, out: FloatArray) {
        for (k in 1 until stride) out[k - 1] = values[index * stride + k]
    }
}

/** How each reaction moves her whole face, or null when it doesn't. */
internal val WhityReaction.motion: WhityTrack?
    get() = when (this) {
        WhityReaction.Watch -> null
        WhityReaction.Tap -> Tracks.tap
        WhityReaction.Pet -> Tracks.pet
        WhityReaction.Treat -> Tracks.treat
        WhityReaction.Dizzy -> Tracks.dizzy
        WhityReaction.Sleepy -> Tracks.sleepy
        WhityReaction.Love -> Tracks.love
    }

internal object Tracks {
    val tap = WhityTrack(WhityTracks.TAP, WhityTracks.MOVE_STRIDE)
    val pet = WhityTrack(WhityTracks.PET, WhityTracks.MOVE_STRIDE)
    val treat = WhityTrack(WhityTracks.TREAT, WhityTracks.MOVE_STRIDE)
    val dizzy = WhityTrack(WhityTracks.DIZZY, WhityTracks.MOVE_STRIDE)
    val sleepy = WhityTrack(WhityTracks.SLEEPY, WhityTracks.MOVE_STRIDE)
    val love = WhityTrack(WhityTracks.LOVE, WhityTracks.MOVE_STRIDE)
    val treatPath = WhityTrack(WhityTracks.TREAT_PATH, WhityTracks.TREAT_STRIDE)
}
