package com.meyouww.feature.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.AlarmSendOutcome
import com.meyouww.core.model.AlarmTimes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import javax.inject.Inject

data class SendAlarmUiState(
    val recipientName: String = "",
    val forMe: Boolean = false,
    val sending: Boolean = false,
    val outcome: AlarmSendOutcome? = null,
    val error: AppError? = null,
)

@HiltViewModel
class SendAlarmViewModel @Inject constructor(
    private val alarms: AlarmRepository,
    private val friends: FriendsRepository,
    private val auth: AuthRepository,
    private val alarmSync: AlarmSync,
) : ViewModel() {
    private val _state = MutableStateFlow(SendAlarmUiState())
    val state: StateFlow<SendAlarmUiState> = _state.asStateFlow()
    private var recipientId: String? = null

    fun load(peerId: String?) {
        val me = auth.currentUserId()
        recipientId = peerId ?: me
        val forMe = peerId == null || peerId == me
        val name = if (forMe) "" else friends.friends.value?.firstOrNull { it.id == peerId }?.card?.name.orEmpty()
        _state.update { it.copy(recipientName = name, forMe = forMe) }
    }

    fun send(title: String, message: String, at: LocalDateTime, repeatDays: Int, snooze: Int, vibrate: Boolean, hold: Boolean) {
        val to = recipientId ?: return
        _state.update { it.copy(sending = true, outcome = null, error = null) }
        viewModelScope.launch {
            val outcome = alarms.create(to, title, message, at, repeatDays, snooze, vibrate, if (hold) "hold" else "tap")
            _state.update {
                when (outcome) {
                    is Outcome.Success -> it.copy(sending = false, outcome = outcome.value)
                    is Outcome.Failure -> it.copy(sending = false, error = outcome.error)
                }
            }
            // An alarm for myself goes onto this phone straight away.
            if (outcome is Outcome.Success && outcome.value == AlarmSendOutcome.Added && _state.value.forMe) alarmSync.syncNow()
        }
    }

    fun clearOutcome() = _state.update { it.copy(outcome = null, error = null) }
}

/**
 * Set an alarm on a friend's phone (or this one). The time is a wall-clock time on
 * THEIR phone: 7:00 means 7:00 where they are.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SendAlarmScreen(
    peerId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: SendAlarmViewModel = hiltViewModel(),
) {
    LaunchedEffect(peerId) { viewModel.load(peerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val crayon = MeyouwwTheme.crayon
    val context = LocalContext.current
    val name = state.recipientName.ifBlank { stringResource(R.string.alarm_send_friend) }

    val defaultTitle = stringResource(R.string.alarm_send_default_title)
    var title by rememberSaveable { mutableStateOf(defaultTitle) }
    var message by rememberSaveable { mutableStateOf("") }
    val tomorrow = LocalDate.now().plusDays(1)
    var dateEpochDay by rememberSaveable { mutableStateOf(tomorrow.toEpochDay()) }
    var hour by rememberSaveable { mutableStateOf(7) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var repeatDays by rememberSaveable { mutableStateOf(0) }
    var snooze by rememberSaveable { mutableStateOf(5) }
    var vibrate by rememberSaveable { mutableStateOf(true) }
    var hold by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.ofEpochDay(dateEpochDay)
    val time = LocalTime.of(hour, minute)

    if (confirm) {
        // One last look before it is set: what, when and for whom.
        val whenText = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) + ", " +
            if (repeatDays == 0) date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) else repeatText(repeatDays)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.alarm_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("⏰ " + title, style = MaterialTheme.typography.titleMedium)
                    Text(whenText, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.forMe) stringResource(R.string.alarm_confirm_for_me) else stringResource(R.string.alarm_confirm_for, name),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    // A repeating alarm starts from the next matching day; the server only needs a first time.
                    val first = if (repeatDays == 0) LocalDateTime.of(date, time) else firstRepeat(repeatDays, time)
                    viewModel.send(title, message, first, repeatDays, snooze, vibrate, hold)
                }) { Text(stringResource(R.string.alarm_confirm_yes)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.alarm_confirm_no)) } },
        )
    }

    CrayonScreen(
        title = if (state.forMe) stringResource(R.string.alarm_send_title_me) else stringResource(R.string.alarm_send_title, name),
        onBack = onBack,
    ) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 21) {
            Text(
                time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                if (repeatDays == 0) {
                    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                } else {
                    repeatText(repeatDays)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!state.forMe) {
                Text(stringResource(R.string.alarm_send_their_time, name), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
            }
            CrayonButton(stringResource(R.string.alarm_send_change_time), onClick = { pickTime = true }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
        }

        if (repeatDays == 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    text = stringResource(R.string.alarm_send_today),
                    selected = date == LocalDate.now(),
                    onClick = { dateEpochDay = LocalDate.now().toEpochDay() },
                )
                ToggleChip(
                    text = stringResource(R.string.alarm_send_tomorrow),
                    selected = date == tomorrow,
                    onClick = { dateEpochDay = tomorrow.toEpochDay() },
                )
                CrayonChip(text = stringResource(R.string.alarm_send_pick_date), onClick = { pickDate = true })
            }
        }

        SectionTitle(stringResource(R.string.alarm_send_repeat))
        val locale = LocalConfiguration.current.locales[0]
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { day ->
                val on = AlarmTimes.repeatsOn(repeatDays, day)
                ToggleChip(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    selected = on,
                    onClick = { repeatDays = repeatDays xor AlarmTimes.bit(day) },
                )
            }
        }

        CrayonTextField(value = title, onValueChange = { if (it.length <= 60) title = it }, label = stringResource(R.string.alarm_send_label))
        CrayonTextField(
            value = message,
            onValueChange = { if (it.length <= 200) message = it },
            label = stringResource(R.string.alarm_send_message),
            placeholder = stringResource(R.string.alarm_send_message_hint),
            singleLine = false,
        )

        SectionTitle(stringResource(R.string.alarm_send_snooze))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15).forEach { m ->
                ToggleChip(text = stringResource(R.string.alarm_send_minutes, m), selected = snooze == m, onClick = { snooze = m })
            }
        }
        SwitchRow(title = stringResource(R.string.alarm_send_vibrate), checked = vibrate, onCheckedChange = { vibrate = it })
        SwitchRow(
            title = stringResource(R.string.alarm_send_hold),
            detail = stringResource(R.string.alarm_send_hold_detail),
            checked = hold,
            onCheckedChange = { hold = it },
        )

        val result = when {
            state.error != null -> "⚠ " + stringResource(state.error!!.messageRes())
            else -> when (state.outcome) {
                AlarmSendOutcome.Sent -> stringResource(R.string.alarm_result_sent, name)
                AlarmSendOutcome.Added -> if (state.forMe) stringResource(R.string.alarm_result_added_me) else stringResource(R.string.alarm_result_added, name)
                AlarmSendOutcome.Unavailable -> stringResource(R.string.alarm_result_unavailable, name)
                AlarmSendOutcome.Limit -> stringResource(R.string.alarm_result_limit, name)
                AlarmSendOutcome.NotFriends -> stringResource(R.string.alarm_result_not_friends)
                AlarmSendOutcome.InPast -> stringResource(R.string.alarm_result_in_past)
                AlarmSendOutcome.Invalid -> stringResource(R.string.alarm_result_invalid)
                AlarmSendOutcome.RateLimited -> stringResource(R.string.alarm_result_rate_limited)
                AlarmSendOutcome.Failed -> stringResource(R.string.alarm_result_failed)
                null -> null
            }
        }
        result?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

        val done = state.outcome == AlarmSendOutcome.Sent || state.outcome == AlarmSendOutcome.Added
        if (done) {
            CrayonButton(stringResource(R.string.alarm_send_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
        } else {
            CrayonButton(
                text = if (state.forMe) stringResource(R.string.alarm_send_set) else stringResource(R.string.alarm_send_send, name),
                onClick = { confirm = true },
                loading = state.sending,
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.alarm_send_privacy), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }

    if (pickTime) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = timeState.hour
                    minute = timeState.minute
                    pickTime = false
                    viewModel.clearOutcome()
                }) { Text(stringResource(R.string.alarm_send_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.alarm_send_cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
    if (pickDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    pickDate = false
                    viewModel.clearOutcome()
                }) { Text(stringResource(R.string.alarm_send_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.alarm_send_cancel)) } },
        ) { DatePicker(state = dateState) }
    }
}

/** The next day (from today, in this phone's zone) that the repeat includes, at [time]. */
private fun firstRepeat(repeatDays: Int, time: LocalTime): LocalDateTime {
    var date = LocalDate.now()
    repeat(8) {
        val candidate = LocalDateTime.of(date, time)
        if (AlarmTimes.repeatsOn(repeatDays, date.dayOfWeek) && candidate.isAfter(LocalDateTime.now().plusMinutes(1))) return candidate
        date = date.plusDays(1)
    }
    return LocalDateTime.of(LocalDate.now().plusDays(1), time)
}

@Composable
internal fun repeatText(repeatDays: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (repeatDays) {
        0 -> stringResource(R.string.alarm_repeat_once)
        AlarmTimes.EVERY_DAY -> stringResource(R.string.alarm_repeat_every_day)
        AlarmTimes.WEEKDAYS -> stringResource(R.string.alarm_repeat_weekdays)
        else -> DayOfWeek.entries.filter { AlarmTimes.repeatsOn(repeatDays, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
}

/** A chip that can be on or off (✓ and a blue fill when on). */
@Composable
internal fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonChip(
        text = if (selected) "✓ $text" else text,
        fill = if (selected) crayon.blue else MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
    )
}
