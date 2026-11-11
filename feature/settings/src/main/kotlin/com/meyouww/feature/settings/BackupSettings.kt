package com.meyouww.feature.settings

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.chat.BackupInfo
import com.meyouww.core.chat.BackupResult
import com.meyouww.core.chat.BackupState
import com.meyouww.core.chat.ChatBackup
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.DriveAccess
import com.meyouww.core.chat.RestoreResult
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.notifications.chat.ChatBackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** A line to show under the backup buttons: a string and what goes into it. */
data class BackupMessage(val res: Int, val args: List<Any> = emptyList(), val error: Boolean = false)

data class BackupUi(
    /** What is in Google Drive (null: none, or not looked yet; see [looked]). */
    val inDrive: BackupInfo? = null,
    val looked: Boolean = false,
    val busy: Boolean = false,
    val message: BackupMessage? = null,
)

/**
 * The chat backup card in Settings and the "bring your chats back?" question on a new phone.
 * Everything that needs Google Drive first asks Google for the person's OK; the first time,
 * that opens Google's own consent screen.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backup: ChatBackup,
    private val auth: AuthRepository,
    private val chat: ChatRepository,
) : ViewModel() {
    private enum class Action { Look, BackUp, Restore, Nightly }

    val state: StateFlow<BackupState> = backup.state

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    /** Google's consent screen to launch, when it is needed. */
    private val _consent = MutableStateFlow<PendingIntent?>(null)
    val consent: StateFlow<PendingIntent?> = _consent.asStateFlow()
    private var waiting: Action? = null

    private val _offerRestore = MutableStateFlow(false)

    /** True once on a phone with no chats: offer to restore them. */
    val offerRestore: StateFlow<Boolean> = _offerRestore.asStateFlow()

    init {
        viewModelScope.launch { _offerRestore.value = backup.shouldOfferRestore() }
    }

    fun restoreOffered() {
        backup.restoreOffered()
        _offerRestore.value = false
    }

    fun look() = start(Action.Look)

    fun backUpNow() = start(Action.BackUp)

    fun restore() = start(Action.Restore)

    fun setNightly(on: Boolean) {
        if (on) {
            start(Action.Nightly)
        } else {
            backup.setNightly(false)
            ChatBackupWorker.cancelNightly(context)
        }
    }

    fun setWifiOnly(on: Boolean) {
        backup.setWifiOnly(on)
        if (state.value.nightly) ChatBackupWorker.scheduleNightly(context, on)
    }

    fun setIncludeMedia(on: Boolean) = backup.setIncludeMedia(on)

    private fun start(action: Action) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            when (val access = DriveAccess.authorize(context, auth.currentEmail())) {
                is DriveAccess.Result.Token -> perform(action, access.token)
                is DriveAccess.Result.NeedsConsent -> {
                    waiting = action
                    _consent.value = access.intent
                }
                DriveAccess.Result.Unavailable -> _ui.update { it.copy(busy = false, message = BackupMessage(R.string.backup_unavailable, error = true)) }
            }
        }
    }

    fun consentLaunched() {
        _consent.value = null
    }

    /** Back from Google's consent screen. */
    fun onConsent(data: Intent?) {
        val action = waiting
        waiting = null
        val token = DriveAccess.tokenFrom(context, data)
        if (action == null || token == null) {
            _ui.update { it.copy(busy = false, message = BackupMessage(R.string.backup_declined, error = true)) }
        } else {
            viewModelScope.launch { perform(action, token) }
        }
    }

    private suspend fun perform(action: Action, token: String) {
        val message: BackupMessage? = when (action) {
            Action.Look -> null
            Action.Nightly -> {
                backup.setNightly(true)
                ChatBackupWorker.scheduleNightly(context, state.value.wifiOnly)
                BackupMessage(R.string.backup_nightly_on)
            }
            Action.BackUp -> when (val result = backup.backUp(token)) {
                is BackupResult.Done -> BackupMessage(R.string.backup_done, listOf(result.messages, result.files))
                BackupResult.DriveDenied -> BackupMessage(R.string.backup_denied, error = true)
                BackupResult.Offline -> BackupMessage(R.string.backup_offline, error = true)
                BackupResult.Failed -> BackupMessage(R.string.backup_failed, error = true)
            }
            Action.Restore -> when (val result = backup.restore(token)) {
                is RestoreResult.Done -> {
                    // Group names and members, and the files, follow in the background.
                    chat.refreshGroups()
                    chat.syncNicknameBook()
                    if (result.filesWaiting > 0) ChatBackupWorker.restoreMedia(context)
                    if (result.filesWaiting > 0) {
                        BackupMessage(R.string.restore_done_files, listOf(result.messages, result.filesWaiting))
                    } else {
                        BackupMessage(R.string.restore_done, listOf(result.messages))
                    }
                }
                RestoreResult.NoBackup -> BackupMessage(R.string.restore_none, error = true)
                RestoreResult.WrongAccount -> BackupMessage(R.string.restore_wrong, error = true)
                RestoreResult.DriveDenied -> BackupMessage(R.string.backup_denied, error = true)
                RestoreResult.Offline -> BackupMessage(R.string.backup_offline, error = true)
                RestoreResult.Failed -> BackupMessage(R.string.restore_failed, error = true)
            }
        }
        val inDrive = backup.latest(token)
        _ui.update { it.copy(busy = false, message = message, inDrive = inDrive, looked = true) }
    }
}

/** Launches Google's consent screen whenever the view model asks for it. */
@Composable
private fun DriveConsent(viewModel: BackupViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onConsent(result.data)
    }
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    LaunchedEffect(consent) {
        val intent = consent ?: return@LaunchedEffect
        viewModel.consentLaunched()
        runCatching { launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build()) }
            .onFailure { viewModel.onConsent(null) }
    }
}

/** Settings: back up now, every night, and restore. */
@Composable
internal fun BackupCard(viewModel: BackupViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val crayon = MeyouwwTheme.crayon
    val context = LocalContext.current
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    DriveConsent(viewModel)

    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 12) {
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)

        val status = when {
            state.running && state.total > 0 -> stringResource(R.string.backup_running_files, state.done, state.total)
            state.running -> stringResource(R.string.backup_running)
            state.lastAt > 0 -> stringResource(R.string.backup_last, dates.format(Date(state.lastAt)))
            else -> stringResource(R.string.backup_never)
        }
        Text(status, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        val inDrive = ui.inDrive
        if (inDrive != null) {
            Text(
                stringResource(R.string.backup_in_drive, dates.format(Date(inDrive.at)), Formatter.formatShortFileSize(context, inDrive.bytes), inDrive.files),
                style = MaterialTheme.typography.bodyMedium,
                color = crayon.textSoft,
            )
        } else if (ui.looked) {
            Text(stringResource(R.string.backup_none_in_drive), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }

        SwitchRow(
            title = stringResource(R.string.backup_nightly),
            detail = stringResource(R.string.backup_nightly_detail),
            checked = state.nightly,
            onCheckedChange = viewModel::setNightly,
        )
        SwitchRow(
            title = stringResource(R.string.backup_media),
            detail = stringResource(R.string.backup_media_detail),
            checked = state.includeMedia,
            onCheckedChange = viewModel::setIncludeMedia,
        )
        SwitchRow(title = stringResource(R.string.backup_wifi), checked = state.wifiOnly, onCheckedChange = viewModel::setWifiOnly)

        val working = ui.busy || state.running
        CrayonButton(stringResource(R.string.backup_now), onClick = viewModel::backUpNow, loading = working, modifier = Modifier.fillMaxWidth())
        CrayonButton(
            stringResource(R.string.backup_restore),
            onClick = viewModel::restore,
            kind = ButtonKind.Secondary,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!ui.looked) {
            CrayonButton(
                stringResource(R.string.backup_look),
                onClick = viewModel::look,
                kind = ButtonKind.Quiet,
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ui.message?.let { message ->
            Text(
                stringResource(message.res, *message.args.toTypedArray()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * On a phone with no chats yet (a new phone, or a fresh install): asks once whether to bring
 * them back from Google Drive, and opens Settings where the restore button is.
 */
@Composable
fun BackupRestorePrompt(onOpenSettings: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val offer by viewModel.offerRestore.collectAsStateWithLifecycle()
    if (!offer) return
    AlertDialog(
        onDismissRequest = viewModel::restoreOffered,
        title = { Text(stringResource(R.string.restore_prompt_title)) },
        text = { Text(stringResource(R.string.restore_prompt_body)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.restoreOffered()
                onOpenSettings()
            }) { Text(stringResource(R.string.restore_prompt_yes)) }
        },
        dismissButton = { TextButton(onClick = viewModel::restoreOffered) { Text(stringResource(R.string.restore_prompt_no)) } },
    )
}
