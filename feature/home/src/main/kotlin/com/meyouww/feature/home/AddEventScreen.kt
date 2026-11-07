package com.meyouww.feature.home

import androidx.compose.foundation.layout.Arrangement
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
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.EventCreated
import com.meyouww.core.model.Friend
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class AddEventUiState(
    val friends: List<Friend> = emptyList(),
    val saving: Boolean = false,
    val created: EventCreated? = null,
    val failed: Boolean = false,
)

@HiltViewModel
class AddEventViewModel @Inject constructor(
    private val calendar: CalendarRepository,
    private val friendsRepo: FriendsRepository,
    private val alarmSync: AlarmSync,
) : ViewModel() {
    private val _state = MutableStateFlow(AddEventUiState(friends = friendsRepo.friends.value.orEmpty()))
    val state: StateFlow<AddEventUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            (friendsRepo.refreshFriends() as? Outcome.Success)?.value?.let { list -> _state.update { it.copy(friends = list) } }
        }
    }

    fun save(title: String, start: LocalDateTime, allDay: Boolean, note: String, emoji: String, remind: Int?, invitees: List<String>) {
        _state.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            val outcome = calendar.create(title, start, allDay, note, emoji, remind, invitees)
            _state.update {
                when (outcome) {
                    is Outcome.Success -> it.copy(saving = false, created = outcome.value, failed = !outcome.value.ok)
                    is Outcome.Failure -> it.copy(saving = false, failed = true)
                }
            }
            if (outcome is Outcome.Success && outcome.value.ok) alarmSync.syncCalendar()
        }
    }
}

private val EMOJIS = listOf("📅", "🎂", "🍿", "🍕", "🎉", "✈️", "💕", "🏋️", "📚", "🎮", "🎵", "☕")

/** Mark a day, optionally invite friends. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventScreen(
    initialDate: LocalDate,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: AddEventViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon

    var emoji by rememberSaveable { mutableStateOf(EMOJIS.first()) }
    var title by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var allDay by rememberSaveable { mutableStateOf(false) }
    var hour by rememberSaveable { mutableStateOf(19) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var remind by rememberSaveable { mutableStateOf(60) } // minutes; -1 = none
    var invited by rememberSaveable { mutableStateOf(listOf<String>()) }
    var note by rememberSaveable { mutableStateOf("") }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(dateEpochDay)

    LaunchedEffect(state.created) {
        if (state.created?.ok == true && state.created?.skipped == 0) onDone()
    }

    CrayonScreen(title = stringResource(R.string.event_title), onBack = onBack) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EMOJIS.forEach { e -> Toggle(text = e, selected = emoji == e, onClick = { emoji = e }) }
        }
        CrayonTextField(
            value = title,
            onValueChange = { if (it.length <= 60) title = it },
            label = stringResource(R.string.event_what),
            placeholder = stringResource(R.string.event_what_hint),
        )

        SectionTitle(stringResource(R.string.event_date))
        Text(date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CrayonChip(text = stringResource(R.string.event_change_date), onClick = { pickDate = true })
            if (!allDay) {
                CrayonChip(
                    text = stringResource(R.string.event_time, LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))),
                    onClick = { pickTime = true },
                )
            }
        }
        SwitchRow(title = stringResource(R.string.event_all_day), checked = allDay, onCheckedChange = { allDay = it })

        SectionTitle(stringResource(R.string.event_reminder))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1 to R.string.event_reminder_none, 10 to R.string.event_reminder_10, 60 to R.string.event_reminder_60, 1440 to R.string.event_reminder_day)
                .forEach { (minutes, label) -> Toggle(text = stringResource(label), selected = remind == minutes, onClick = { remind = minutes }) }
        }

        SectionTitle(stringResource(R.string.event_invite))
        if (state.friends.isEmpty()) {
            Text(stringResource(R.string.event_invite_none), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.friends.forEach { friend ->
                val on = friend.id in invited
                Toggle(text = friend.card.name, selected = on, onClick = { invited = if (on) invited - friend.id else invited + friend.id })
            }
        }
        CrayonTextField(
            value = note,
            onValueChange = { if (it.length <= 200) note = it },
            label = stringResource(R.string.event_note),
            singleLine = false,
        )

        val created = state.created
        when {
            state.failed -> stringResource(R.string.event_failed)
            created != null && created.skipped > 0 -> stringResource(R.string.event_saved_skipped, created.skipped)
            else -> null
        }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }

        if (created?.ok == true) {
            CrayonButton(
                if (created.invited > 0) stringResource(R.string.event_saved_invited, created.invited) else stringResource(R.string.event_saved),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CrayonButton(
                stringResource(R.string.event_save),
                onClick = {
                    val start = if (allDay) date.atStartOfDay() else LocalDateTime.of(date, LocalTime.of(hour, minute))
                    viewModel.save(title, start, allDay, note, emoji, remind.takeIf { it >= 0 }, invited)
                },
                loading = state.saving,
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (pickDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    pickDate = false
                }) { Text(stringResource(R.string.event_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.event_cancel)) } },
        ) { DatePicker(state = dateState) }
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
                }) { Text(stringResource(R.string.event_ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.event_cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}

/** A chip that can be on or off (✓ and a blue fill when on). */
@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    CrayonChip(
        text = if (selected) "✓ $text" else text,
        fill = if (selected) crayon.blue else MaterialTheme.colorScheme.surface,
        onClick = onClick,
        modifier = Modifier.semantics { this.selected = selected },
    )
}
