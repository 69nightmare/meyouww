package com.meyouww.feature.alarms

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.alarm.LocalAlarm
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.parseInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import javax.inject.Inject

data class AlarmsUiState(
    val onPhone: List<LocalAlarm> = emptyList(),
    val waiting: List<AlarmInfo> = emptyList(),
    val sent: List<AlarmInfo> = emptyList(),
    val canExact: Boolean = true,
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
class AlarmsViewModel @Inject constructor(
    private val alarms: AlarmRepository,
    private val sync: AlarmSync,
) : ViewModel() {
    private val _state = MutableStateFlow(AlarmsUiState())
    val state: StateFlow<AlarmsUiState> = _state.asStateFlow()

    fun refresh() {
        local()
        viewModelScope.launch {
            val mine = (alarms.mine() as? Outcome.Success)?.value
            val sent = (alarms.sent() as? Outcome.Success)?.value
            if (mine != null) {
                sync.syncNow()
                _state.update { it.copy(waiting = mine.filter { a -> a.pending }) }
            }
            if (sent != null) _state.update { it.copy(sent = sent) }
        }
    }

    private fun local() = _state.update {
        it.copy(onPhone = sync.scheduler.all(), canExact = sync.scheduler.canExact())
    }

    fun respond(alarm: AlarmInfo, accept: Boolean) = act(alarm.id) {
        alarms.respond(alarm.id, accept)
        sync.syncNow()
    }

    /** Recipient: delete it from this phone. Sender: take it back. */
    fun remove(id: String) = act(id) {
        sync.scheduler.remove(id)
        alarms.cancel(id)
    }

    private fun act(id: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = it.busy + id) }
        block()
        _state.update { it.copy(busy = it.busy - id) }
        refresh()
    }
}

@Composable
fun AlarmsScreen(
    onBack: () -> Unit,
    onSetForMe: () -> Unit,
    viewModel: AlarmsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon

    CrayonScreen(title = stringResource(R.string.alarms_title), onBack = onBack) {
        // A sky card with Whity stretching awake, and the one main button.
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 118) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.alarms_none_body), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Sticker(WhitySticker.GreetGM, decorative = true, size = 96.dp)
            }
            CrayonButton(stringResource(R.string.alarms_set_for_me), onClick = onSetForMe, modifier = Modifier.fillMaxWidth())
        }

        if (!state.canExact) {
            Warning(
                text = stringResource(R.string.alarms_exact_off),
                action = stringResource(R.string.alarms_exact_fix),
                onClick = { openExactAlarmSettings(context) },
            )
        }
        if (!canFullScreen(context)) {
            Warning(
                text = stringResource(R.string.alarms_fullscreen_off),
                action = stringResource(R.string.alarms_fullscreen_fix),
                onClick = { openFullScreenSettings(context) },
            )
        }

        if (state.waiting.isNotEmpty()) {
            SectionTitle(stringResource(R.string.alarms_waiting))
            state.waiting.forEach { alarm ->
                CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.bubblegum, seed = alarm.id.hashCode()) {
                    Text(stringResource(R.string.alarms_request_from, alarm.sender.name), style = MaterialTheme.typography.titleMedium)
                    Text(describe(alarm), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrayonButton(
                            stringResource(R.string.alarms_accept),
                            onClick = { viewModel.respond(alarm, true) },
                            loading = alarm.id in state.busy,
                            modifier = Modifier.weight(1f),
                        )
                        CrayonButton(
                            stringResource(R.string.alarms_decline),
                            onClick = { viewModel.respond(alarm, false) },
                            kind = ButtonKind.Quiet,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.alarms_on_phone))
        if (state.onPhone.isEmpty()) {
            StickerMessage(WhitySticker.MoodNapping, title = stringResource(R.string.alarms_none_title), body = stringResource(R.string.alarms_none_body), blob = crayon.blue)
        }
        state.onPhone.forEach { alarm ->
            CrayonCard(modifier = Modifier.fillMaxWidth(), seed = alarm.id.hashCode()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(alarm.nextAtMs)),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(alarm.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOfNotNull(
                                if (alarm.repeatDays == 0) {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(alarm.nextAtMs))
                                } else {
                                    repeatText(alarm.repeatDays)
                                },
                                alarm.fromName?.let { stringResource(R.string.alarms_from, it) },
                                if (!alarm.exact) stringResource(R.string.alarms_may_be_late) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = crayon.textSoft,
                        )
                    }
                    CrayonButton(
                        stringResource(R.string.alarms_delete),
                        onClick = { viewModel.remove(alarm.id) },
                        kind = ButtonKind.Quiet,
                        loading = alarm.id in state.busy,
                    )
                }
            }
        }

        val sent = state.sent
        if (sent.isNotEmpty()) {
            SectionTitle(stringResource(R.string.alarms_sent))
            sent.forEach { alarm ->
                CrayonCard(modifier = Modifier.fillMaxWidth(), seed = alarm.id.hashCode()) {
                    Text(stringResource(R.string.alarms_sent_to, alarm.recipient.name, alarm.title), style = MaterialTheme.typography.titleMedium)
                    Text(describe(alarm) + " · " + statusText(alarm.status), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                    if (alarm.status == "pending" || alarm.status == "accepted") {
                        CrayonButton(
                            stringResource(R.string.alarms_take_back),
                            onClick = { viewModel.remove(alarm.id) },
                            kind = ButtonKind.Quiet,
                            loading = alarm.id in state.busy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Warning(text: String, action: String, onClick: () -> Unit) {
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.yellow, seed = text.hashCode()) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        CrayonButton(action, onClick = onClick, kind = ButtonKind.Quiet, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun describe(alarm: AlarmInfo): String {
    val time = alarm.localTime
    val whenText = if (alarm.repeatDays == 0) {
        parseInstant(alarm.ringAt)?.atZone(java.time.ZoneId.of(alarm.timeZone))?.toLocalDate()
            ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()
    } else {
        repeatText(alarm.repeatDays)
    }
    return "⏰ ${alarm.title} · $time · $whenText"
}

@Composable
private fun statusText(status: String): String = when (status) {
    "pending" -> stringResource(R.string.alarms_status_pending)
    "accepted" -> stringResource(R.string.alarms_status_accepted)
    "declined" -> stringResource(R.string.alarms_status_declined)
    "cancelled" -> stringResource(R.string.alarms_status_cancelled)
    "deleted" -> stringResource(R.string.alarms_status_deleted)
    "expired" -> stringResource(R.string.alarms_status_expired)
    else -> status
}

private fun canFullScreen(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
