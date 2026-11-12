// Made by scripts/whity_reactions.py from whity_peek.webp: run it again instead of editing this file.
package com.meyouww.core.designsystem.pet

/**
 * How Whity's whole face moves during each reaction, frame by frame, in step with its pictures
 * (the pictures keep her head still, so the outfit she wears moves with her), and the path her
 * treat takes.
 */
internal object WhityTracks {
    /** The point she squashes and turns around (the middle of her chin on the ledge), as fractions of the picture. */
    const val PIVOT_X = 0.5f
    const val PIVOT_Y = 0.9639f

    /** Numbers per frame in a motion track. */
    const val MOVE_STRIDE = 6

    /**
     * The tap reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val TAP = floatArrayOf(
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.08f, 0.9f, 0f, 0f, 0f,
        70f, 1.04f, 0.95f, 0f, 0f, 0f,
        70f, 0.96f, 1.06f, 0f, -0.0742f, 0f,
        70f, 0.98f, 1.03f, 0f, -0.1187f, 0f,
        70f, 1.0f, 1.0f, 0f, -0.089f, 0f,
        70f, 1.02f, 0.98f, 0f, -0.0297f, 0f,
        70f, 1.07f, 0.92f, 0f, 0f, 0f,
        70f, 0.98f, 1.02f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        500f, 1.0f, 1.0f, 0f, 0f, 0f,
    )

    /**
     * The pet reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val PET = floatArrayOf(
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, -0.0023f, -1.2475f,
        80f, 1.0f, 1.0f, 0f, -0.0045f, -2.4404f,
        80f, 1.0f, 1.0f, 0f, -0.0065f, -3.5267f,
        80f, 1.0f, 1.0f, 0f, -0.0083f, -4.4589f,
        80f, 1.0f, 1.0f, 0f, -0.0096f, -5.1962f,
        80f, 1.0f, 1.0f, 0f, -0.0106f, -5.7063f,
        80f, 1.0f, 1.0f, 0f, -0.0111f, -5.9671f,
        80f, 1.0f, 1.0f, 0f, -0.0111f, -5.9671f,
        80f, 1.0f, 1.0f, 0f, -0.0106f, -5.7063f,
        80f, 1.0f, 1.0f, 0f, -0.0096f, -5.1962f,
        80f, 1.0f, 1.0f, 0f, -0.0083f, -4.4589f,
        80f, 1.0f, 1.0f, 0f, -0.0065f, -3.5267f,
        80f, 1.0f, 1.0f, 0f, -0.0045f, -2.4404f,
        80f, 1.0f, 1.0f, 0f, -0.0023f, -1.2475f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, -0.0023f, 1.2475f,
        80f, 1.0f, 1.0f, 0f, -0.0045f, 2.4404f,
        80f, 1.0f, 1.0f, 0f, -0.0065f, 3.5267f,
        80f, 1.0f, 1.0f, 0f, -0.0083f, 4.4589f,
        80f, 1.0f, 1.0f, 0f, -0.0096f, 5.1962f,
        80f, 1.0f, 1.0f, 0f, -0.0106f, 5.7063f,
        80f, 1.0f, 1.0f, 0f, -0.0111f, 5.9671f,
        80f, 1.0f, 1.0f, 0f, -0.0111f, 5.9671f,
        80f, 1.0f, 1.0f, 0f, -0.0106f, 5.7063f,
        80f, 1.0f, 1.0f, 0f, -0.0096f, 5.1962f,
        80f, 1.0f, 1.0f, 0f, -0.0083f, 4.4589f,
        80f, 1.0f, 1.0f, 0f, -0.0065f, 3.5267f,
        80f, 1.0f, 1.0f, 0f, -0.0045f, 2.4404f,
        80f, 1.0f, 1.0f, 0f, -0.0023f, 1.2475f,
    )

    /**
     * The treat reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val TREAT = floatArrayOf(
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        60f, 1.0f, 1.0f, 0f, 0f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, 0.0148f, 0f,
        90f, 1.0f, 1.0f, 0f, -0.0148f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        80f, 1.0f, 1.0f, 0f, 0f, 0f,
        600f, 1.0f, 1.0f, 0f, 0f, 0f,
    )

    /**
     * The dizzy reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val DIZZY = floatArrayOf(
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0.0081f, 0f, -3.5f,
        70f, 1.0f, 1.0f, 0.014f, 0f, -6.0622f,
        70f, 1.0f, 1.0f, 0.0161f, 0f, -7.0f,
        70f, 1.0f, 1.0f, 0.014f, 0f, -6.0622f,
        70f, 1.0f, 1.0f, 0.0081f, 0f, -3.5f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, -0.0081f, 0f, 3.5f,
        70f, 1.0f, 1.0f, -0.014f, 0f, 6.0622f,
        70f, 1.0f, 1.0f, -0.0161f, 0f, 7.0f,
        70f, 1.0f, 1.0f, -0.014f, 0f, 6.0622f,
        70f, 1.0f, 1.0f, -0.0081f, 0f, 3.5f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, 0.0081f, 0f, -3.5f,
        70f, 1.0f, 1.0f, 0.014f, 0f, -6.0622f,
        70f, 1.0f, 1.0f, 0.0161f, 0f, -7.0f,
        70f, 1.0f, 1.0f, 0.014f, 0f, -6.0622f,
        70f, 1.0f, 1.0f, 0.0081f, 0f, -3.5f,
        70f, 1.0f, 1.0f, 0f, 0f, 0f,
        70f, 1.0f, 1.0f, -0.0081f, 0f, 3.5f,
        70f, 1.0f, 1.0f, -0.014f, 0f, 6.0622f,
        70f, 1.0f, 1.0f, -0.0161f, 0f, 7.0f,
        70f, 1.0f, 1.0f, -0.014f, 0f, 6.0622f,
        70f, 1.0f, 1.0f, -0.0081f, 0f, 3.5f,
    )

    /**
     * The sleepy reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val SLEEPY = floatArrayOf(
        90f, 1.0f, 1.0f, 0f, 0f, 0f,
        90f, 1.0077f, 1.0077f, 0f, 0f, 0f,
        90f, 1.0141f, 1.0141f, 0f, 0f, 0f,
        90f, 1.0185f, 1.0185f, 0f, 0f, 0f,
        90f, 1.02f, 1.02f, 0f, 0f, 0f,
        90f, 1.0185f, 1.0185f, 0f, 0f, 0f,
        90f, 1.0141f, 1.0141f, 0f, 0f, 0f,
        90f, 1.0077f, 1.0077f, 0f, 0f, 0f,
        90f, 1.0f, 1.0f, 0f, 0f, 0f,
        90f, 0.9923f, 0.9923f, 0f, 0f, 0f,
        90f, 0.9859f, 0.9859f, 0f, 0f, 0f,
        90f, 0.9815f, 0.9815f, 0f, 0f, 0f,
        90f, 0.98f, 0.98f, 0f, 0f, 0f,
        90f, 0.9815f, 0.9815f, 0f, 0f, 0f,
        90f, 0.9859f, 0.9859f, 0f, 0f, 0f,
        90f, 0.9923f, 0.9923f, 0f, 0f, 0f,
        90f, 1.0f, 1.0f, 0f, 0f, 0f,
        90f, 1.0077f, 1.0077f, 0f, 0f, 0f,
        90f, 1.0141f, 1.0141f, 0f, 0f, 0f,
        90f, 1.0185f, 1.0185f, 0f, 0f, 0f,
        90f, 1.02f, 1.02f, 0f, 0f, 0f,
        90f, 1.0185f, 1.0185f, 0f, 0f, 0f,
        90f, 1.0141f, 1.0141f, 0f, 0f, 0f,
        90f, 1.0077f, 1.0077f, 0f, 0f, 0f,
        90f, 1.0f, 1.0f, 0f, 0f, 0f,
        90f, 0.9923f, 0.9923f, 0f, 0f, 0f,
        90f, 0.9859f, 0.9859f, 0f, 0f, 0f,
        90f, 0.9815f, 0.9815f, 0f, 0f, 0f,
        90f, 0.98f, 0.98f, 0f, 0f, 0f,
        90f, 0.9815f, 0.9815f, 0f, 0f, 0f,
        90f, 0.9859f, 0.9859f, 0f, 0f, 0f,
        90f, 0.9923f, 0.9923f, 0f, 0f, 0f,
    )

    /**
     * The love reaction, per frame: how long it shows (ms), width and height scale, how far it moves
     * across and down (fractions of the picture's width and height) and its turn (degrees clockwise).
     */
    val LOVE = floatArrayOf(
        75f, 1.0f, 1.0f, 0f, 0f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, 0f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, 0f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, 0f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0071f, 0f,
        75f, 1.0f, 1.0f, 0f, -0.0044f, 0f,
    )

    /** Numbers per frame in the treat's path. */
    const val TREAT_STRIDE = 4

    /**
     * Per frame of the treat reaction: how long it shows (ms), then the treat's centre across and down
     * and its radius (fractions of the picture's width, height and width); radius 0 hides it.
     */
    val TREAT_PATH = floatArrayOf(
        60f, 0.98f, 0.95f, 0.0565f,
        60f, 0.9298f, 0.8546f, 0.0565f,
        60f, 0.8796f, 0.77f, 0.0565f,
        60f, 0.8294f, 0.7054f, 0.0565f,
        60f, 0.7792f, 0.668f, 0.0565f,
        60f, 0.729f, 0.6614f, 0.0565f,
        60f, 0.6788f, 0.6857f, 0.0565f,
        60f, 0.6286f, 0.7371f, 0.0565f,
        60f, 0.5784f, 0.8086f, 0.0565f,
        60f, 0.5282f, 0.8908f, 0.0565f,
        90f, 0.5282f, 0.8908f, 0.0565f,
        90f, 0.5282f, 0.8908f, 0.0502f,
        90f, 0.5282f, 0.8908f, 0.0439f,
        90f, 0.5282f, 0.8908f, 0.0377f,
        90f, 0.5282f, 0.8908f, 0.0314f,
        90f, 0.5282f, 0.8908f, 0.0251f,
        90f, 0.5282f, 0.8908f, 0.0188f,
        90f, 0.5282f, 0.8908f, 0.0126f,
        90f, 0.5282f, 0.8908f, 0.0063f,
        90f, 0f, 0f, 0f,
        90f, 0f, 0f, 0f,
        90f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        80f, 0f, 0f, 0f,
        600f, 0f, 0f, 0f,
    )

    /** The frame the treat reaction's still pose shows. */
    const val TREAT_STILL_FRAME = 12
}
