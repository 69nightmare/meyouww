package com.meyouww.feature.friends

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
                StickerMessage(WhitySticker.Confused, title = stringResource(R.string.detail_not_found), body = state.message?.let { stringResource(it) }.orEmpty())
            }
            return@CrayonScreen
        }
        val name = state.name.ifBlank { stringResource(R.string.level_friend) }
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
                ModeRow(
                    stringResource(R.string.perm_mode_auto),
                    stringResource(if (state.level.allowsAutomatic) R.string.perm_mode_auto_detail else R.string.perm_needs_closer),
                    p.mode == WallpaperMode.Auto,
                    enabled = !state.saving && state.level.allowsAutomatic,
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

        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 22) {
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
