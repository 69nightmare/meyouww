package com.meyouww.feature.friends

import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyDivider
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.CardLevel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.ContactPermissions
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperMode
import com.meyouww.core.model.parseInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

data class PermissionsUiState(
    val loading: Boolean = true,
    val name: String = "",
    val level: RelationshipLevel = RelationshipLevel.Friend,
    val permissions: ContactPermissions? = null,
    val saving: Boolean = false,
    val message: Int? = null,
    val unavailable: Boolean = false,
)

@HiltViewModel
class ContactPermissionsViewModel @Inject constructor(
    private val repo: FriendsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PermissionsUiState())
    val state: StateFlow<PermissionsUiState> = _state.asStateFlow()
    private var contactId: String? = null

    fun load(id: String) {
        contactId = id
        val name = repo.friends.value?.firstOrNull { it.id == id }?.card?.name.orEmpty()
        _state.update { it.copy(name = name) }
        viewModelScope.launch {
            when (val outcome = repo.permissions(id)) {
                is Outcome.Success -> {
                    val snapshot = outcome.value
                    _state.update {
                        if (snapshot == null) {
                            it.copy(loading = false, unavailable = true)
                        } else {
                            it.copy(loading = false, level = snapshot.level, permissions = snapshot.permissions)
                        }
                    }
                }
                is Outcome.Failure -> _state.update { it.copy(loading = false, message = outcome.error.messageRes()) }
            }
        }
    }

    fun setBool(key: String, value: Boolean) = change(buildJsonObject { put(key, value) })

    fun setMode(mode: WallpaperMode, pauseFor: Duration? = null) = change(
        buildJsonObject {
            put("wallpaper_mode", mode.wire)
            if (pauseFor != null) put("paused_until", Instant.now().plus(pauseFor).toString())
        },
    )

    fun setLimit(limit: Int) = change(buildJsonObject { put("wallpaper_daily_limit", limit.coerceIn(1, 20)) })

    fun setQuietHours(start: String?, end: String?) = change(
        buildJsonObject {
            put("quiet_start", start?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            put("quiet_end", end?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        },
    )

    fun setQuietBehavior(hold: Boolean) = change(buildJsonObject { put("quiet_behavior", if (hold) "hold" else "silent") })

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun change(changes: JsonObject) {
        val id = contactId ?: return
        val current = _state.value.permissions ?: return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            when (val outcome = repo.updatePermissions(id, changes, current.version)) {
                is Outcome.Success -> {
                    val update = outcome.value
                    _state.update {
                        it.copy(
                            saving = false,
                            permissions = update.permissions ?: it.permissions,
                            message = when (update.result) {
                                ChangeResult.Ok -> R.string.perm_saved
                                ChangeResult.Invalid -> R.string.perm_invalid
                                else -> resultMessage(update.result)
                            },
                        )
                    }
                    if (update.result == ChangeResult.Ok) repo.refreshFriends()
                }
                is Outcome.Failure -> _state.update { it.copy(saving = false, message = outcome.error.messageRes()) }
            }
        }
    }
}

@Composable
fun ContactPermissionsScreen(
    contactId: String,
    onBack: () -> Unit,
    viewModel: ContactPermissionsViewModel = hiltViewModel(),
) {
    LaunchedEffect(contactId) { viewModel.load(contactId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var showAutoSheet by rememberSaveable { mutableStateOf(false) }
    var showSpecsSheet by rememberSaveable { mutableStateOf(false) }
    var showPauseChoice by rememberSaveable { mutableStateOf(false) }
    var showNotificationAsk by rememberSaveable { mutableStateOf(false) }
    var editingTime by rememberSaveable { mutableStateOf<String?>(null) } // "start" or "end"

    CrayonScreen(title = stringResource(R.string.perm_title), onBack = onBack) {
        val p = state.permissions
        if (p == null) {
            if (state.loading) {
                WhityLoading(stringResource(R.string.friends_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            } else {
                StickerMessage(WhitySticker.OopsFacepalm, title = stringResource(R.string.detail_not_found), body = state.message?.let { stringResource(it) }.orEmpty())
            }
            return@CrayonScreen
        }
        val name = state.name.ifBlank { stringResource(R.string.level_friend) }
        if (crayon.living) {
            SkyPermissions(
                p = p,
                name = name,
                state = state,
                viewModel = viewModel,
                onAuto = { if (!notificationsAllowed(context)) showNotificationAsk = true else showAutoSheet = true },
                onPause = { showPauseChoice = true },
                onSpecs = { on -> if (on) showSpecsSheet = true else viewModel.setBool("share_screen_specs", false) },
                onEditTime = { editingTime = it },
            )
            return@CrayonScreen
        }
        Text(
            stringResource(
                R.string.perm_intro,
                name,
                stringResource(R.string.capability_auto),
                stringResource(R.string.capability_ask),
                stringResource(R.string.capability_unavailable),
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        state.message?.let {
            CrayonChip(
                text = stringResource(it),
                fill = if (it == R.string.perm_saved) crayon.green else crayon.peach,
                onClick = viewModel::messageShown,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 21) {
            Text(stringResource(R.string.perm_wallpaper), style = MaterialTheme.typography.titleLarge)
            SwitchRow(stringResource(R.string.perm_home), p.wallpaperHome, { viewModel.setBool("wallpaper_home", it) }, enabled = !state.saving)
            SwitchRow(stringResource(R.string.perm_lock), p.wallpaperLock, { viewModel.setBool("wallpaper_lock", it) }, enabled = !state.saving)
            SwitchRow(stringResource(R.string.perm_doodles), p.doodleWallpapers, { viewModel.setBool("doodle_wallpapers", it) }, enabled = !state.saving)

            Text(stringResource(R.string.perm_mode), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Column(Modifier.selectableGroup()) {
                ModeRow(stringResource(R.string.perm_mode_ask), stringResource(R.string.perm_mode_ask_detail), p.mode == WallpaperMode.Ask, enabled = !state.saving) {
                    viewModel.setMode(WallpaperMode.Ask)
                }
                // Only a girlfriend / boyfriend's wallpapers go on by themselves.
                ModeRow(
                    stringResource(R.string.perm_mode_auto),
                    stringResource(if (state.level.isPartner) R.string.perm_mode_auto_detail else R.string.perm_needs_closer),
                    p.mode == WallpaperMode.Auto && state.level.isPartner,
                    enabled = !state.saving && state.level.isPartner,
                ) {
                    if (!notificationsAllowed(context)) {
                        showNotificationAsk = true
                    } else {
                        showAutoSheet = true
                    }
                }
                val pausedDetail = pausedUntilText(p.pausedUntil)?.let { stringResource(R.string.perm_mode_paused_until, it) }
                ModeRow(stringResource(R.string.perm_mode_paused), pausedDetail, p.mode == WallpaperMode.Paused, enabled = !state.saving) {
                    showPauseChoice = true
                }
                ModeRow(stringResource(R.string.perm_mode_never), null, p.mode == WallpaperMode.Never, enabled = !state.saving) {
                    viewModel.setMode(WallpaperMode.Never)
                }
            }

            var limit by remember(p.wallpaperDailyLimit) { mutableFloatStateOf(p.wallpaperDailyLimit.toFloat()) }
            Text(stringResource(R.string.perm_limit, limit.toInt()), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = limit,
                onValueChange = { limit = it },
                onValueChangeFinished = { viewModel.setLimit(limit.toInt()) },
                valueRange = 1f..20f,
                steps = 18,
                enabled = !state.saving,
            )

            SwitchRow(
                title = stringResource(R.string.perm_quiet),
                detail = if (p.hasQuietHours) stringResource(R.string.perm_quiet_detail, p.quietStart.orEmpty(), p.quietEnd.orEmpty()) else stringResource(R.string.perm_quiet_off),
                checked = p.hasQuietHours,
                enabled = !state.saving,
                onCheckedChange = { on -> if (on) viewModel.setQuietHours("22:00", "07:00") else viewModel.setQuietHours(null, null) },
            )
            if (p.hasQuietHours) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrayonButton("${stringResource(R.string.perm_quiet_start)} ${p.quietStart}", onClick = { editingTime = "start" }, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                    CrayonButton("${stringResource(R.string.perm_quiet_end)} ${p.quietEnd}", onClick = { editingTime = "end" }, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                }
                Column(Modifier.selectableGroup()) {
                    ModeRow(stringResource(R.string.perm_quiet_hold), null, p.quietBehavior == "hold", enabled = !state.saving) { viewModel.setQuietBehavior(true) }
                    ModeRow(stringResource(R.string.perm_quiet_silent), null, p.quietBehavior == "silent", enabled = !state.saving) { viewModel.setQuietBehavior(false) }
                }
            }
            SwitchRow(stringResource(R.string.perm_receipts), p.receipts, { viewModel.setBool("receipts", it) }, enabled = !state.saving)
        }

        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 20, fill = crayon.yellow) {
            Text(stringResource(R.string.perm_levels_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.perm_levels_body), style = MaterialTheme.typography.bodyMedium)
        }

        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 22) {
            if (state.level.isPartner) {
                SwitchRow(
                    title = stringResource(R.string.perm_ringer),
                    detail = stringResource(R.string.perm_ringer_detail),
                    checked = p.ringerAllowed,
                    enabled = !state.saving,
                    onCheckedChange = { viewModel.setBool("ringer_allowed", it) },
                )
            }
            SwitchRow(
                title = stringResource(R.string.perm_reminders),
                detail = stringResource(R.string.perm_reminders_detail),
                checked = p.reminderSuggestions,
                enabled = !state.saving,
                onCheckedChange = { viewModel.setBool("reminder_suggestions", it) },
            )
            SwitchRow(
                title = stringResource(R.string.perm_alarms),
                detail = stringResource(R.string.perm_alarms_detail),
                checked = p.alarmRequests,
                enabled = !state.saving,
                onCheckedChange = { viewModel.setBool("alarm_requests", it) },
            )
            SwitchRow(
                title = stringResource(R.string.perm_specs),
                detail = stringResource(R.string.perm_specs_detail),
                checked = p.shareScreenSpecs,
                enabled = !state.saving,
                onCheckedChange = { on ->
                    if (on) {
                        showSpecsSheet = true
                    } else {
                        viewModel.setBool("share_screen_specs", false)
                    }
                },
            )
        }
    }

    val name = state.name
    if (showAutoSheet) {
        ConfirmDialog(
            title = stringResource(R.string.perm_auto_sheet_title, name),
            body = stringResource(R.string.perm_auto_sheet_body, name),
            confirmLabel = stringResource(R.string.perm_auto_sheet_confirm),
            onDismiss = { showAutoSheet = false },
            onConfirm = {
                showAutoSheet = false
                viewModel.setMode(WallpaperMode.Auto)
            },
        )
    }
    if (showSpecsSheet) {
        ConfirmDialog(
            title = stringResource(R.string.perm_specs_sheet_title, name),
            body = stringResource(R.string.perm_specs_sheet_body),
            confirmLabel = stringResource(R.string.perm_specs_sheet_confirm),
            onDismiss = { showSpecsSheet = false },
            onConfirm = {
                showSpecsSheet = false
                viewModel.setBool("share_screen_specs", true)
            },
        )
    }
    if (showNotificationAsk) {
        NotificationExplainer(onDone = { showNotificationAsk = false })
    }
    if (showPauseChoice) {
        PauseDialog(
            onDismiss = { showPauseChoice = false },
            onPick = { duration ->
                showPauseChoice = false
                viewModel.setMode(WallpaperMode.Paused, duration)
            },
        )
    }
    editingTime?.let { which ->
        val p = state.permissions
        TimeDialog(
            initial = if (which == "start") p?.quietStart else p?.quietEnd,
            onDismiss = { editingTime = null },
            onPick = { time ->
                editingTime = null
                if (which == "start") viewModel.setQuietHours(time, p?.quietEnd ?: "07:00") else viewModel.setQuietHours(p?.quietStart ?: "22:00", time)
            },
        )
    }
}

@Composable
private fun ModeRow(title: String, detail: String?, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
        }
    }
}

@Composable
private fun PauseDialog(onDismiss: () -> Unit, onPick: (Duration) -> Unit) {
    val choices = listOf(
        R.string.perm_pause_1h to Duration.ofHours(1),
        R.string.perm_pause_8h to Duration.ofHours(8),
        R.string.perm_pause_1d to Duration.ofDays(1),
        R.string.perm_pause_1w to Duration.ofDays(7),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.perm_pause_for)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (label, duration) ->
                    CrayonButton(stringResource(label), onClick = { onPick(duration) }, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val parts = initial?.split(":")?.mapNotNull { it.toIntOrNull() }.orEmpty()
    val picker = rememberTimePickerState(
        initialHour = parts.getOrElse(0) { 22 },
        initialMinute = parts.getOrElse(1) { 0 },
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = picker) },
        confirmButton = {
            TextButton(onClick = { onPick(String.format(Locale.ROOT, "%02d:%02d", picker.hour, picker.minute)) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun pausedUntilText(value: String?): String? =
    parseInstant(value)?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))

/**
 * "Decorating my phone" in the Living Sky look, as on the board: their coin with what they may do;
 * where (home, lock, doodles) as chips; what happens when they send a wallpaper as four choice
 * cards; the daily limit and quiet hours; and the rest of what they may do as switches.
 */
@Composable
private fun SkyPermissions(
    p: ContactPermissions,
    name: String,
    state: PermissionsUiState,
    viewModel: ContactPermissionsViewModel,
    onAuto: () -> Unit,
    onPause: () -> Unit,
    onSpecs: (Boolean) -> Unit,
    onEditTime: (String) -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val busy = state.saving
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
        Box(
            Modifier.drawBehind {
                val w = 3.dp.toPx()
                drawCircle(crayon.red, radius = size.minDimension / 2 + 3.dp.toPx() + w / 2, style = Stroke(w))
            },
        ) { PersonAvatar(name.take(1).uppercase(), state.name.hashCode(), size = 68.dp) }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.perm_sky_may_do, name), style = SkyType.h3.copy(shadow = skyHalo()), color = SkyInk.onSky)
            Text(
                stringResource(R.string.perm_intro, name, stringResource(R.string.capability_auto), stringResource(R.string.capability_ask), stringResource(R.string.capability_unavailable)),
                style = SkyType.sm.copy(shadow = skyHalo()),
                color = SkyInk.onSkySoft,
            )
        }
    }
    state.message?.let {
        SkyChip(
            stringResource(it),
            tone = if (it == R.string.perm_saved) SkyTone.Mint else SkyTone.Peach,
            icon = if (it == R.string.perm_saved) SkyIcons.Check else null,
            onClick = viewModel::messageShown,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    SkySectionHeading(stringResource(R.string.perm_sky_where), inset = 2.dp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        SkyChip(stringResource(R.string.perm_sky_home), selected = p.wallpaperHome, onClick = if (busy) null else ({ viewModel.setBool("wallpaper_home", !p.wallpaperHome) }))
        SkyChip(stringResource(R.string.perm_sky_lock), selected = p.wallpaperLock, onClick = if (busy) null else ({ viewModel.setBool("wallpaper_lock", !p.wallpaperLock) }))
        SkyChip(stringResource(R.string.perm_sky_doodles), selected = p.doodleWallpapers, onClick = if (busy) null else ({ viewModel.setBool("doodle_wallpapers", !p.doodleWallpapers) }))
    }
    SkySectionHeading(stringResource(R.string.perm_sky_when, name), inset = 2.dp)
    val pausedDetail = pausedUntilText(p.pausedUntil)?.let { stringResource(R.string.perm_mode_paused_until, it) }
    val choices = listOf(
        Triple(stringResource(R.string.perm_mode_ask), stringResource(R.string.perm_mode_ask_detail), WallpaperMode.Ask),
        Triple(stringResource(R.string.perm_mode_auto), stringResource(if (state.level.isPartner) R.string.perm_mode_auto_detail else R.string.perm_needs_closer), WallpaperMode.Auto),
        Triple(stringResource(R.string.perm_mode_paused), pausedDetail ?: stringResource(R.string.perm_sky_pick_how_long), WallpaperMode.Paused),
        Triple(stringResource(R.string.perm_mode_never), stringResource(R.string.perm_sky_never_detail), WallpaperMode.Never),
    )
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        choices.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                row.forEach { (title, detail, mode) ->
                    val on = p.mode == mode && (mode != WallpaperMode.Auto || state.level.isPartner)
                    val enabled = !busy && (mode != WallpaperMode.Auto || state.level.isPartner)
                    SkyCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 106.dp)
                            .selectable(selected = on, enabled = enabled, role = Role.RadioButton) {
                                when (mode) {
                                    WallpaperMode.Auto -> onAuto()
                                    WallpaperMode.Paused -> onPause()
                                    else -> viewModel.setMode(mode)
                                }
                            }
                            .graphicsLayer { alpha = if (enabled || on) 1f else 0.6f },
                        tone = if (on) SkyTone.Pink else null,
                        level = if (on) CardLevel.Raised else CardLevel.Normal,
                        padding = PaddingValues(12.dp),
                        corner = 22.dp,
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = if (on) 24.dp else 0.dp)) {
                                Text(title, style = SkyType.t, color = if (on) SkyInk.onPastel else MaterialTheme.colorScheme.onSurface)
                                Text(detail, style = SkyType.hp, color = if (on) SkyInk.onPastel else crayon.textSoft)
                            }
                            if (on) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(crayon.red, CircleShape),
                                ) { Icon(SkyIcons.Check, contentDescription = null, tint = crayon.onRed, modifier = Modifier.size(15.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
    SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp)) {
            val limit = p.wallpaperDailyLimit
            Text(stringResource(R.string.perm_sky_limit, limit), style = SkyType.tt, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            SkyIconButton(SkyIcons.Minus, stringResource(R.string.perm_sky_fewer), if (!busy && limit > 1) ({ viewModel.setLimit(limit - 1) }) else null, size = SkyIconSize.Small)
            Text(limit.toString(), style = SkyType.t, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 26.dp))
            SkyIconButton(SkyIcons.Plus, stringResource(R.string.perm_sky_more_button), if (!busy && limit < 20) ({ viewModel.setLimit(limit + 1) }) else null, size = SkyIconSize.Small)
        }
        SkyDivider()
        SwitchRow(
            title = stringResource(R.string.perm_quiet),
            detail = if (p.hasQuietHours) stringResource(R.string.perm_quiet_detail, p.quietStart.orEmpty(), p.quietEnd.orEmpty()) else stringResource(R.string.perm_quiet_off),
            checked = p.hasQuietHours,
            enabled = !busy,
            onCheckedChange = { on -> if (on) viewModel.setQuietHours("22:00", "07:00") else viewModel.setQuietHours(null, null) },
        )
        if (p.hasQuietHours) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp).horizontalScroll(rememberScrollState())) {
                SkyChip("${stringResource(R.string.perm_quiet_start)} ${p.quietStart}", height = 36.dp, onClick = { onEditTime("start") })
                SkyChip("${stringResource(R.string.perm_quiet_end)} ${p.quietEnd}", height = 36.dp, onClick = { onEditTime("end") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp).selectableGroup().horizontalScroll(rememberScrollState())) {
                SkyChip(stringResource(R.string.perm_quiet_hold), selected = p.quietBehavior == "hold", height = 36.dp, onClick = if (busy) null else ({ viewModel.setQuietBehavior(true) }))
                SkyChip(stringResource(R.string.perm_quiet_silent), selected = p.quietBehavior == "silent", height = 36.dp, onClick = if (busy) null else ({ viewModel.setQuietBehavior(false) }))
            }
        }
        SkyDivider()
        SwitchRow(stringResource(R.string.perm_receipts), p.receipts, { viewModel.setBool("receipts", it) }, enabled = !busy)
    }
    SkySectionHeading(stringResource(R.string.perm_sky_more_from, name), inset = 2.dp)
    SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        SwitchRow(
            title = stringResource(R.string.perm_alarms),
            detail = stringResource(R.string.perm_alarms_detail),
            checked = p.alarmRequests,
            enabled = !busy,
            onCheckedChange = { viewModel.setBool("alarm_requests", it) },
        )
        if (state.level.isPartner) {
            SkyDivider()
            SwitchRow(
                title = stringResource(R.string.perm_ringer),
                detail = stringResource(R.string.perm_ringer_detail),
                checked = p.ringerAllowed,
                enabled = !busy,
                onCheckedChange = { viewModel.setBool("ringer_allowed", it) },
            )
        }
        SkyDivider()
        SwitchRow(
            title = stringResource(R.string.perm_reminders),
            detail = stringResource(R.string.perm_reminders_detail),
            checked = p.reminderSuggestions,
            enabled = !busy,
            onCheckedChange = { viewModel.setBool("reminder_suggestions", it) },
        )
        SkyDivider()
        SwitchRow(
            title = stringResource(R.string.perm_specs),
            detail = stringResource(R.string.perm_specs_detail),
            checked = p.shareScreenSpecs,
            enabled = !busy,
            onCheckedChange = onSpecs,
        )
    }
    Text(stringResource(R.string.perm_levels_body), style = SkyType.hp.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
}
