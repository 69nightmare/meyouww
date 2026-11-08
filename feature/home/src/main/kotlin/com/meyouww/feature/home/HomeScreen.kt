package com.meyouww.feature.home

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.device.DeviceStateCollector
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.Bunting
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.CalendarGrid
import com.meyouww.core.model.DayPart
import com.meyouww.core.model.DeliveryHealth
import com.meyouww.core.model.EventInfo
import com.meyouww.core.model.PersonCard
import com.meyouww.core.model.Profile
import com.meyouww.core.render.AvatarEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import javax.inject.Inject

data class HomeUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    val error: AppError? = null,
    val deliveryReady: Boolean? = null,
    val dayPart: DayPart = DayPart.ofHour(LocalTime.now().hour),
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val events: List<EventInfo> = emptyList(),
    val uploadingPhoto: Boolean = false,
    val photoFailed: Boolean = false,
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    private val avatars: AvatarRepository,
    private val calendar: CalendarRepository,
    private val alarmSync: AlarmSync,
    private val stateCollector: DeviceStateCollector,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeUiState(
            profile = profiles.profile.value,
            loading = profiles.profile.value == null,
            events = calendar.events.value.orEmpty(),
        ),
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(dayPart = DayPart.ofHour(LocalTime.now().hour)) }
        val results = DeliveryHealth.evaluate(
            state = stateCollector.collect(),
            manufacturer = Build.MANUFACTURER,
            phoneMakerStepConfirmed = prefs.autostartConfirmed.first(),
            pushConfigured = BackendConfig.isFirebaseConfigured,
        )
        _state.update { it.copy(deliveryReady = DeliveryHealth.isReady(results)) }
        loadEvents()
        when (val outcome = profiles.refresh()) {
            is Outcome.Success -> _state.update { it.copy(profile = outcome.value ?: it.profile, loading = false, error = null) }
            // Keep showing the cached profile when offline.
            is Outcome.Failure -> _state.update { it.copy(loading = false, error = if (it.profile == null) outcome.error else null) }
        }
    }

    /** The shown month plus the next two, so "Upcoming" has something to show. */
    private suspend fun loadEvents() {
        val month = _state.value.month
        val from = minOf(month.atDay(1), LocalDate.now()).minusDays(7)
        val to = maxOf(month.atEndOfMonth(), LocalDate.now().plusDays(60))
        val outcome = calendar.refresh(from, to)
        if (outcome is Outcome.Success) {
            _state.update { it.copy(events = outcome.value) }
            withContext(Dispatchers.IO) { alarmSync.reminders.update(outcome.value) }
        }
    }

    fun showMonth(month: YearMonth) {
        _state.update { it.copy(month = month) }
        viewModelScope.launch { loadEvents() }
    }

    fun select(date: LocalDate) = _state.update { it.copy(selected = date) }

    fun respond(event: EventInfo, going: Boolean) = act(event.id) { calendar.respond(event.id, going) }

    fun cancel(event: EventInfo) = act(event.id) { calendar.cancel(event.id) }

    private fun act(id: String, block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = it.busy + id) }
        block()
        loadEvents()
        _state.update { it.copy(busy = it.busy - id) }
    }

    /** Square-crops, shrinks and uploads a new profile picture. */
    fun setPhoto(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(uploadingPhoto = true, photoFailed = false) }
        val jpeg = withContext(Dispatchers.IO) { AvatarEncoder.encode { context.contentResolver.openInputStream(uri) } }
        val ok = jpeg != null && avatars.upload(jpeg) is Outcome.Success
        _state.update { it.copy(uploadingPhoto = false, photoFailed = !ok, profile = profiles.profile.value ?: it.profile) }
    }

    fun removePhoto() = viewModelScope.launch {
        _state.update { it.copy(uploadingPhoto = true) }
        avatars.remove()
        _state.update { it.copy(uploadingPhoto = false, profile = profiles.profile.value ?: it.profile) }
    }
}

/**
 * Home: you, your inbox heart and a + to add friends; a month calendar where the
 * little faces under a date show who marked it; and what's coming up.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDeliverySetup: () -> Unit,
    onAddFriend: () -> Unit,
    onAddEvent: (LocalDate) -> Unit,
    heart: @Composable () -> Unit,
    onAlarms: () -> Unit = {},
    /** Things waiting for an answer (a friend's wallpaper), shown under the top row. */
    banner: @Composable () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val profile = state.profile

    val narrow = LocalConfiguration.current.screenWidthDp < 360
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (narrow) 10.dp else 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
    ) {
        Bunting()
        TopRow(
            profile = profile,
            dayPart = state.dayPart,
            uploading = state.uploadingPhoto,
            onPickPhoto = viewModel::setPhoto,
            onRemovePhoto = viewModel::removePhoto,
            onOpenSettings = onOpenSettings,
            onAddFriend = onAddFriend,
            heart = heart,
        )
        if (state.photoFailed) {
            Text(stringResource(R.string.home_photo_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        banner()
        when {
            profile != null -> {
                if (state.deliveryReady == false) {
                    CrayonChip(
                        text = stringResource(R.string.home_delivery_fix),
                        fill = MeyouwwTheme.crayon.bubblegum,
                        onClick = onOpenDeliverySetup,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MonthCalendar(
                    month = state.month,
                    selected = state.selected,
                    events = state.events,
                    onMonth = viewModel::showMonth,
                    onSelect = viewModel::select,
                )
                DayPanel(
                    date = state.selected,
                    events = state.events,
                    busy = state.busy,
                    onAdd = { onAddEvent(state.selected) },
                    onRespond = viewModel::respond,
                    onCancel = viewModel::cancel,
                )
                Upcoming(state.events, state.selected, onSelect = viewModel::select)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrayonChip(text = stringResource(R.string.home_alarms), onClick = onAlarms, fill = MaterialTheme.colorScheme.surface)
                }
            }
            state.loading -> WhityLoading(stringResource(R.string.home_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            else -> StickerMessage(
                sticker = WhitySticker.Confused,
                title = stringResource(R.string.home_profile_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.home_retry),
                onAction = { viewModel.refresh() },
            )
        }
    }
}

@Composable
private fun TopRow(
    profile: Profile?,
    dayPart: DayPart,
    uploading: Boolean,
    onPickPhoto: (Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFriend: () -> Unit,
    heart: @Composable () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPickPhoto(uri) }
    val greeting = when (dayPart) {
        DayPart.MORNING -> R.string.home_hello_morning
        DayPart.AFTERNOON -> R.string.home_hello_afternoon
        DayPart.EVENING -> R.string.home_hello_evening
        DayPart.NIGHT -> R.string.home_hello_night
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val label = stringResource(R.string.home_profile_menu)
        Box {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = label, role = Role.Button) { menu = true }
                    .semantics { contentDescription = label },
            ) {
                PersonAvatar(
                    initial = (profile?.greetingName?.firstOrNull() ?: '♥').uppercase(),
                    seed = (profile?.id ?: "me").hashCode(),
                    size = 48.dp,
                    photo = profile?.avatarPath,
                )
                if (uploading) {
                    Box(
                        Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("…", style = MaterialTheme.typography.titleLarge) }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_photo_change)) }, onClick = {
                    menu = false
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
                if (profile?.avatarPath != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.home_photo_remove)) }, onClick = {
                        menu = false
                        onRemovePhoto()
                    })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.home_settings)) }, onClick = {
                    menu = false
                    onOpenSettings()
                })
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(greeting), style = MaterialTheme.typography.labelLarge, color = MeyouwwTheme.crayon.textSoft)
            Text(
                profile?.greetingName.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        heart()
        IconButton(onClick = onAddFriend) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_add_friend))
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    events: List<EventInfo>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val zone = remember { ZoneId.systemDefault() }
    val byDate = remember(events) { CalendarGrid.byDate(events, zone) }
    val today = LocalDate.now()
    val cells = remember(month, firstDay) { CalendarGrid.cells(month, firstDay) }
    val rows = cells.chunked(7).filter { week -> week.any { it != null } }

    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MaterialTheme.colorScheme.surface, seed = 31) {
        TapeLabel(
            text = stringResource(R.string.home_calendar_tape),
            tape = crayon.yellow,
            style = MaterialTheme.typography.labelMedium,
            isHeading = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (month != YearMonth.from(today)) {
                TextButton(onClick = {
                    onMonth(YearMonth.from(today))
                    onSelect(today)
                }) { Text(stringResource(R.string.home_today)) }
            }
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.home_previous_month))
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.home_next_month))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            CalendarGrid.weekdays(firstDay).forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    color = crayon.textSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                events = byDate[date].orEmpty(),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, events: List<EventInfo>, onClick: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val grain = LocalCrayonGrain.current
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected && !reducedMotion) 1.035f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "calendar selection",
    )
    // The same soft crayon box used in the calendar preview, with ink text on every colour.
    val crayons = listOf(
        Color(0xFFFFD3D1), Color(0xFFFFDCAE), Color(0xFFFFEDAA), Color(0xFFD3ECCD),
        Color(0xFFCCE8F4), Color(0xFFE1D8F5), Color(0xFFFBD3E6),
    )
    val fill = crayons[date.dayOfWeek.value % crayons.size]
    val people = events.flatMap { it.people }.distinctBy { it.id }
    val description = stringResource(R.string.home_day_description, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), events.size)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .crayonSurface(
                fill = fill,
                outline = if (isSelected) crayon.outline else crayon.outline.copy(alpha = 0.28f),
                seed = date.dayOfMonth,
                cornerRadius = 11.dp,
                strokeWidth = if (isSelected) 2.dp else 1.dp,
                wobble = 0.5.dp,
                grain = grain,
                grainAlpha = 0.12f,
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            }
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .then(
                    when {
                        isSelected -> Modifier.background(MeyouwwTheme.crayon.red, CircleShape)
                        isToday -> Modifier.border(2.5.dp, MeyouwwTheme.crayon.red, CircleShape)
                        else -> Modifier
                    },
                ),
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            )
        }
        // Who marked it: up to three tiny faces, overlapping a little.
        Box(Modifier.height(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (people.isNotEmpty()) TinyFaces(people.take(3), extra = people.size - 3)
        }
        if (events.isNotEmpty() && people.isEmpty()) {
            Box(Modifier.size(5.dp).background(crayon.outline, CircleShape))
        }
    }
}

@Composable
private fun TinyFaces(people: List<PersonCard>, extra: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        people.forEachIndexed { index, person ->
            PersonAvatar(
                initial = person.initial,
                seed = person.id.hashCode(),
                size = 14.dp,
                photo = person.avatar,
                modifier = Modifier.offset(x = (-4 * index).dp),
            )
        }
        if (extra > 0) {
            Text("+$extra", style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(x = (-4 * people.size + 2).dp))
        }
    }
}

@Composable
private fun DayPanel(
    date: LocalDate,
    events: List<EventInfo>,
    busy: Set<String>,
    onAdd: () -> Unit,
    onRespond: (EventInfo, Boolean) -> Unit,
    onCancel: (EventInfo) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = events.filter { !it.cancelled && it.myStatus != "declined" && it.dateIn(zone) == date }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.home_add_event))
        }
    }
    if (today.isEmpty()) {
        Text(stringResource(R.string.home_day_empty), style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
    }
    today.forEach { event -> EventRow(event, busy = event.id in busy, onRespond = onRespond, onCancel = onCancel) }
}

@Composable
private fun EventRow(event: EventInfo, busy: Boolean, onRespond: (EventInfo, Boolean) -> Unit, onCancel: (EventInfo) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    var menu by remember { mutableStateOf(false) }
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = if (event.invited) crayon.yellow else MaterialTheme.colorScheme.surface, seed = event.id.hashCode()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(event.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
            Column(Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(eventWhen(event), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
            }
            PersonAvatar(event.owner.initial, event.owner.id.hashCode(), size = 28.dp, photo = event.owner.avatar)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) { Text("⋯") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (event.mine) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_event_cancel)) }, onClick = {
                            menu = false
                            onCancel(event)
                        })
                    } else if (event.going) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.home_event_leave)) }, onClick = {
                            menu = false
                            onRespond(event, false)
                        })
                    }
                }
            }
        }
        if (!event.mine) {
            Text(stringResource(R.string.home_event_by, event.owner.name), style = MaterialTheme.typography.labelSmall, color = crayon.textSoft)
        }
        event.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        if (event.invited) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(stringResource(R.string.home_event_going), onClick = { onRespond(event, true) }, loading = busy, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.home_event_not_going), onClick = { onRespond(event, false) }, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun eventWhen(event: EventInfo): String {
    if (event.allDay) return stringResource(R.string.home_event_all_day)
    val start = event.start ?: return ""
    return start.atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@Composable
private fun Upcoming(events: List<EventInfo>, selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val next = events
        .filter { !it.cancelled && it.myStatus != "declined" }
        .mapNotNull { e -> e.dateIn(zone)?.let { it to e } }
        .filter { (date, _) -> date >= today && date != selected }
        .sortedBy { (date, e) -> date.toEpochDay() * 100_000 + (e.start?.epochSecond ?: 0) % 100_000 }
        .take(5)
    if (next.isEmpty()) return
    Text(stringResource(R.string.home_upcoming), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    next.forEach { (date, event) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button) { onSelect(date) }
                .padding(vertical = 6.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(46.dp)
                    .aspectRatio(1f)
                    .background(MeyouwwTheme.crayon.yellow, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium, color = MeyouwwTheme.crayon.onFill)
                Text(
                    date.month.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.labelSmall,
                    color = MeyouwwTheme.crayon.onFill,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(event.emoji + " " + event.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (event.invited) stringResource(R.string.home_event_invited, event.owner.name) else eventWhen(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MeyouwwTheme.crayon.textSoft,
                )
            }
            TinyFaces(event.people.take(3), extra = event.people.size - 3)
        }
    }
}
