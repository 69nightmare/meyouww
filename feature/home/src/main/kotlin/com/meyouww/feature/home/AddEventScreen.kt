package com.meyouww.feature.home

import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyField
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButton
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.LocalOnCard
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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

    if (crayon.living) {
        // Living Sky, as on the board: a sheet with "New plan" and a close button at its top.
        SkyPlanSheet(onBack = onBack) {
            val save = {
                val start = if (allDay) date.atStartOfDay() else LocalDateTime.of(date, LocalTime.of(hour, minute))
                viewModel.save(title, start, allDay, note, emoji, remind.takeIf { it >= 0 }, invited)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.event_what), style = SkyType.lb)
                var emojis by rememberSaveable { mutableStateOf(false) }
                SkyField(
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    placeholder = stringResource(R.string.event_what_hint),
                    trailing = {
                        Box {
                            Text(
                                emoji,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClickLabel = stringResource(R.string.event_sky_emoji), role = Role.Button) { emojis = true }
                                    .padding(4.dp),
                            )
                            DropdownMenu(expanded = emojis, onDismissRequest = { emojis = false }) {
                                EMOJIS.chunked(4).forEach { row ->
                                    Row {
                                        row.forEach { e ->
                                            Text(
                                                e,
                                                fontSize = 24.sp,
                                                modifier = Modifier
                                                    .clip(CircleShape)
                                                    .clickable(role = Role.Button) {
                                                        emoji = e
                                                        emojis = false
                                                    }
                                                    .semantics { selected = emoji == e }
                                                    .padding(10.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
            val locale = LocalConfiguration.current.locales[0]
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                PlanTile(
                    icon = SkyIcons.Calendar,
                    label = stringResource(R.string.event_date),
                    value = date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                    tone = SkyTone.Pink,
                    onClick = { pickDate = true },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                PlanTile(
                    icon = SkyIcons.Clock,
                    label = stringResource(R.string.event_sky_time),
                    value = if (allDay) stringResource(R.string.event_all_day) else LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    tone = SkyTone.Blue,
                    onClick = if (allDay) null else ({ pickTime = true }),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            SwitchRow(title = stringResource(R.string.event_all_day), checked = allDay, onCheckedChange = { allDay = it })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.event_sky_remind), style = SkyType.lb)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(-1 to R.string.event_sky_none, 10 to R.string.event_sky_10, 60 to R.string.event_sky_60, 1440 to R.string.event_sky_day)
                        .forEach { (minutes, label) -> SkyChip(stringResource(label), selected = remind == minutes, onClick = { remind = minutes }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.event_invite), style = SkyType.lb)
                if (state.friends.isEmpty()) {
                    Text(stringResource(R.string.event_invite_none), style = SkyType.sb, color = crayon.textSoft)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)) {
                    state.friends.forEach { friend ->
                        val on = friend.id in invited
                        InviteCoin(friend, on) { invited = if (on) invited - friend.id else invited + friend.id }
                    }
                }
            }
            SkyField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                placeholder = stringResource(R.string.event_note),
                singleLine = false,
                maxLines = 4,
            )
            val created = state.created
            when {
                state.failed -> stringResource(R.string.event_failed)
                created != null && created.skipped > 0 -> stringResource(R.string.event_saved_skipped, created.skipped)
                else -> null
            }?.let { Text(it, style = SkyType.sm, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            if (created?.ok == true) {
                SkyButton(
                    if (created.invited > 0) stringResource(R.string.event_saved_invited, created.invited) else stringResource(R.string.event_saved),
                    onDone,
                    block = true,
                )
            } else {
                SkyButton(stringResource(R.string.event_save), save, block = true, loading = state.saving, enabled = title.isNotBlank())
            }
        }
    } else CrayonScreen(title = stringResource(R.string.event_title), onBack = onBack, sheet = true) {
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

/** A chip that can be on or off: cocoa with a tick when on, white with an outline when off. */
@Composable
private fun Toggle(text: String, selected: Boolean, onClick: () -> Unit) {
    CrayonChip(
        text = text,
        fill = null,
        selected = selected,
        onClick = onClick,
    )
}

/**
 * Living Sky: the board's sheet for a short job. It rises from the bottom over the sky, its top
 * holds the title ("New plan") and a round close button, and its content scrolls.
 */
@Composable
private fun SkyPlanSheet(onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 64.dp)
                .fillMaxSize()
                .shadow(18.dp, shape, ambientColor = crayon.shadow, spotColor = crayon.shadow)
                .background(MaterialTheme.colorScheme.surface, shape),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface, LocalOnCard provides true) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 46.dp, height = 5.dp).background(crayon.line, CircleShape))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp),
                ) {
                    Text(stringResource(R.string.event_sky_title), style = SkyType.h2, modifier = Modifier.weight(1f).semantics { heading() })
                    SkyIconButton(SkyIcons.Close, stringResource(R.string.event_sky_close), onBack, size = SkyIconSize.Small)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}

/** One of the two tiles for when (.t2): an icon, a small label over the value, on a pastel. */
@Composable
private fun PlanTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tone: SkyTone, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    SkyCard(modifier = modifier, tone = tone, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), corner = 22.dp, onClick = onClick, onClickLabel = label) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(22.dp))
            Column {
                Text(label, style = SkyType.cap.copy(fontSize = 12.sp, lineHeight = 16.sp), color = Color(0xFF5E4853))
                Text(value, style = SkyType.t.copy(fontSize = 17.sp), color = SkyInk.onPastel, maxLines = 1)
            }
        }
    }
}

/** A friend to invite (.who): their coin with a ring and a tick when invited, their name under it. */
@Composable
private fun InviteCoin(friend: Friend, on: Boolean, onToggle: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(top = 6.dp),
    ) {
        Box(
            Modifier.drawBehind {
                if (on) {
                    val w = 3.dp.toPx()
                    drawCircle(crayon.red, radius = size.minDimension / 2 + 3.dp.toPx() + w / 2, style = Stroke(w))
                }
            },
        ) {
            PersonAvatar(friend.card.initial, friend.id.hashCode(), size = 52.dp, photo = friend.card.avatar)
            if (on) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).background(crayon.red, CircleShape),
                ) { Icon(SkyIcons.Check, contentDescription = null, tint = crayon.onRed, modifier = Modifier.size(14.dp)) }
            }
        }
        Text(friend.card.name, style = SkyType.cap, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
