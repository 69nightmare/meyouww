package com.meyouww.feature.inbox

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
