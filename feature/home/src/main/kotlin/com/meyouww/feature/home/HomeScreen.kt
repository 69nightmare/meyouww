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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.device.DeviceStateCollector
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.pet.WhityBuddy
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.AlarmTimes
import com.meyouww.core.model.CalendarGrid
import com.meyouww.core.model.DayPart
import com.meyouww.core.model.DeliveryHealth
import com.meyouww.core.model.EventInfo
import com.meyouww.core.model.Holiday
import com.meyouww.core.model.IndianHolidays
import com.meyouww.core.model.parseInstant
import com.meyouww.core.model.PersonCard
import com.meyouww.core.model.Profile
import com.meyouww.core.render.AvatarCrop
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
    /** Alarms that ring on this phone (mine, and friends' that I accepted). */
    val alarms: List<AlarmInfo> = emptyList(),
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
    private val alarms: AlarmRepository,
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
        // The alarms that ring here, for the clock marks on the calendar.
        (alarms.mine() as? Outcome.Success)?.value?.let { list ->
            _state.update { it.copy(alarms = list.filter { a -> a.accepted && a.toMe }) }
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

    /** Crops to the square framed in the circle (or the centre), shrinks and uploads a new profile picture. */
    fun setPhoto(uri: Uri, crop: AvatarCrop? = null) = viewModelScope.launch {
        _state.update { it.copy(uploadingPhoto = true, photoFailed = false) }
        val jpeg = withContext(Dispatchers.IO) { AvatarEncoder.encode({ context.contentResolver.openInputStream(uri) }, crop) }
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
        TopRow(
            profile = profile,
            dayPart = state.dayPart,
            uploading = state.uploadingPhoto,
            onPickPhoto = { uri, crop -> viewModel.setPhoto(uri, crop) },
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
                // Whity sits with her paws over the calendar's top edge (drawn above it); the
                // alarms chip stays on the right.
                Row(Modifier.fillMaxWidth().zIndex(1f), verticalAlignment = Alignment.Bottom) {
                    WhityBuddy(
                        width = 96.dp,
                        modifier = Modifier.padding(start = 8.dp).offset(y = if (narrow) 17.dp else 21.dp),
                        chatty = true,
                        next = whityNext(state.events),
                    )
                    Spacer(Modifier.weight(1f))
                    CrayonChip(
                        text = stringResource(R.string.home_alarms),
                        onClick = onAlarms,
                        fill = MeyouwwTheme.crayon.lilac,
                    )
                }
                MonthCalendar(
                    month = state.month,
                    selected = state.selected,
                    events = state.events,
                    alarms = state.alarms,
                    onMonth = viewModel::showMonth,
                    onSelect = viewModel::select,
                )
                DayPanel(
                    date = state.selected,
                    events = state.events,
                    alarms = state.alarms,
                    busy = state.busy,
                    onAdd = { onAddEvent(state.selected) },
                    onRespond = viewModel::respond,
                    onCancel = viewModel::cancel,
                )
                Upcoming(state.events, state.selected, onSelect = viewModel::select)
            }
            state.loading -> WhityLoading(stringResource(R.string.home_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            else -> StickerMessage(
                sticker = WhitySticker.OopsFacepalm,
                blob = MeyouwwTheme.crayon.peach,
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
    onPickPhoto: (Uri, AvatarCrop) -> Unit,
    onRemovePhoto: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddFriend: () -> Unit,
    heart: @Composable () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    // Picking a photo opens the circle framer first; uploading happens after "Use photo".
    var framing by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) framing = uri }
    framing?.let { uri ->
        AvatarCropDialog(
            uri = uri,
            onDismiss = { framing = null },
            onDone = { crop ->
                framing = null
                onPickPhoto(uri, crop)
            },
        )
    }
    val greeting = when (dayPart) {
        DayPart.MORNING -> R.string.home_hello_morning
        DayPart.AFTERNOON -> R.string.home_hello_afternoon
        DayPart.EVENING -> R.string.home_hello_evening
        DayPart.NIGHT -> R.string.home_hello_night
    }
    // An open header on the page: your photo, the greeting, then round heart and add buttons.
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                    size = 52.dp,
                    photo = profile?.avatarPath,
                )
                if (uploading) {
                    Box(
                        Modifier.size(52.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape),
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
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(stringResource(greeting), style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
            Text(
                profile?.greetingName.orEmpty(),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        heart()
        IconButton(
            onClick = onAddFriend,
            modifier = Modifier.size(48.dp).crayonSurface(
                fill = MeyouwwTheme.crayon.bubblegum,
                outline = Color.Transparent,
                seed = 124,
                cornerRadius = 24.dp,
                strokeWidth = 0.dp,
            ),
        ) {
            Icon(PastelIcons.AddPerson, contentDescription = stringResource(R.string.home_add_friend), tint = MeyouwwTheme.crayon.onFill)
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    events: List<EventInfo>,
    alarms: List<AlarmInfo>,
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

    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MaterialTheme.colorScheme.surface, seed = 31, contentPadding = PaddingValues(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TapeLabel(
                text = stringResource(R.string.home_calendar_tape),
                tape = crayon.yellow,
                style = MaterialTheme.typography.labelMedium,
                isHeading = false,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (month != YearMonth.from(today)) {
                TextButton(onClick = {
                    onMonth(YearMonth.from(today))
                    onSelect(today)
                }) { Text(stringResource(R.string.home_today)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.year,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(PastelIcons.Back, contentDescription = stringResource(R.string.home_previous_month))
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(PastelIcons.Forward, contentDescription = stringResource(R.string.home_next_month))
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
                                hasAlarm = alarms.any { it.ringsOn(date, zone) },
                                holiday = IndianHolidays.on(date).firstOrNull(),
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
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<EventInfo>,
    hasAlarm: Boolean,
    holiday: Holiday?,
    onClick: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val grain = LocalCrayonGrain.current
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected && !reducedMotion) 1.035f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "calendar selection",
    )
    // A soft pastel per weekday (lighter than the fills, so the berry day stands out), cocoa text on every one.
    val crayons = listOf(
        Color(0xFFFFEEF3), Color(0xFFFFF0E2), Color(0xFFFFF7DA), Color(0xFFE9F8EF),
        Color(0xFFEAF3FF), Color(0xFFF3EEFF), Color(0xFFFFEEE5),
    )
    val fill = crayons[date.dayOfWeek.value % crayons.size]
    val people = events.flatMap { it.people }.distinctBy { it.id }
    val description = stringResource(R.string.home_day_description, date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)), events.size) +
        (holiday?.let { ", " + it.name } ?: "") + (if (hasAlarm) ", " + stringResource(R.string.home_day_has_alarm) else "")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .crayonSurface(
                fill = fill,
                outline = if (isSelected) crayon.red else Color.Transparent,
                seed = date.dayOfMonth,
                cornerRadius = 14.dp,
                strokeWidth = if (isSelected) 2.dp else 0.dp,
                grain = grain,
            )
            .clip(RoundedCornerShape(14.dp))
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
                // Holidays are red, like on a printed calendar.
                color = when {
                    isSelected -> crayon.onRed
                    holiday != null -> MeyouwwTheme.crayon.red
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        // Who marked it: up to three tiny faces, overlapping a little.
        Box(Modifier.height(16.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (people.isNotEmpty()) TinyFaces(people.take(3), extra = people.size - 3)
        }
        // What is on that day: a bell for a reminder, a clock for an alarm, a star for a holiday.
        val marks = (if (events.isNotEmpty()) "🔔" else "") + (if (hasAlarm) "⏰" else "") + (if (holiday != null) "🎉" else "")
        Text(
            marks,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            modifier = Modifier.height(12.dp).clearAndSetSemantics { },
        )
    }
}

/** True if this alarm rings on [date] (its own day, or one of its repeat days from then on). */
private fun AlarmInfo.ringsOn(date: LocalDate, zone: ZoneId): Boolean {
    val first = parseInstant(ringAt) ?: return false
    return AlarmTimes.ringsOn(first, repeatDays, date, zone)
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
    alarms: List<AlarmInfo>,
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
        CrayonButton(
            text = stringResource(R.string.home_add_event),
            onClick = onAdd,
            icon = PastelIcons.Plus,
        )
    }
    val crayon = MeyouwwTheme.crayon
    val holidays = IndianHolidays.on(date)
    val ringing = alarms.filter { it.ringsOn(date, zone) }.sortedBy { it.localTime }
    holidays.forEach { holiday ->
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = holiday.name.hashCode()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎉", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
                Column(Modifier.weight(1f)) {
                    Text(holiday.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (holiday.gazetted) R.string.home_holiday_public else R.string.home_holiday_festival),
                        style = MaterialTheme.typography.bodySmall,
                        color = crayon.textSoft,
                    )
                }
            }
        }
    }
    ringing.forEach { alarm ->
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.lilac, seed = alarm.id.hashCode()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⏰", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { })
                Column(Modifier.weight(1f)) {
                    Text(alarm.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val time = AlarmTimes.parseLocalTime(alarm.localTime)?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: alarm.localTime
                    Text(
                        stringResource(R.string.home_alarm_at, time) +
                            (if (alarm.repeatDays != 0) " · " + stringResource(R.string.home_alarm_repeats) else "") +
                            (if (!alarm.fromMe) " · " + stringResource(R.string.home_alarm_from, alarm.sender.name) else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = crayon.textSoft,
                    )
                }
            }
        }
    }
    if (today.isEmpty() && holidays.isEmpty() && ringing.isEmpty()) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 218) {
            Text(stringResource(R.string.home_day_empty), style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
        }
    }
    today.forEach { event -> EventRow(event, busy = event.id in busy, onRespond = onRespond, onCancel = onCancel) }
    if (today.any { !it.allDay }) {
        Text(stringResource(R.string.home_reminder_times), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }
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

/** What Whity tells you is coming up: the next event in the coming week, or null. */
@Composable
private fun whityNext(events: List<EventInfo>): String? {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val (date, event) = events
        .filter { !it.cancelled && it.myStatus != "declined" }
        .mapNotNull { e -> e.dateIn(zone)?.let { it to e } }
        .filter { (date, _) -> date >= today && date <= today.plusDays(WHITY_NEXT_DAYS) }
        .minByOrNull { (date, e) -> date.toEpochDay() * 100_000 + (e.start?.epochSecond ?: 0) % 100_000 }
        ?: return null
    val what = event.emoji + " " + event.title
    return when (val days = (date.toEpochDay() - today.toEpochDay()).toInt()) {
        0 -> stringResource(R.string.home_whity_next_today, what)
        1 -> stringResource(R.string.home_whity_next_tomorrow, what)
        else -> pluralStringResource(R.plurals.home_whity_next_days, days, what, days)
    }
}

/** How far ahead Whity looks for what's coming up. */
private const val WHITY_NEXT_DAYS = 7L

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
