package com.meyouww.feature.alarms

import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import com.meyouww.core.designsystem.component.PersonAvatar
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import com.meyouww.core.designsystem.component.CardLevel
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.designsystem.component.skyHalo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
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
    if (crayon.living) {
        SkyAlarms(state, viewModel, onBack, onSetForMe)
        return
    }

    CrayonScreen(
        title = stringResource(R.string.alarms_title),
        onBack = onBack,
        // Living Sky: the one main button floats at the bottom, within reach of a thumb.
        floating = { CrayonButton(stringResource(R.string.alarms_set_for_me), onClick = onSetForMe, icon = PastelIcons.Plus) },
    ) {
        if (crayon.living) {
            // Living Sky: the next alarm, big on the sky, with Whity stretching awake beside it.
            NextUp(state.onPhone.minByOrNull { it.nextAtMs })
        } else {
            // A sky card with Whity stretching awake, and the one main button.
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 118) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.alarms_none_body), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Sticker(WhitySticker.GreetGM, decorative = true, size = 96.dp)
                }
                CrayonButton(stringResource(R.string.alarms_set_for_me), onClick = onSetForMe, modifier = Modifier.fillMaxWidth())
            }
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
                CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.bubblegum, seed = alarm.id.hashCode(), level = CardLevel.Raised) {
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
                CrayonCard(modifier = Modifier.fillMaxWidth(), seed = alarm.id.hashCode(), level = CardLevel.Flat) {
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

/** Living Sky: "Next up", the time in big numbers on the sky, what it's for, and Whity stretching awake. */
@Composable
private fun NextUp(next: LocalAlarm?) {
    val crayon = MeyouwwTheme.crayon
    Box(Modifier.fillMaxWidth().heightIn(min = 124.dp)) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (next == null) {
                Text(stringResource(R.string.alarms_none_body), style = MaterialTheme.typography.titleMedium.copy(shadow = skyHalo()))
            } else {
                Text(stringResource(R.string.alarms_next_up), style = MaterialTheme.typography.labelLarge.copy(shadow = skyHalo()), color = crayon.textSoft)
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(next.nextAtMs)),
                    style = MaterialTheme.typography.displayMedium.copy(shadow = skyHalo()),
                    maxLines = 1,
                )
                Text(
                    listOfNotNull(next.title, next.fromName?.let { stringResource(R.string.alarms_from, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.copy(shadow = skyHalo()),
                    color = crayon.textSoft,
                    maxLines = 2,
                )
            }
        }
        Sticker(WhitySticker.GreetGM, decorative = true, size = 112.dp, modifier = Modifier.align(Alignment.CenterEnd))
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

/**
 * Alarms in the Living Sky look, as on the board: the next one big on the sky with Whity
 * stretching awake; requests waiting for your OK on a pink card; warnings as a butter strip with
 * its fix; the alarms on this phone as rows on one card; the ones you sent; and "Set an alarm".
 */
@Composable
private fun SkyAlarms(state: AlarmsUiState, viewModel: AlarmsViewModel, onBack: () -> Unit, onSetForMe: () -> Unit) {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    CrayonScreen(
        title = stringResource(R.string.alarms_title),
        onBack = onBack,
        floating = { SkyButton(stringResource(R.string.alarms_sky_set), onSetForMe, icon = SkyIcons.Plus) },
    ) {
        SkyNextUp(state.onPhone.minByOrNull { it.nextAtMs })
        state.waiting.forEach { alarm ->
            SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Pink, level = CardLevel.Raised, spacing = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PersonAvatar(alarm.sender.initial, alarm.sender.id.hashCode(), size = 38.dp, photo = alarm.sender.avatar)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.alarms_sky_wants, alarm.sender.name), style = SkyType.tt, color = SkyInk.onPastel)
                        Text(skyDescribe(alarm), style = SkyType.sb, color = SkyInk.onPastel)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkyButton(stringResource(R.string.alarms_accept), { viewModel.respond(alarm, true) }, modifier = Modifier.weight(1f), small = true, loading = alarm.id in state.busy)
                    SkyButton(stringResource(R.string.alarms_decline), { viewModel.respond(alarm, false) }, small = true, tone = SkyButtonTone.Quiet)
                }
            }
        }
        if (!state.canExact) {
            SkyWarning(stringResource(R.string.alarms_exact_off), stringResource(R.string.alarms_sky_allow)) { openExactAlarmSettings(context) }
        }
        if (!canFullScreen(context)) {
            SkyWarning(stringResource(R.string.alarms_fullscreen_off), stringResource(R.string.alarms_sky_allow)) { openFullScreenSettings(context) }
        }
        SkySectionHeading(stringResource(R.string.alarms_on_phone), inset = 2.dp)
        if (state.onPhone.isEmpty()) {
            StickerMessage(WhitySticker.MoodNapping, title = stringResource(R.string.alarms_none_title), body = stringResource(R.string.alarms_none_body), blob = crayon.blue)
        } else {
            SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                state.onPhone.forEachIndexed { i, alarm ->
                    val line = crayon.line
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind { if (i > 0) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                            .padding(vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SkyClock(alarm.nextAtMs, big = 24.sp, small = 13.sp)
                            Text(
                                listOfNotNull(
                                    alarm.title,
                                    if (alarm.repeatDays == 0) {
                                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(alarm.nextAtMs))
                                    } else {
                                        repeatText(alarm.repeatDays)
                                    },
                                    if (!alarm.exact) stringResource(R.string.alarms_may_be_late) else null,
                                ).joinToString(" · "),
                                style = SkyType.sb,
                                color = crayon.textSoft,
                            )
                            alarm.fromName?.let { SkyPill(stringResource(R.string.alarms_from, it), tone = SkyTone.Lilac) }
                        }
                        if (alarm.id in state.busy) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        } else {
                            IconButton(onClick = { viewModel.remove(alarm.id) }) {
                                Icon(SkyIcons.Trash, contentDescription = stringResource(R.string.alarms_sky_delete, alarm.title), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        val sent = state.sent
        if (sent.isNotEmpty()) {
            SkySectionHeading(stringResource(R.string.alarms_sent), inset = 2.dp)
            sent.forEach { alarm ->
                SkyCard(modifier = Modifier.fillMaxWidth(), level = CardLevel.Normal, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(alarm.recipient.initial, alarm.recipient.id.hashCode(), size = 38.dp, photo = alarm.recipient.avatar)
                        Column(Modifier.weight(1f)) {
                            Text(alarm.title + " · " + alarm.localTime, style = SkyType.tt)
                            Text(
                                if (alarm.status == "accepted") stringResource(R.string.alarms_sky_on_their_phone, alarm.recipient.name) else statusText(alarm.status),
                                style = SkyType.sb,
                                color = crayon.textSoft,
                            )
                        }
                        if (alarm.status == "pending" || alarm.status == "accepted") {
                            SkyButton(stringResource(R.string.alarms_take_back), { viewModel.remove(alarm.id) }, small = true, tone = SkyButtonTone.Text, loading = alarm.id in state.busy)
                        }
                    }
                }
            }
        }
    }
}

/** "Next up · tonight", the time in big numbers on the sky, what it is and who from; Whity stretching awake beside it. */
@Composable
private fun SkyNextUp(next: LocalAlarm?) {
    Box(Modifier.fillMaxWidth().heightIn(min = 124.dp)) {
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.66f).padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (next == null) {
                Text(stringResource(R.string.alarms_none_body), style = SkyType.t.copy(shadow = skyHalo()), color = SkyInk.onSky)
            } else {
                val zone = java.time.ZoneId.systemDefault()
                val at = java.time.Instant.ofEpochMilli(next.nextAtMs).atZone(zone)
                val today = java.time.LocalDate.now(zone)
                val day = when (at.toLocalDate()) {
                    today -> stringResource(if (at.hour >= 17) R.string.alarms_sky_tonight else R.string.alarms_sky_today)
                    today.plusDays(1) -> stringResource(R.string.alarms_sky_tomorrow)
                    else -> at.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, LocalConfiguration.current.locales[0])
                }
                Text(stringResource(R.string.alarms_sky_next, day), style = SkyType.cap.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
                SkyClock(next.nextAtMs, big = 44.sp, small = 20.sp, onSky = true)
                Text(
                    listOfNotNull(next.title, next.fromName?.let { stringResource(R.string.alarms_from, it) }).joinToString(" · "),
                    style = SkyType.sb.copy(shadow = skyHalo()),
                    color = SkyInk.onSkySoft,
                    maxLines = 2,
                )
            }
        }
        Sticker(WhitySticker.GreetGM, decorative = true, size = 112.dp, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** A time as the boards write it: big numbers and a small "am"/"pm" (or 24-hour numbers alone). */
@Composable
private fun SkyClock(atMs: Long, big: androidx.compose.ui.unit.TextUnit, small: androidx.compose.ui.unit.TextUnit, onSky: Boolean = false) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = java.time.Instant.ofEpochMilli(atMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
    val h24 = android.text.format.DateFormat.is24HourFormat(context)
    val numbers = time.format(DateTimeFormatter.ofPattern(if (h24) "H:mm" else "h:mm", locale))
    val half = if (h24) null else time.format(DateTimeFormatter.ofPattern("a", locale)).lowercase(locale)
    val color = if (onSky) SkyInk.onSky else MaterialTheme.colorScheme.onSurface
    val halo = if (onSky) skyHalo() else null
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(numbers, style = SkyType.h1.copy(fontSize = big, lineHeight = big, shadow = halo), color = color)
        if (half != null) Text(half, style = SkyType.t.copy(fontSize = small, lineHeight = small, shadow = halo), color = color, modifier = Modifier.padding(bottom = 2.dp))
    }
}

/** A warning strip (butter): what's off, and a small button that fixes it. */
@Composable
private fun SkyWarning(text: String, action: String, onClick: () -> Unit) {
    SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Butter, level = CardLevel.Flat, padding = PaddingValues(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), corner = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text, style = SkyType.sm, color = SkyInk.onPastel, modifier = Modifier.weight(1f))
            SkyButton(action, onClick, small = true, tone = SkyButtonTone.Quiet)
        }
    }
}

@Composable
private fun skyDescribe(alarm: AlarmInfo): String {
    val whenText = if (alarm.repeatDays == 0) {
        parseInstant(alarm.ringAt)?.atZone(java.time.ZoneId.of(alarm.timeZone))?.toLocalDate()
            ?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty()
    } else {
        repeatText(alarm.repeatDays)
    }
    return listOf(whenText, alarm.localTime, "\u201C" + alarm.title + "\u201D").filter { it.isNotBlank() }.joinToString(" · ")
}
