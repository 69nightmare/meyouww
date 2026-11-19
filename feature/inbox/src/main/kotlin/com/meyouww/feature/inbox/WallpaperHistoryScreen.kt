package com.meyouww.feature.inbox

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySegmented
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.CardLevel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ReceivedWallpaper
import com.meyouww.core.model.SentWallpaper
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.model.parseInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class HistoryUiState(
    val received: List<ReceivedWallpaper>? = null,
    val sent: List<SentWallpaper>? = null,
)

@HiltViewModel
class WallpaperHistoryViewModel @Inject constructor(private val wallpapers: WallpaperRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val received = (wallpapers.received() as? Outcome.Success)?.value.orEmpty()
        val sent = (wallpapers.sent() as? Outcome.Success)?.value.orEmpty()
        _state.update { HistoryUiState(received, sent) }
    }
}

/** "What happened on my phone": every remote wallpaper, who, when and the outcome. */
@Composable
fun WallpaperHistoryScreen(onBack: () -> Unit, viewModel: WallpaperHistoryViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSent by rememberSaveable { mutableStateOf(false) }
    val crayon = MeyouwwTheme.crayon

    if (crayon.living) {
        SkyHistory(state, showSent, onShowSent = { showSent = it }, onBack = onBack)
        return
    }
    CrayonScreen(title = stringResource(R.string.history_title), onBack = onBack) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(10.dp), seed = 701) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CrayonChip(stringResource(R.string.history_received), fill = if (!showSent) crayon.yellow else MaterialTheme.colorScheme.surface, onClick = { showSent = false }, modifier = Modifier.weight(1f))
                CrayonChip(stringResource(R.string.history_sent), fill = if (showSent) crayon.yellow else MaterialTheme.colorScheme.surface, onClick = { showSent = true }, modifier = Modifier.weight(1f))
            }
        }
        if (!showSent) {
            Text(stringResource(R.string.history_note), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
            val list = state.received
            if (list != null && list.isEmpty()) Text(stringResource(R.string.history_empty))
            list?.forEach { w ->
                HistoryRow(
                    seed = w.sender.id.hashCode(),
                    initial = w.sender.initial,
                    title = w.sender.name,
                    line = stringResource(
                        R.string.history_line_received,
                        dateText(w.createdAt),
                        targetText(WallpaperTarget.fromWire(w.target)),
                        receivedStatus(w.status) + if (w.mode == "auto") " · " + stringResource(R.string.history_auto) else "",
                    ),
                )
            }
        } else {
            val list = state.sent
            if (list != null && list.isEmpty()) Text(stringResource(R.string.history_empty))
            list?.forEach { w ->
                HistoryRow(
                    seed = w.recipient.id.hashCode(),
                    initial = w.recipient.initial,
                    title = w.recipient.name,
                    line = stringResource(
                        R.string.history_line_sent,
                        dateText(w.createdAt),
                        targetText(WallpaperTarget.fromWire(w.target)),
                        sentStatus(w.status),
                    ),
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(seed: Int, initial: String, title: String, line: String) {
    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = seed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(initial, seed)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(line, style = MaterialTheme.typography.bodySmall, color = MeyouwwTheme.crayon.textSoft)
            }
        }
    }
}

@Composable
private fun receivedStatus(status: String): String = stringResource(
    when (status) {
        "applied" -> R.string.status_applied
        "declined" -> R.string.status_declined
        "failed" -> R.string.status_failed
        "expired" -> R.string.status_expired
        "cancelled" -> R.string.status_cancelled
        else -> R.string.status_waiting
    },
)

@Composable
private fun sentStatus(status: String): String = stringResource(
    when (status) {
        "applied" -> R.string.status_applied
        "declined" -> R.string.status_declined_sent
        "failed" -> R.string.status_failed
        "expired" -> R.string.status_expired
        "cancelled" -> R.string.status_cancelled
        "scheduled" -> R.string.status_scheduled
        else -> R.string.status_sent
    },
)

private fun dateText(text: String): String =
    parseInstant(text)?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)).orEmpty()

/**
 * Living Sky, as on its board: "On my phone" / "I sent" as a segmented switch, then this week's
 * wallpapers and earlier ones as cards with a little picture tile, who and where, and pills for
 * how it went.
 */
@Composable
private fun SkyHistory(state: HistoryUiState, showSent: Boolean, onShowSent: (Boolean) -> Unit, onBack: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonScreen(title = stringResource(R.string.history_title), onBack = onBack) {
        SkySegmented(
            options = listOf(stringResource(R.string.history_received), stringResource(R.string.history_sent)),
            selected = if (showSent) 1 else 0,
            onSelect = { onShowSent(it == 1) },
            modifier = Modifier.fillMaxWidth(),
        )
        class Entry(val key: String, val seed: Int, val title: String, val line: String, val at: java.time.Instant?, val status: String, val auto: Boolean, val sent: Boolean)
        val entries = if (!showSent) {
            state.received?.map { w ->
                Entry(w.id, w.sender.id.hashCode(), stringResource(R.string.history_from_sky, w.sender.name), targetText(WallpaperTarget.fromWire(w.target)), parseInstant(w.createdAt), w.status, w.mode == "auto", false)
            }
        } else {
            state.sent?.map { w ->
                Entry(w.id, w.recipient.id.hashCode(), stringResource(R.string.history_to_sky, w.recipient.name), targetText(WallpaperTarget.fromWire(w.target)), parseInstant(w.createdAt), w.status, false, true)
            }
        }
        if (entries != null && entries.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSky)
        }
        val weekAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7))
        val (recent, older) = entries.orEmpty().partition { it.at?.isAfter(weekAgo) == true }
        listOf(R.string.history_this_week to recent, R.string.history_earlier to older).forEach { (heading, list) ->
            if (list.isEmpty()) return@forEach
            SkySectionHeading(stringResource(heading), inset = 2.dp)
            list.forEach { e ->
                SkyCard(modifier = Modifier.fillMaxWidth(), level = if (e.status == "applied" && !e.sent) CardLevel.Raised else CardLevel.Normal) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        WallTile(e.seed)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(e.title, style = SkyType.tt)
                            Text(listOf(e.line, e.at?.let { whenText(it) }.orEmpty()).filter { it.isNotEmpty() }.joinToString(" · "), style = SkyType.sb, color = crayon.textSoft)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val (word, tone) = statusPill(e.status, e.sent)
                                SkyPill(word, tone = tone)
                                if (e.auto) SkyPill(stringResource(R.string.history_auto_sky), tone = SkyTone.Blue)
                            }
                        }
                    }
                }
            }
        }
        if (!showSent) {
            Text(stringResource(R.string.history_note), style = SkyType.hp.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
        }
    }
}

/** A little upright picture tile standing for the wallpaper (the picture itself isn't kept here). */
@Composable
private fun WallTile(seed: Int) {
    val tones = listOf(SkyTone.Blue, SkyTone.Lilac, SkyTone.Mint, SkyTone.Pink, SkyTone.Peach, SkyTone.Butter)
    val top = tones[Math.floorMod(seed, tones.size)].fill()
    val bottom = tones[Math.floorMod(seed + 3, tones.size)].fill()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 60.dp, height = 84.dp)
            .skySurface(top, MeyouwwTheme.crayon.hardShadow, Color.Transparent, 0f, cornerRadius = 14.dp, depth = 3.dp, belly = false)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        Box(Modifier.size(14.dp).background(Color.White.copy(alpha = 0.9f), CircleShape))
    }
}

@Composable
private fun statusPill(status: String, sent: Boolean): Pair<String, SkyTone> = when (status) {
    "applied" -> stringResource(R.string.history_pill_applied) to SkyTone.Mint
    "declined" -> stringResource(if (sent) R.string.history_pill_they_declined else R.string.history_pill_declined) to SkyTone.Butter
    "failed", "expired", "cancelled" -> (if (sent) sentStatus(status) else receivedStatus(status)) to SkyTone.Peach
    "scheduled" -> sentStatus(status) to SkyTone.Lilac
    else -> (if (sent) sentStatus(status) else receivedStatus(status)) to SkyTone.Blue
}

/** "Today 9:12 pm", "Tue" this week, else "2 Nov". */
@Composable
private fun whenText(at: java.time.Instant): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val time = at.atZone(zone)
    val today = java.time.LocalDate.now(zone)
    return when {
        time.toLocalDate() == today -> stringResource(R.string.history_today_at, time.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
        time.toLocalDate().isAfter(today.minusDays(7)) -> time.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale)
        else -> time.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
}
