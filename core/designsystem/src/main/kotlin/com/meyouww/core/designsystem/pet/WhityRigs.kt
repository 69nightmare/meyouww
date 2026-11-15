package com.meyouww.core.designsystem.pet

import com.meyouww.core.designsystem.component.WhitySticker

// Which parts of each Whity sticker move in the Living Sky look, from its design. Every number is
// a percentage of the sticker (x of its width, y of its height), marked by hand on enlarged, gridded
// copies. A part is a polygon that turns round its joint (x, y) by up to the last number in degrees;
// holes are the parts' far ends, cut out of the still picture so nothing shows twice as they move.

internal val WHITY_RIGS: Map<WhitySticker, WhityRig> = mapOf(
    WhitySticker.GreetGM to WhityRig(
        loop = BodyLoop.Stretch, emitX = 52f, emitY = 6f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Tail, floatArrayOf(7f, 0f, 29f, 0f, 29f, 19f, 21f, 22f, 19f, 29f, 9f, 31f, 7f, 22f), 14f, 29f, 9f),
            RigPart(PartKind.Ear, floatArrayOf(27f, 17f, 38f, 17f, 47f, 30f, 46f, 44f, 38f, 47f, 30f, 38f), 43f, 38f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(48f, 5f, 58f, 5f, 60f, 26f, 52f, 29f, 49f, 18f), 56f, 27f, 6f),
        ),
        holes = listOf(floatArrayOf(7f, 0f, 29f, 0f, 29f, 19f, 21f, 22f, 20.6f, 23.41f, 7f, 14.26f), floatArrayOf(27f, 17f, 38f, 17f, 42.44f, 23.41f, 33.69f, 42.15f, 30f, 38f), floatArrayOf(48f, 5f, 58f, 5f, 58.99f, 15.42f, 49.77f, 20.82f, 49f, 18f)),
    ),
    WhitySticker.PeekLedge to WhityRig(
        loop = BodyLoop.PeekUp, emitX = 50f, emitY = 14f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(10f, 6f, 18f, 6f, 38f, 27f, 30f, 40f, 22f, 56f, 14f, 52f), 27f, 42f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(88f, 9f, 80f, 9f, 64f, 27f, 70f, 40f, 78f, 56f, 88f, 50f), 73f, 42f, 5f),
            RigPart(PartKind.Tap, floatArrayOf(14f, 58f, 35f, 58f, 35f, 80f, 14f, 80f), 24f, 80f, 0f),
        ),
        holes = listOf(floatArrayOf(10f, 6f, 18f, 6f, 31.57f, 20.25f, 13.69f, 48.39f), floatArrayOf(88f, 9f, 80f, 9f, 69.37f, 20.96f, 87.04f, 50.58f, 88f, 50f)),
        eyes = listOf(RigEye(36.6f, 51f, 2.6f, 0xFFEBE0D7), RigEye(61f, 50.6f, 2.6f, 0xFFECE0DC)),
    ),
    WhitySticker.PartyStarGlasses to WhityRig(
        loop = BodyLoop.Nod, emitX = 48f, emitY = 8f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(5f, 10f, 16f, 10f, 36f, 26f, 34f, 40f, 24f, 52f, 8f, 40f), 29f, 42f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(84f, 6f, 74f, 6f, 66f, 16f, 74f, 26f, 76f, 42f, 84f, 36f), 76f, 40f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(8f, 49f, 25f, 49f, 25f, 71f, 8f, 71f), 16.5f, 60f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(77f, 40f, 95f, 40f, 95f, 60f, 77f, 60f), 86f, 50f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(71f, 57f, 84f, 57f, 84f, 71f, 71f, 71f), 77.5f, 64f, 0f),
        ),
        holes = listOf(floatArrayOf(5f, 10f, 16f, 10f, 35.74f, 25.79f, 15.79f, 45.85f, 8f, 40f), floatArrayOf(84f, 6f, 74f, 6f, 66f, 16f, 74f, 26f, 74.84f, 32.72f, 84f, 32.97f), floatArrayOf(8f, 49f, 25f, 49f, 25f, 71f, 8f, 71f), floatArrayOf(77f, 40f, 95f, 40f, 95f, 60f, 77f, 60f), floatArrayOf(71f, 57f, 84f, 57f, 84f, 71f, 71f, 71f)),
    ),
    WhitySticker.GrrOnGuard to WhityRig(
        loop = BodyLoop.Tilt, emitX = 52f, emitY = 3f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(33f, 2f, 42f, 2f, 52f, 22f, 46f, 31f, 38f, 30f), 46f, 29f, 6f),
            RigPart(PartKind.Ear, floatArrayOf(70f, 0f, 60f, 0f, 55f, 20f, 60f, 29f, 69f, 27f), 62f, 27f, 6f),
            RigPart(PartKind.Pulse, floatArrayOf(70.5f, 16f, 96f, 16f, 96f, 68f, 70.5f, 68f), 83f, 44f, 0f),
        ),
        holes = listOf(floatArrayOf(33f, 2f, 42f, 2f, 49.88f, 17.75f, 37.18f, 25.41f), floatArrayOf(70f, 0f, 60f, 0f, 55.5f, 18.01f, 69.27f, 19.73f), floatArrayOf(70.5f, 16f, 96f, 16f, 96f, 68f, 70.5f, 68f)),
        eyes = listOf(RigEye(53.2f, 34.6f, 1.5f, 0xFFCEC7C2), RigEye(64.8f, 33.3f, 1.5f, 0xFFFBF3E7)),
    ),
    WhitySticker.GreetWelcome to WhityRig(
        loop = BodyLoop.Wave, emitX = 48f, emitY = 4f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(14f, 3f, 24f, 3f, 40f, 18f, 36f, 30f, 26f, 34f, 17f, 24f), 32f, 29f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(83f, 2f, 72f, 2f, 58f, 17f, 62f, 30f, 72f, 33f, 81f, 22f), 66f, 29f, 5f),
            RigPart(PartKind.Paw, floatArrayOf(11f, 35f, 29f, 35f, 31f, 50f, 27f, 64f, 18f, 66f, 11f, 54f), 24f, 63f, 8f),
            RigPart(PartKind.Paw, floatArrayOf(72f, 35f, 90f, 35f, 90f, 54f, 84f, 64f, 75f, 64f, 71f, 50f), 78f, 63f, -8f),
        ),
        holes = listOf(floatArrayOf(14f, 3f, 24f, 3f, 37.57f, 15.72f, 21.42f, 28.92f, 17f, 24f), floatArrayOf(83f, 2f, 72f, 2f, 59f, 15.93f, 76.31f, 27.73f, 81f, 22f), floatArrayOf(11f, 35f, 29f, 35f, 31f, 50f, 30f, 53.51f, 13.85f, 58.89f, 11f, 54f), floatArrayOf(72f, 35f, 90f, 35f, 90f, 54f, 87.51f, 58.14f, 72.16f, 54.05f, 71f, 50f)),
        eyes = listOf(RigEye(38.4f, 29.2f, 2f, 0xFFE9E4E1), RigEye(58.4f, 26.4f, 2f, 0xFFEFE7E5)),
    ),
    WhitySticker.SayHi to WhityRig(
        loop = BodyLoop.Wave, emitX = 50f, emitY = 3f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(17f, 1f, 27f, 1f, 42f, 22f, 40f, 34f, 30f, 40f, 20f, 26f), 35f, 33f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(88f, 2f, 78f, 2f, 60f, 24f, 64f, 34f, 74f, 38f, 86f, 24f), 68f, 33f, 5f),
            RigPart(PartKind.Paw, floatArrayOf(10f, 33f, 30f, 33f, 31f, 50f, 30f, 70f, 13f, 70f, 10f, 52f), 21f, 72f, 8f),
        ),
        holes = listOf(floatArrayOf(17f, 1f, 27f, 1f, 39.79f, 18.91f, 24.55f, 32.37f, 20f, 26f), floatArrayOf(88f, 2f, 78f, 2f, 64.46f, 18.55f, 78f, 33.33f, 86f, 24f), floatArrayOf(10f, 33f, 30f, 33f, 31f, 50f, 30.33f, 63.39f, 11.99f, 63.96f, 10f, 52f)),
        eyes = listOf(RigEye(39.5f, 39f, 2f, 0xFFE3DCD9), RigEye(58.5f, 36.5f, 1.9f, 0xFFEFE7E5)),
    ),
    WhitySticker.MoodSmitten to WhityRig(
        loop = BodyLoop.Beat, emitX = 66f, emitY = 8f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(5f, 8f, 16f, 8f, 34f, 20f, 34f, 36f, 24f, 46f, 8f, 30f), 29f, 36f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(74f, 5f, 97f, 5f, 97f, 48f, 82f, 48f, 74f, 30f), 86f, 26f, 0f),
        ),
        holes = listOf(floatArrayOf(5f, 8f, 16f, 8f, 32.51f, 19.01f, 17.99f, 39.99f, 8f, 30f), floatArrayOf(74f, 5f, 97f, 5f, 97f, 48f, 82f, 48f, 74f, 30f)),
    ),
    WhitySticker.ToonHiIAmHere to WhityRig(
        loop = BodyLoop.Hop, emitX = 34f, emitY = 7f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(7f, 13f, 17f, 13f, 29f, 28f, 27f, 40f, 19f, 44f, 9f, 32f), 25f, 38f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(52f, 6f, 44f, 6f, 38f, 24f, 42f, 34f, 50f, 34f, 52f, 20f), 45f, 33f, 6f),
        ),
        holes = listOf(floatArrayOf(7f, 13f, 17f, 13f, 25.63f, 23.78f, 15.62f, 39.95f, 9f, 32f), floatArrayOf(52f, 6f, 44f, 6f, 38.55f, 22.36f, 51.23f, 25.42f, 52f, 20f)),
    ),
    WhitySticker.MoodWave to WhityRig(
        loop = BodyLoop.Wave, emitX = 60f, emitY = 3f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(29f, 2f, 39f, 2f, 52f, 20f, 50f, 32f, 40f, 36f, 31f, 24f), 45f, 32f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(94f, 0f, 82f, 0f, 68f, 20f, 72f, 32f, 82f, 36f, 93f, 22f), 76f, 32f, 5f),
            RigPart(PartKind.Paw, floatArrayOf(16f, 34f, 37f, 34f, 38f, 52f, 38f, 72f, 24f, 72f, 17f, 56f), 31f, 74f, 8f),
        ),
        holes = listOf(floatArrayOf(29f, 2f, 39f, 2f, 52f, 20f, 51.55f, 22.67f, 35.04f, 29.39f, 31f, 24f), floatArrayOf(94f, 0f, 82f, 0f, 68f, 20f, 68.62f, 21.87f, 86.65f, 30.08f, 93f, 22f), floatArrayOf(16f, 34f, 37f, 34f, 38f, 52f, 38f, 66.79f, 22.64f, 68.9f, 17f, 56f)),
        eyes = listOf(RigEye(46f, 31.8f, 2.3f, 0xFFDED4CB), RigEye(67.6f, 30f, 2.2f, 0xFFF4EFED)),
    ),
    WhitySticker.MoodNapping to WhityRig(
        loop = BodyLoop.Snooze, emitX = 80f, emitY = 6f, sleepy = true,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(48f, 17f, 60f, 17f, 60f, 40f, 54f, 46f, 48f, 38f), 54f, 44f, 4f),
            RigPart(PartKind.Bob, floatArrayOf(68f, 6f, 88f, 6f, 88f, 34f, 68f, 34f), 78f, 20f, 0f),
        ),
        holes = listOf(floatArrayOf(48f, 17f, 60f, 17f, 60f, 34.67f, 48f, 34.67f), floatArrayOf(68f, 6f, 88f, 6f, 88f, 34f, 68f, 34f)),
    ),
    WhitySticker.Hi to WhityRig(
        loop = BodyLoop.Tilt, emitX = 52f, emitY = 5f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(2f, 24f, 14f, 24f, 26f, 36f, 24f, 56f, 14f, 68f, 4f, 52f), 23f, 48f, 4f),
            RigPart(PartKind.Ear, floatArrayOf(98f, 8f, 86f, 8f, 72f, 26f, 76f, 44f, 86f, 52f, 97f, 34f), 79f, 42f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(35f, 3f, 46f, 3f, 46f, 19f, 35f, 19f), 40.5f, 11f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(60f, 5f, 72f, 5f, 72f, 20f, 60f, 20f), 66f, 12.5f, 0f),
        ),
        holes = listOf(floatArrayOf(2f, 24f, 14f, 24f, 20.66f, 30.66f, 12.56f, 65.7f, 4f, 52f), floatArrayOf(98f, 8f, 86f, 8f, 73.39f, 24.21f, 90.77f, 44.2f, 97f, 34f), floatArrayOf(35f, 3f, 46f, 3f, 46f, 19f, 35f, 19f), floatArrayOf(60f, 5f, 72f, 5f, 72f, 20f, 60f, 20f)),
        eyes = listOf(RigEye(34.7f, 50.7f, 3f, 0xFFD79D6D), RigEye(63.7f, 44f, 2.8f, 0xFFF4D0A8)),
    ),
    WhitySticker.Teddy to WhityRig(
        loop = BodyLoop.Snooze, emitX = 30f, emitY = 6f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(41f, 2f, 57f, 2f, 60f, 24f, 52f, 31f, 43f, 26f), 51f, 29f, 4f),
            RigPart(PartKind.Pulse, floatArrayOf(7f, 18f, 18f, 18f, 18f, 31f, 7f, 31f), 12.5f, 24.5f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(17f, 9f, 29f, 9f, 29f, 22f, 17f, 22f), 23f, 15.5f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(77f, 12f, 92f, 12f, 92f, 27f, 77f, 27f), 84.5f, 19.5f, 0f),
        ),
        holes = listOf(floatArrayOf(41f, 2f, 57f, 2f, 59.82f, 22.66f, 42.77f, 23.24f), floatArrayOf(7f, 18f, 18f, 18f, 18f, 31f, 7f, 31f), floatArrayOf(17f, 9f, 29f, 9f, 29f, 22f, 17f, 22f), floatArrayOf(77f, 12f, 92f, 12f, 92f, 27f, 77f, 27f)),
    ),
    WhitySticker.SayLoveU to WhityRig(
        loop = BodyLoop.Beat, emitX = 40f, emitY = 6f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(5f, 9f, 16f, 9f, 29f, 22f, 28f, 34f, 20f, 42f, 8f, 30f), 25f, 34f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(72f, 5f, 62f, 5f, 50f, 20f, 54f, 31f, 64f, 33f, 71f, 20f), 58f, 30f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(58.5f, 28f, 77f, 28f, 77f, 52f, 58.5f, 52f), 67.5f, 40f, 0f),
        ),
        holes = listOf(floatArrayOf(5f, 9f, 16f, 9f, 26.53f, 19.53f, 14.83f, 36.83f, 8f, 30f), floatArrayOf(72f, 5f, 62f, 5f, 52.71f, 16.61f, 67.1f, 27.24f, 71f, 20f), floatArrayOf(58.5f, 28f, 77f, 28f, 77f, 52f, 58.5f, 52f)),
    ),
    WhitySticker.LoveHugMe to WhityRig(
        loop = BodyLoop.Hop, emitX = 53f, emitY = 2f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(25f, 0f, 35f, 0f, 46f, 18f, 44f, 30f, 36f, 34f, 28f, 22f), 40f, 30f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(82f, 2f, 72f, 2f, 60f, 18f, 62f, 30f, 72f, 34f, 80f, 22f), 66f, 30f, 5f),
            RigPart(PartKind.Paw, floatArrayOf(5f, 52f, 27f, 52f, 32f, 66f, 30f, 80f, 12f, 80f, 5f, 68f), 30f, 74f, 6f),
            RigPart(PartKind.Paw, floatArrayOf(74f, 58f, 97f, 58f, 97f, 86f, 80f, 86f, 72f, 76f), 74f, 76f, -6f),
        ),
        holes = listOf(floatArrayOf(25f, 0f, 35f, 0f, 45.64f, 17.41f, 31.09f, 26.64f, 28f, 22f), floatArrayOf(82f, 2f, 72f, 2f, 61.16f, 16.46f, 75.7f, 28.45f, 80f, 22f), floatArrayOf(5f, 52f, 27f, 52f, 29.11f, 57.9f, 21.16f, 80f, 12f, 80f, 5f, 68f), floatArrayOf(77.06f, 58f, 97f, 58f, 97f, 86f, 81.89f, 86f)),
        eyes = listOf(RigEye(44.4f, 34.7f, 2.2f, 0xFFDFD8D6), RigEye(60.2f, 32.3f, 2.1f, 0xFFE6E1DE)),
    ),
    WhitySticker.LoveBlowingAKiss to WhityRig(
        loop = BodyLoop.Nod, emitX = 72f, emitY = 28f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(5f, 8f, 16f, 8f, 34f, 26f, 33f, 40f, 24f, 50f, 8f, 34f), 29f, 40f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(73f, 5f, 63f, 5f, 52f, 22f, 56f, 32f, 66f, 34f, 72f, 22f), 60f, 31f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(79f, 36f, 95f, 36f, 95f, 54f, 79f, 54f), 87f, 45f, 0f),
        ),
        holes = listOf(floatArrayOf(5f, 8f, 16f, 8f, 32.45f, 24.45f, 17.59f, 43.59f, 8f, 34f), floatArrayOf(73f, 5f, 63f, 5f, 54.07f, 18.79f, 69.16f, 27.67f, 72f, 22f), floatArrayOf(79f, 36f, 95f, 36f, 95f, 54f, 79f, 54f)),
    ),
    WhitySticker.ToonLoveU to WhityRig(
        loop = BodyLoop.Beat, emitX = 50f, emitY = 8f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(17f, 4f, 27f, 4f, 39f, 18f, 38f, 30f, 30f, 38f, 20f, 24f), 35f, 30f, 5f),
            RigPart(PartKind.Pulse, floatArrayOf(38f, 29f, 52f, 29f, 52f, 46f, 38f, 46f), 45f, 37.5f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(58f, 21f, 70f, 21f, 70f, 36f, 58f, 36f), 64f, 28.5f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(10f, 32f, 27f, 32f, 27f, 52f, 10f, 52f), 18.5f, 42f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(81f, 20f, 97f, 20f, 97f, 42f, 81f, 42f), 89f, 31f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(85f, 48f, 96f, 48f, 96f, 66f, 85f, 66f), 90.5f, 57f, 0f),
        ),
        holes = listOf(floatArrayOf(17f, 4f, 27f, 4f, 36.7f, 15.32f, 25.26f, 31.37f, 20f, 24f), floatArrayOf(38f, 29f, 52f, 29f, 52f, 46f, 38f, 46f), floatArrayOf(58f, 21f, 70f, 21f, 70f, 36f, 58f, 36f), floatArrayOf(10f, 32f, 27f, 32f, 27f, 52f, 10f, 52f), floatArrayOf(81f, 20f, 97f, 20f, 97f, 42f, 81f, 42f), floatArrayOf(85f, 48f, 96f, 48f, 96f, 66f, 85f, 66f)),
    ),
    WhitySticker.LoveTeddyHug to WhityRig(
        loop = BodyLoop.Tilt, emitX = 40f, emitY = 8f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(5f, 13f, 16f, 13f, 31f, 24f, 30f, 36f, 22f, 44f, 8f, 34f), 27f, 36f, 5f),
            RigPart(PartKind.Ear, floatArrayOf(70f, 7f, 60f, 7f, 48f, 22f, 52f, 30f, 60f, 32f, 69f, 22f), 56f, 30f, 5f),
        ),
        holes = listOf(floatArrayOf(5f, 13f, 16f, 13f, 28.56f, 22.21f, 16.42f, 40.01f, 8f, 34f), floatArrayOf(70f, 7f, 60f, 7f, 50.66f, 18.68f, 64.63f, 26.85f, 69f, 22f)),
    ),
    WhitySticker.Love to WhityRig(
        loop = BodyLoop.Beat, emitX = 50f, emitY = 4f, sleepy = false,
        parts = listOf(
            RigPart(PartKind.Ear, floatArrayOf(0f, 6f, 12f, 6f, 30f, 24f, 28f, 44f, 18f, 62f, 2f, 36f), 25f, 42f, 4f),
            RigPart(PartKind.Ear, floatArrayOf(100f, 6f, 88f, 6f, 70f, 26f, 72f, 46f, 82f, 64f, 99f, 40f), 75f, 42f, 4f),
            RigPart(PartKind.Pulse, floatArrayOf(32f, 2f, 43f, 2f, 43f, 18f, 32f, 18f), 37.5f, 10f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(63f, 3f, 74f, 3f, 74f, 19f, 63f, 19f), 68.5f, 11f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(2f, 58f, 11f, 58f, 11f, 75f, 2f, 75f), 6.5f, 66.5f, 0f),
            RigPart(PartKind.Pulse, floatArrayOf(89f, 54f, 98f, 54f, 98f, 70f, 89f, 70f), 93.5f, 62f, 0f),
        ),
        holes = listOf(floatArrayOf(0f, 6f, 12f, 6f, 26.89f, 20.89f, 12.58f, 53.2f, 2f, 36f), floatArrayOf(100f, 6f, 88f, 6f, 74.27f, 21.25f, 87.47f, 56.28f, 99f, 40f), floatArrayOf(32f, 2f, 43f, 2f, 43f, 18f, 32f, 18f), floatArrayOf(63f, 3f, 74f, 3f, 74f, 19f, 63f, 19f), floatArrayOf(2f, 58f, 11f, 58f, 11f, 75f, 2f, 75f), floatArrayOf(89f, 54f, 98f, 54f, 98f, 70f, 89f, 70f)),
        eyes = listOf(RigEye(35.5f, 41f, 3.2f, 0xFFE0B896), RigEye(63f, 40.4f, 3f, 0xFFF7DDBD)),
    ),
)
