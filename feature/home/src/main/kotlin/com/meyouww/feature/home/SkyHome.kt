package com.meyouww.feature.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.messageRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.meyouww.core.designsystem.component.CardLevel
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyFab
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.pet.WhityBuddy
import com.meyouww.core.designsystem.pet.WhityPetStore
import com.meyouww.core.designsystem.theme.LocalSky
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.AlarmTimes
import com.meyouww.core.model.CalendarGrid
import com.meyouww.core.model.DayPart
import com.meyouww.core.model.EventInfo
import com.meyouww.core.model.IndianHolidays
import com.meyouww.core.model.Profile
import com.meyouww.core.render.AvatarCrop
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.abs

/** Everything Home's Living Sky layout can do; the same actions as the other looks. */
internal class SkyHomeActions(
    val onPickPhoto: (Uri, AvatarCrop) -> Unit,
    val onRemovePhoto: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDeliverySetup: () -> Unit,
    val onAlarms: () -> Unit,
    val onAddEvent: (LocalDate) -> Unit,
    val onMonth: (YearMonth) -> Unit,
    val onSelect: (LocalDate) -> Unit,
    val onRespond: (EventInfo, Boolean) -> Unit,
    val onCancel: (EventInfo) -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Home in the Living Sky look, laid out as its design board: the greeting on the sky with the
 * inbox heart and your photo; Whity's stage (tap to pet her, hold her for her corner) with her
 * treats; the Today card, whose week strip opens into the month; the Alarms tile; what's coming
 * up as a row of cards; and "Add a plan" floating at the bottom.
 */
@Composable
internal fun SkyHomeScreen(
    state: HomeUiState,
    actions: SkyHomeActions,
    heart: @Composable () -> Unit,
    banner: @Composable () -> Unit,
) {
    val profile = state.profile
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MAX_CONTENT_WIDTH)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 92.dp),
        ) {
            SkyHomeBar(state, actions, heart)
            Box(Modifier.padding(horizontal = 16.dp)) { banner() }
            when {
                profile != null -> {
                    if (state.deliveryReady == false) {
                        SkyCard(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                            tone = SkyTone.Butter,
                            level = CardLevel.Raised,
                            padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            onClick = actions.onOpenDeliverySetup,
                        ) {
                            Text(stringResource(R.string.home_delivery_fix), style = SkyType.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        }
                    }
                    SkyStage(state)
                    TodayCard(state, actions)
                    AlarmsTile(state.alarms, actions.onAlarms)
                    ComingUp(state, actions.onSelect)
                }
                state.loading -> WhityLoading(stringResource(R.string.home_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
                else -> StickerMessage(
                    sticker = WhitySticker.OopsFacepalm,
                    title = stringResource(R.string.home_profile_error_title),
                    body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                    actionLabel = stringResource(R.string.home_retry),
                    onAction = actions.onRetry,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (profile != null) SkyFab(stringResource(R.string.home_sky_add_plan), SkyIcons.Plus, onClick = { actions.onAddEvent(state.selected) })
    }
}

/** The bar: "Good afternoon" and your name on the sky; the inbox heart; your photo (its menu). */
@Composable
private fun SkyHomeBar(state: HomeUiState, actions: SkyHomeActions, heart: @Composable () -> Unit) {
    val greeting = when (state.dayPart) {
        DayPart.MORNING -> R.string.home_hello_morning
        DayPart.AFTERNOON -> R.string.home_hello_afternoon
        DayPart.EVENING -> R.string.home_hello_evening
        DayPart.NIGHT -> R.string.home_hello_night
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(greeting).trimEnd { !it.isLetterOrDigit() }.trimEnd(), style = SkyType.sm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, shadow = skyHalo()), color = SkyInk.onSkySoft)
            Text(
                state.profile?.greetingName.orEmpty(),
                style = SkyType.h1.copy(shadow = skyHalo()),
                color = SkyInk.onSky,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        heart()
        SkyProfileCoin(state.profile, state.uploadingPhoto, actions)
    }
}

/** Your photo as a round coin; tap it for: change photo, remove photo, settings. */
@Composable
private fun SkyProfileCoin(profile: Profile?, uploading: Boolean, actions: SkyHomeActions) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var framing by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) framing = uri }
    framing?.let { uri ->
        AvatarCropDialog(uri = uri, onDismiss = { framing = null }, onDone = { crop ->
            framing = null
            actions.onPickPhoto(uri, crop)
        })
    }
    val label = stringResource(R.string.home_profile_menu)
    Box(Modifier.padding(bottom = 4.dp)) {
        Box(
            Modifier
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
                Box(Modifier.size(52.dp).background(Color.White.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("…", style = SkyType.h3)
                }
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
                    actions.onRemovePhoto()
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.home_settings)) }, onClick = {
                menu = false
                actions.onOpenSettings()
            })
        }
    }
}

/** Whity's stage: Whity on the right (her line pops up above her head), her treats and a hint on the left. */
@Composable
private fun SkyStage(state: HomeUiState) {
    val context = LocalContext.current
    val pet by remember(context) { WhityPetStore.state(context) }.collectAsState()
    Box(Modifier.fillMaxWidth().height(130.dp).zIndex(1f)) {
        if (pet.shown) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 22.dp),
            ) {
                SkyPill(pluralStringResource(R.plurals.home_sky_treats, pet.treats, pet.treats), tone = SkyTone.Butter, icon = SkyIcons.Cookie)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SpinRing()
                    Text(stringResource(R.string.home_sky_hint), style = SkyType.cap.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, shadow = skyHalo()), color = SkyInk.onSkySoft)
                }
            }
        }
        WhityBuddy(
            width = 136.dp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).offset(y = 14.dp),
            chatty = true,
            next = whityNext(state.events),
        )
    }
}

/** The little turning ring before the hint (.hint .dotr). */
@Composable
private fun SpinRing() {
    val sky = LocalSky.current
    val color = SkyInk.onSkySoft
    Box(
        Modifier
            .size(16.dp)
            .graphicsLayer { rotationZ = ((sky?.millis ?: 0L) % 2_400L) / 2_400f * 360f }
            .drawBehind {
                drawArc(color, -90f, 270f, false, style = Stroke(2.5.dp.toPx()))
            }
            .clearAndSetSemantics { },
    )
}

/** The Today card: its header (Today, the date, the month chip), the week strip or the whole month, and the day's rows. */
@Composable
private fun TodayCard(state: HomeUiState, actions: SkyHomeActions) {
    var month by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val selected = state.selected
    val monthOpen = stringResource(R.string.home_sky_month_open)
    val weekOnly = stringResource(R.string.home_sky_week_only)
    val today = LocalDate.now()
    SkyCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                if (selected == today) stringResource(R.string.home_today) else selected.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                style = SkyType.h3,
            )
            Text(
                selected.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)),
                style = SkyType.cap,
                color = MeyouwwTheme.crayon.textSoft,
                modifier = Modifier.weight(1f),
            )
            SkyChip(
                text = (if (month) state.month.month else selected.month).getDisplayName(TextStyle.FULL_STANDALONE, locale),
                trailingIcon = if (month) SkyIcons.Up else SkyIcons.Down,
                height = 36.dp,
                onClick = { month = !month },
                modifier = Modifier.semantics {
                    stateDescription = if (month) monthOpen else weekOnly
                },
            )
        }
        if (month) {
            MonthGrid(state, actions)
        } else {
            WeekStrip(selected, actions)
        }
        DayRows(state, actions)
    }
}

/** This week: seven day pills (letter over number); the chosen day is the raised accent pill. Swipe for other weeks. */
@Composable
private fun WeekStrip(selected: LocalDate, actions: SkyHomeActions) {
    val crayon = MeyouwwTheme.crayon
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val start = selected.with(TemporalAdjusters.previousOrSame(firstDay))
    val today = LocalDate.now()
    val prev = stringResource(R.string.home_sky_previous_week)
    val next = stringResource(R.string.home_sky_next_week)
    fun go(date: LocalDate) {
        if (YearMonth.from(date) != YearMonth.from(selected)) actions.onMonth(YearMonth.from(date))
        actions.onSelect(date)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .swipe(onLeft = { go(selected.plusWeeks(1)) }, onRight = { go(selected.minusWeeks(1)) })
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(prev) { go(selected.minusWeeks(1)); true },
                    CustomAccessibilityAction(next) { go(selected.plusWeeks(1)); true },
                )
            },
    ) {
        (0L until 7L).map { start.plusDays(it) }.forEach { day ->
            val on = day == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { translationY = if (on) -3.dp.toPx() else 0f }
                    .then(
                        if (on) {
                            Modifier.skySurface(crayon.red, crayon.berryDeep, Color.Transparent, 0f, cornerRadius = 18.dp, depth = 4.dp, belly = false)
                        } else if (day == today) {
                            Modifier.drawBehind { drawRoundRect(crayon.red, cornerRadius = CornerRadius(18.dp.toPx()), style = Stroke(2.dp.toPx())) }
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { go(day) }
                    .semantics {
                        this.selected = on
                        contentDescription = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                    }
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = SkyType.pill.copy(fontSize = 11.sp, lineHeight = 14.sp),
                    color = if (on) crayon.onRed else crayon.textSoft,
                )
                Text(day.dayOfMonth.toString(), style = SkyType.t.copy(lineHeight = 16.sp), color = if (on) crayon.onRed else SkyInk.ink)
            }
        }
    }
}

/** The whole month: day numbers in circles (the chosen one is the accent), little dots for plans and alarms, filter chips. */
@Composable
private fun MonthGrid(state: HomeUiState, actions: SkyHomeActions) {
    val crayon = MeyouwwTheme.crayon
    val locale = LocalConfiguration.current.locales[0]
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val zone = remember { ZoneId.systemDefault() }
    val byDate = remember(state.events) { CalendarGrid.byDate(state.events, zone) }
    val cells = remember(state.month, firstDay) { CalendarGrid.cells(state.month, firstDay) }
    val today = LocalDate.now()
    var showPlans by rememberSaveable { mutableStateOf(true) }
    var showAlarms by rememberSaveable { mutableStateOf(true) }
    var showHolidays by rememberSaveable { mutableStateOf(true) }
    val prev = stringResource(R.string.home_previous_month)
    val next = stringResource(R.string.home_next_month)
    Column(
        Modifier
            .fillMaxWidth()
            .swipe(onLeft = { actions.onMonth(state.month.plusMonths(1)) }, onRight = { actions.onMonth(state.month.minusMonths(1)) })
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(prev) { actions.onMonth(state.month.minusMonths(1)); true },
                    CustomAccessibilityAction(next) { actions.onMonth(state.month.plusMonths(1)); true },
                )
            },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + state.month.year,
            style = SkyType.cap,
            color = crayon.textSoft,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            CalendarGrid.weekdays(firstDay).forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, locale),
                    style = SkyType.pill.copy(fontSize = 11.sp, lineHeight = 18.sp),
                    color = crayon.textSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        cells.chunked(7).filter { week -> week.any { it != null } }.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val on = date == state.selected
                            val plan = showPlans && byDate[date].orEmpty().any { !it.cancelled }
                            val alarm = showAlarms && state.alarms.any { it.ringsOn(date, zone) }
                            val holiday = showHolidays && IndianHolidays.on(date).isNotEmpty()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .then(
                                        if (on) {
                                            Modifier.skySurface(crayon.red, crayon.berryDeep, Color.Transparent, 0f, cornerRadius = 17.dp, depth = 3.dp, belly = false)
                                        } else if (date == today) {
                                            Modifier.drawBehind { drawCircle(crayon.red, style = Stroke(2.dp.toPx())) }
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button) { actions.onSelect(date) }
                                    .semantics {
                                        this.selected = on
                                        contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
                                    },
                            ) {
                                Text(date.dayOfMonth.toString(), style = SkyType.t.copy(fontSize = 15.sp, lineHeight = 15.sp), color = if (on) crayon.onRed else SkyInk.ink)
                                // The dots under a day: plans (accent), alarms (blue), holidays (butter).
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = 1.dp),
                                ) {
                                    if (plan) Dot(if (on) crayon.onRed else crayon.red)
                                    if (alarm) Dot(Color(0xFF3F7FD0))
                                    if (holiday) Dot(Color(0xFFE0A21B))
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())) {
            SkyChip(stringResource(R.string.home_sky_filter_plans), selected = showPlans, onClick = { showPlans = !showPlans })
            SkyChip(stringResource(R.string.home_sky_filter_alarms), selected = showAlarms, onClick = { showAlarms = !showAlarms })
            SkyChip(stringResource(R.string.home_sky_filter_holidays), selected = showHolidays, onClick = { showHolidays = !showHolidays })
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
}

/** The chosen day's rows under the strip, each on a hairline: plans (with "I'm in"), holidays and alarms. */
@Composable
private fun DayRows(state: HomeUiState, actions: SkyHomeActions) {
    val zone = remember { ZoneId.systemDefault() }
    val date = state.selected
    val events = state.events.filter { !it.cancelled && it.myStatus != "declined" && it.dateIn(zone) == date }
    val holidays = IndianHolidays.on(date)
    val ringing = state.alarms.filter { it.ringsOn(date, zone) }.sortedBy { it.localTime }
    val crayon = MeyouwwTheme.crayon
    Column(Modifier.padding(top = 8.dp)) {
        if (events.isEmpty() && holidays.isEmpty() && ringing.isEmpty()) {
            HairlineRow { Text(stringResource(R.string.home_sky_day_empty), style = SkyType.sb, color = crayon.textSoft) }
        }
        events.forEach { event -> EventLine(event, busy = event.id in state.busy, actions = actions) }
        holidays.forEach { holiday ->
            HairlineRow {
                RowCoin(SkyIcons.Spark, SkyTone.Butter)
                Column(Modifier.weight(1f)) {
                    Text(holiday.name, style = SkyType.tt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (holiday.gazetted) R.string.home_holiday_public else R.string.home_holiday_festival),
                        style = SkyType.sb,
                        color = crayon.textSoft,
                    )
                }
            }
        }
        ringing.forEach { alarm ->
            HairlineRow {
                RowCoin(SkyIcons.Alarm, SkyTone.Blue)
                Column(Modifier.weight(1f)) {
                    Text(alarm.title, style = SkyType.tt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val time = AlarmTimes.parseLocalTime(alarm.localTime)?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) ?: alarm.localTime
                    Text(
                        time + (if (!alarm.fromMe) " · " + stringResource(R.string.home_alarm_from, alarm.sender.name) else ""),
                        style = SkyType.sb,
                        color = crayon.textSoft,
                    )
                }
            }
        }
    }
}

/** One plan: its coin, title, time and who; "I'm in" when you're invited (or going); a menu to cancel or leave. */
@Composable
private fun EventLine(event: EventInfo, busy: Boolean, actions: SkyHomeActions) {
    val crayon = MeyouwwTheme.crayon
    var menu by remember { mutableStateOf(false) }
    val canChange = event.mine || event.going || event.invited
    HairlineRow(onClick = if (canChange && !busy) ({ menu = true }) else null) {
        RowCoin(SkyIcons.Calendar, SkyTone.Peach, emoji = event.emoji)
        Column(Modifier.weight(1f)) {
            Text(event.title, style = SkyType.tt, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                eventWhen(event) + if (!event.mine) " · " + stringResource(R.string.home_event_invited, event.owner.name) else "",
                style = SkyType.sb,
                color = crayon.textSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            event.invited -> SkyPill(
                stringResource(R.string.home_sky_im_in),
                tone = SkyTone.Mint,
                onClick = if (busy) null else ({ actions.onRespond(event, true) }),
            )
            event.going && !event.mine -> SkyPill(stringResource(R.string.home_sky_im_in), tone = SkyTone.Mint)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (event.invited) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_sky_im_in)) }, onClick = {
                    menu = false
                    actions.onRespond(event, true)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.home_event_not_going)) }, onClick = {
                    menu = false
                    actions.onRespond(event, false)
                })
            } else if (event.mine) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_event_cancel)) }, onClick = {
                    menu = false
                    actions.onCancel(event)
                })
            } else if (event.going) {
                DropdownMenuItem(text = { Text(stringResource(R.string.home_event_leave)) }, onClick = {
                    menu = false
                    actions.onRespond(event, false)
                })
            }
        }
    }
}

/** A row inside a card with a hairline above it (.r3 with border-top), 66 dp tall at least. */
@Composable
private fun HairlineRow(onClick: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val line = MeyouwwTheme.crayon.line
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 66.dp)
            .padding(vertical = 9.dp),
        content = content,
    )
}

/** A small round coin (.a3.s) with an icon, or the plan's own emoji when it has one. */
@Composable
private fun RowCoin(icon: androidx.compose.ui.graphics.vector.ImageVector, tone: SkyTone, emoji: String? = null) {
    val crayon = MeyouwwTheme.crayon
    val face = tone.fill()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .skySurface(face, crayon.pressShadowFor(face), crayon.shadow, crayon.sunX * 0.5f, cornerRadius = 19.dp, depth = 3.dp, lift = 6.dp, blur = 10.dp)
            .clearAndSetSemantics { },
    ) {
        if (emoji.isNullOrBlank()) {
            Icon(icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(20.dp))
        } else {
            Text(emoji, fontSize = 18.sp, lineHeight = 18.sp)
        }
    }
}

/** The Alarms tile (.c3.bl.qt): a pastel blue card with the next alarm; tap to open Alarms. */
@Composable
private fun AlarmsTile(alarms: List<AlarmInfo>, onAlarms: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val next = remember(alarms) { nextRing(alarms, zone) }
    val sub = next?.let { (at, alarm) ->
        val time = at.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        if (alarm.fromMe) {
            stringResource(R.string.home_sky_alarm_next, time, alarm.title)
        } else {
            stringResource(R.string.home_sky_alarm_next_from, time, alarm.title, alarm.sender.name)
        }
    } ?: stringResource(R.string.home_sky_alarms_body)
    SkyCard(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth(),
        tone = SkyTone.Blue,
        padding = PaddingValues(12.dp),
        corner = 24.dp,
        onClick = onAlarms,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(SkyIcons.Alarm, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_sky_alarms), style = SkyType.t.copy(lineHeight = 20.sp), color = SkyInk.onPastel)
                Text(sub, style = SkyType.cap.copy(fontSize = 12.sp, lineHeight = 16.sp), color = SkyInk.onPastel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(SkyIcons.Next, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(20.dp))
        }
    }
}

/** When the next of these alarms rings (within the coming week), and which one. */
private fun nextRing(alarms: List<AlarmInfo>, zone: ZoneId): Pair<LocalDateTime, AlarmInfo>? {
    val now = LocalDateTime.now(zone)
    var best: Pair<LocalDateTime, AlarmInfo>? = null
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        alarms.forEach { alarm ->
            if (!alarm.ringsOn(date, zone)) return@forEach
            val time = AlarmTimes.parseLocalTime(alarm.localTime) ?: return@forEach
            val at = date.atTime(time)
            if (at.isAfter(now) && (best == null || at.isBefore(best!!.first))) best = at to alarm
        }
        if (best != null) return best
    }
    return best
}

/** One thing coming up: when, what, and a line under it. */
private class Upcoming(val date: LocalDate, val sortKey: Long, val tone: SkyTone, val event: EventInfo? = null, val alarm: AlarmInfo? = null, val holiday: com.meyouww.core.model.Holiday? = null)

/**
 * What's coming up, as a row of little cards (.cu), all the same height: plans (pink), alarms
 * (blue) and holidays (butter) in the next month. The first floats higher. Tap one to see its day.
 */
@Composable
private fun ComingUp(state: HomeUiState, onSelect: (LocalDate) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val locale = LocalConfiguration.current.locales[0]
    val items = remember(state.events, state.alarms, today) {
        val plans = state.events
            .filter { !it.cancelled && it.myStatus != "declined" }
            .mapNotNull { e -> e.dateIn(zone)?.let { d -> Upcoming(d, e.start?.epochSecond ?: 0L, SkyTone.Pink, event = e) } }
        val alarms = state.alarms.mapNotNull { a ->
            val time = AlarmTimes.parseLocalTime(a.localTime) ?: return@mapNotNull null
            (1L..30L).map { today.plusDays(it) }.firstOrNull { a.ringsOn(it, zone) }
                ?.let { d -> Upcoming(d, d.atTime(time).atZone(zone).toEpochSecond(), SkyTone.Blue, alarm = a) }
        }
        val holidays = (1L..30L).flatMap { n ->
            val d = today.plusDays(n)
            IndianHolidays.on(d).map { Upcoming(d, d.atStartOfDay(zone).toEpochSecond(), SkyTone.Butter, holiday = it) }
        }
        (plans + alarms + holidays)
            .filter { it.date > today && it.date <= today.plusDays(30) }
            .sortedBy { it.sortKey }
            .take(6)
    }
    if (items.isEmpty()) return
    val thisMonth = items.all { YearMonth.from(it.date) == YearMonth.from(today) }
    SkySectionHeading(
        stringResource(R.string.home_upcoming),
        caption = stringResource(if (thisMonth) R.string.home_sky_this_month else R.string.home_sky_next_weeks),
        modifier = Modifier.padding(top = 14.dp),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 16.dp),
    ) {
        items.forEachIndexed { i, item ->
            val day = if (item.date == today.plusDays(1)) stringResource(R.string.home_sky_tomorrow) else item.date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
            val time = when {
                item.event != null && !item.event.allDay -> eventWhen(item.event)
                item.alarm != null -> AlarmTimes.parseLocalTime(item.alarm.localTime)?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                else -> null
            }
            val title = item.event?.title ?: item.alarm?.title ?: item.holiday?.name.orEmpty()
            val sub = when {
                item.event != null && item.event.mine -> stringResource(R.string.home_sky_your_plan)
                item.event != null -> stringResource(R.string.home_event_invited, item.event.owner.name)
                item.alarm != null && item.alarm.fromMe -> stringResource(R.string.home_sky_alarm_for, item.alarm.recipient.name)
                item.alarm != null -> stringResource(R.string.home_sky_alarm_from, item.alarm.sender.name)
                else -> stringResource(R.string.home_sky_holiday)
            }
            SkyCard(
                modifier = Modifier.width(150.dp).fillMaxHeight(),
                tone = item.tone,
                level = if (i == 0) CardLevel.Raised else CardLevel.Normal,
                padding = PaddingValues(12.dp),
                corner = 24.dp,
                onClick = { onSelect(item.date) },
                onClickLabel = title,
            ) {
                Text(
                    (if (time != null) "$day · $time" else day).uppercase(locale),
                    style = SkyType.cap,
                    color = Color(0xFF5E4853),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(title, style = SkyType.t, color = SkyInk.onPastel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub, style = SkyType.sm, color = SkyInk.onPastel, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Horizontal swipes (a finger's width or more) to the left or right. */
@Composable
private fun Modifier.swipe(onLeft: () -> Unit, onRight: () -> Unit): Modifier {
    val left by rememberUpdatedState(onLeft)
    val right by rememberUpdatedState(onRight)
    return this.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (abs(total) > 48.dp.toPx()) {
                    if (total < 0) left() else right()
                }
            },
            onHorizontalDrag = { change, amount ->
                change.consume()
                total += amount
            },
        )
    }
}
