package com.meyouww.feature.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.chat.ChatAttachment
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import kotlin.math.sin
import kotlinx.coroutines.delay
import java.io.File
import java.util.UUID

/** Material's microphone and pause shapes (the extended icon set isn't part of the app). */
internal object VoiceIcons {
    private fun icon(name: String, path: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black))
        .build()

    val Mic: ImageVector by lazy {
        icon(
            "Mic",
            "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 " +
                "-5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.48 6,-3.3 6,-6.72h-1.7z",
        )
    }
    val Pause: ImageVector by lazy { icon("Pause", "M6,19h4L10,5L6,5v14zM14,5v14h4L18,5h-4z") }
    val Play: ImageVector by lazy { icon("Play", "M8,5v14l11,-7z") }
}

/**
 * Records a voice message: AAC in an .m4a file, mono, 64 kbit/s (about 0.5 MB a minute),
 * into the app's own cache until it is sent or thrown away.
 */
internal class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val active: Boolean get() = recorder != null

    fun start(): Boolean {
        cancel()
        return try {
            val dir = File(context.cacheDir, "chat_prepare").apply { mkdirs() }
            val target = File(dir, "voice-" + UUID.randomUUID() + ".m4a")
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            recorder = r
            file = target
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            // A little past the limit: the screen sends it at the limit itself.
            r.setMaxDuration((MAX_MS + 3_000).toInt())
            r.setOutputFile(target.path)
            r.prepare()
            r.start()
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            cancel()
            false
        }
    }

    fun elapsedMs(): Long = if (active) SystemClock.elapsedRealtime() - startedAt else 0L

    /** 0..1, how loud it is right now (for the little level dot). */
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Stops and returns the recording and its length, or null if it was too short or failed. */
    fun finish(): Pair<File, Long>? {
        val r = recorder ?: return null
        val length = elapsedMs()
        val stopped = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        recorder = null
        val out = file
        file = null
        return if (stopped && out != null && out.length() > 0 && length >= MIN_MS) {
            out to length.coerceAtMost(MAX_MS)
        } else {
            out?.delete()
            null
        }
    }

    fun cancel() {
        recorder?.let { r ->
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }

    companion object {
        const val MIN_MS = 800L
        const val MAX_MS = 5 * 60_000L
    }
}

/** Plays one voice message at a time; every bubble shows whether it is the one playing. */
internal object VoicePlayer {
    var playingId by mutableStateOf<String?>(null)
        private set
    var paused by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    private var player: MediaPlayer? = null

    /** Plays [file] (or pauses / resumes it if it is the one playing). */
    fun toggle(id: String, file: File) {
        val current = player
        if (playingId == id && current != null) {
            if (current.isPlaying) {
                current.pause()
                paused = true
            } else {
                current.start()
                paused = false
            }
            return
        }
        stop()
        val next = MediaPlayer()
        try {
            next.setDataSource(file.path)
            next.prepare()
        } catch (e: Exception) {
            next.release()
            return
        }
        next.setOnCompletionListener { stop() }
        next.start()
        player = next
        playingId = id
        paused = false
        positionMs = 0
        durationMs = next.duration.toLong().coerceAtLeast(0)
    }

    /** Jumps to [fraction] (0..1) of the message that is playing. */
    fun seekTo(fraction: Float) {
        val p = player ?: return
        val to = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        runCatching { p.seekTo(to.toInt()) }
        positionMs = to
    }

    /** Called while a bubble is on screen and playing, to move its progress bar. */
    fun tick() {
        val p = player ?: return
        runCatching { if (p.isPlaying) positionMs = p.currentPosition.toLong() }
    }

    fun stop() {
        player?.let { p ->
            runCatching { p.stop() }
            runCatching { p.release() }
        }
        player = null
        playingId = null
        paused = false
        positionMs = 0
        durationMs = 0
    }
}

internal fun clock(ms: Long): String {
    val seconds = (ms / 1000).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/** A voice message in its bubble: play / pause, its wave (filling in as it plays), and how long it lasts. */
@Composable
internal fun VoiceNoteContent(messageId: String, attachment: ChatAttachment, ink: Color, maxBubble: Dp) {
    val file = attachment.file
    val mine = VoicePlayer.playingId == messageId
    val playing = mine && !VoicePlayer.paused
    LaunchedEffect(playing) {
        while (playing) {
            VoicePlayer.tick()
            delay(100)
        }
    }
    val total = (if (mine && VoicePlayer.durationMs > 0) VoicePlayer.durationMs else attachment.durationMs ?: 0L).coerceAtLeast(1L)
    val position = if (mine) VoicePlayer.positionMs else 0L
    val label = stringResource(if (playing) R.string.chat_voice_pause else R.string.chat_voice_play)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 4.dp).width(minOf(maxBubble - 48.dp, 240.dp)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.14f))
                .clickable(enabled = file != null, role = Role.Button, onClickLabel = label) { file?.let { VoicePlayer.toggle(messageId, it) } }
                .semantics { contentDescription = label },
        ) {
            Icon(if (playing) VoiceIcons.Pause else VoiceIcons.Play, contentDescription = null, tint = ink, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VoiceWave(
                wave = remember(messageId) { voiceWave(messageId) },
                progress = (position.toFloat() / total).coerceIn(0f, 1f),
                playing = playing,
                ink = ink,
                onSeek = if (mine) VoicePlayer::seekTo else null,
                modifier = Modifier.fillMaxWidth().height(30.dp),
            )
            Text(
                when {
                    attachment.unavailable -> stringResource(R.string.chat_file_unavailable)
                    file == null -> stringResource(R.string.chat_file_downloading)
                    mine -> clock(position) + " / " + clock(total)
                    else -> "🎤 " + clock(attachment.durationMs ?: 0L)
                },
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.8f),
            )
        }
    }
}

/** How many bars a voice message's wave has. */
private const val WAVE_BARS = 32

/**
 * A wave shape for a voice message. Its real loudness isn't stored with the message, so this is
 * a speech-like shape drawn from the message's [id]: the same message always looks the same.
 */
internal fun voiceWave(id: String, bars: Int = WAVE_BARS): FloatArray {
    val rnd = java.util.Random(id.hashCode().toLong())
    val phase1 = rnd.nextFloat() * 6.28f
    val phase2 = rnd.nextFloat() * 6.28f
    return FloatArray(bars) { i ->
        val t = i / (bars - 1f)
        // Syllables (two waves of bumps), a little roughness, and softer ends.
        val bumps = 0.5f + 0.3f * sin(t * 17f + phase1) + 0.2f * sin(t * 7f + phase2)
        val rough = rnd.nextFloat() * 0.35f
        val ends = minOf(1f, t * 6f, (1f - t) * 6f).coerceAtLeast(0.35f)
        ((bumps * 0.75f + rough) * ends).coerceIn(0.14f, 1f)
    }
}

/**
 * The wave in a voice message's bubble: the part already played in [ink], the rest faded.
 * While it plays, the bars at the play point bounce a little. Tap it to jump there ([onSeek],
 * only for the message that's playing).
 */
@Composable
private fun VoiceWave(wave: FloatArray, progress: Float, playing: Boolean, ink: Color, onSeek: ((Float) -> Unit)?, modifier: Modifier = Modifier) {
    val bouncing = playing && !LocalReducedMotion.current
    val clock = if (bouncing) {
        rememberInfiniteTransition(label = "voiceWave").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
            label = "bounce",
        )
    } else {
        null
    }
    val faded = ink.copy(alpha = 0.3f)
    Canvas(
        modifier = modifier
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(onSeek) { detectTapGestures { onSeek(it.x / size.width) } }
                } else {
                    Modifier
                },
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                if (onSeek != null) {
                    setProgress { target ->
                        onSeek(target)
                        true
                    }
                }
            },
    ) {
        val n = wave.size
        val step = size.width / n
        val barWidth = (step * 0.55f).coerceAtMost(4.dp.toPx())
        val playhead = progress * n
        val t = clock?.value ?: 0f
        for (i in 0 until n) {
            // Bars near the play point bounce, the nearest most.
            val near = (1f - kotlin.math.abs(i + 0.5f - playhead) / 3f).coerceIn(0f, 1f)
            val bounce = 1f + 0.35f * near * sin((t + i * 0.13f) * 6.2832f)
            val h = (size.height * wave[i] * bounce).coerceIn(barWidth, size.height)
            val x = step * (i + 0.5f)
            drawLine(
                color = if (i + 0.5f <= playhead) ink else faded,
                start = Offset(x, (size.height - h) / 2),
                end = Offset(x, (size.height + h) / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** How often the recording wave takes a new reading from the microphone. */
internal const val LIVE_WAVE_MS = 80L

/** The most readings the recording wave keeps (enough to fill the widest bar). */
internal const val LIVE_WAVE_KEEP = 72

/**
 * The live wave while recording: one bar per reading of how loud you are ([levels], 0..1, the
 * newest last), entering on the right and scrolling left, older bars fading.
 */
@Composable
internal fun LiveVoiceWave(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val step = 5.dp.toPx()
        val barWidth = 3.dp.toPx()
        val fits = (size.width / step).toInt().coerceAtLeast(1)
        val shown = levels.takeLast(fits)
        shown.forEachIndexed { k, level ->
            val fromRight = shown.size - 1 - k
            val x = size.width - barWidth / 2 - fromRight * step
            val h = (size.height * level).coerceIn(barWidth, size.height)
            val age = fromRight / fits.toFloat()
            drawLine(
                color = color.copy(alpha = color.alpha * (1f - 0.6f * age)),
                start = Offset(x, (size.height - h) / 2),
                end = Offset(x, (size.height + h) / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
