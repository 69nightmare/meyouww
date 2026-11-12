package com.meyouww.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.data.auth.AccountSession
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.CrashLog
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceProfileRepository
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.data.profile.UsernameClaim
import com.meyouww.core.data.wallpaper.WallpaperApplier
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.LinkRow
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.pet.WhityCorner
import com.meyouww.core.designsystem.pet.WhityPetStore
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Profile
import com.meyouww.core.model.UserIdCheck
import com.meyouww.core.model.UserIdRules
import com.meyouww.core.notifications.push.CatchUpWorker
import com.meyouww.core.notifications.push.WallpaperGallery
import com.meyouww.core.notifications.push.NotifiedStore
import com.meyouww.core.notifications.push.WallpaperActionReceiver
import com.meyouww.core.notifications.push.WallpaperActionWorker
import com.meyouww.core.render.PhotoSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Public URLs of the legal drafts (the repository is public). */
object LegalLinks {
    const val PRIVACY = "https://github.com/69nightmare/meyouww/blob/main/docs/legal/PRIVACY.md"
    const val TERMS = "https://github.com/69nightmare/meyouww/blob/main/docs/legal/TERMS.md"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profiles: ProfileRepository,
    auth: AuthRepository,
    private val account: AccountSession,
    private val notified: NotifiedStore,
    private val alarmSync: AlarmSync,
    private val calendar: CalendarRepository,
    private val avatars: AvatarRepository,
    private val prefs: AppPreferences,
    private val deviceProfiles: DeviceProfileRepository,
    private val gallery: WallpaperGallery,
) : ViewModel() {
    val profile: StateFlow<Profile?> = profiles.profile
    val email: String? = auth.currentEmail()

    /** Off (default): chats stay on this phone. On: seen messages vanish unless saved. */
    val disappearingChats: StateFlow<Boolean> =
        prefs.disappearingChats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDisappearingChats(on: Boolean) = viewModelScope.launch { prefs.setDisappearingChats(on) }

    val shareModel: StateFlow<Boolean> = prefs.shareModel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val liveWallpaper: StateFlow<Boolean> = prefs.usesLiveWallpaper.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val lockRotates: StateFlow<Boolean> = prefs.lockRotates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _homeBase = MutableStateFlow(WallpaperApplier.hasHomeBase(context))
    val homeBase: StateFlow<Boolean> = _homeBase.asStateFlow()

    /** Copies a picked photo (no metadata) as the "home base" wallpaper. */
    fun setHomeBase(uri: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                PhotoSanitizer.importInto({ context.contentResolver.openInputStream(uri) }, WallpaperApplier.homeBase(context)) != null
            }.getOrDefault(false)
        }
        _homeBase.value = ok || WallpaperApplier.hasHomeBase(context)
    }

    fun restoreHomeBase() =
        WallpaperActionWorker.enqueue(context, WallpaperActionReceiver.ACTION_RESTORE_HOME_BASE, "", "both")

    /** Saves the answer and refreshes what friends' previews know about this phone. */
    fun setPhoneAnswer(shareModel: Boolean? = null, liveWallpaper: Boolean? = null, lockRotates: Boolean? = null) =
        viewModelScope.launch {
            prefs.setPhoneAnswers(shareModel, liveWallpaper, lockRotates)
            deviceProfiles.uploadIfChanged()
        }

    /** female, male, other or null ("prefer not to say"). Only this person sees it. */
    fun setGender(gender: String?) = viewModelScope.launch { profiles.setGender(gender) }

    private val _nameChange = MutableStateFlow(NameChange.Idle)

    /** Changing the name and ID (allowed once every 15 days; IDs are unique). */
    val nameChange: StateFlow<NameChange> = _nameChange.asStateFlow()

    fun changeName(username: String, displayName: String) = viewModelScope.launch {
        val id = (UserIdRules.check(username) as? UserIdCheck.Valid)?.normalized
        if (id == null || displayName.isBlank()) {
            _nameChange.value = NameChange.Invalid
            return@launch
        }
        _nameChange.value = NameChange.Busy
        _nameChange.value = when (val outcome = profiles.claimUsername(id, displayName, profile.value?.nickname)) {
            is Outcome.Success -> when (outcome.value) {
                UsernameClaim.Ok -> {
                    profiles.refresh()
                    NameChange.Done
                }
                UsernameClaim.Taken -> NameChange.Taken
                UsernameClaim.Reserved -> NameChange.Reserved
                UsernameClaim.TooSoon -> NameChange.TooSoon
                UsernameClaim.RateLimited -> NameChange.RateLimited
                UsernameClaim.Invalid -> NameChange.Invalid
            }
            is Outcome.Failure -> NameChange.Failed
        }
    }

    fun nameChangeSeen() {
        _nameChange.value = NameChange.Idle
    }

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    fun signOut() = viewModelScope.launch {
        _signingOut.value = true
        account.signOut()
        CatchUpWorker.cancel(context)
        notified.clear()
        // This phone stops ringing the old account's alarms and forgets its calendar and photos.
        alarmSync.clear()
        calendar.clear()
        avatars.clearCache()
        gallery.clear()
        // The app switches back to the welcome screen when the session ends.
    }
}

enum class NameChange { Idle, Busy, Done, Taken, Reserved, TooSoon, RateLimited, Invalid, Failed }

/** A new name and ID. Both can change once every 15 days; the ID must be one nobody else has. */
@Composable
private fun NameDialog(name: String, id: String, state: NameChange, onDismiss: () -> Unit, onSave: (id: String, name: String) -> Unit) {
    var newName by rememberSaveable { mutableStateOf(name) }
    var newId by rememberSaveable { mutableStateOf(id) }
    val message = when (state) {
        NameChange.Done -> R.string.settings_name_done
        NameChange.Taken -> R.string.settings_name_taken
        NameChange.Reserved -> R.string.settings_name_reserved
        NameChange.TooSoon -> R.string.settings_name_too_soon
        NameChange.RateLimited -> R.string.settings_name_slow_down
        NameChange.Invalid -> R.string.settings_name_invalid
        NameChange.Failed -> R.string.settings_name_failed
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_name_change)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.settings_name_body), style = MaterialTheme.typography.bodyMedium)
                CrayonTextField(
                    value = newName,
                    onValueChange = { newName = it.take(40) },
                    label = stringResource(R.string.settings_name_label),
                    modifier = Modifier.fillMaxWidth(),
                )
                CrayonTextField(
                    value = newId,
                    onValueChange = { newId = it.take(21).lowercase() },
                    label = stringResource(R.string.settings_id_label),
                    supportingText = stringResource(R.string.settings_id_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message != null) {
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state == NameChange.Done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (state == NameChange.Done) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_name_close)) }
            } else {
                TextButton(
                    onClick = { onSave(newId, newName.trim()) },
                    enabled = state != NameChange.Busy && newName.isNotBlank() && newId.isNotBlank() && (newName.trim() != name || newId.removePrefix("@") != id),
                ) { Text(stringResource(R.string.settings_name_save)) }
            }
        },
        dismissButton = {
            if (state != NameChange.Done) TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_name_cancel)) }
        },
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeliverySetup: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf(false) }
    val nameChange by viewModel.nameChange.collectAsStateWithLifecycle()
    val version = remember { appVersion(context) }
    if (editName) {
        NameDialog(
            name = profile?.displayName.orEmpty(),
            id = profile?.username.orEmpty(),
            state = nameChange,
            onDismiss = {
                editName = false
                viewModel.nameChangeSeen()
            },
            onSave = viewModel::changeName,
        )
    }

    CrayonScreen(title = stringResource(R.string.settings_title), onBack = onBack) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 3) {
            TapeLabel(stringResource(R.string.settings_account))
            profile?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PersonAvatar(it.displayName.orEmpty().take(1), it.username.orEmpty().hashCode(), size = 56.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(it.displayName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                        Text("@${it.username.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                CrayonButton(
                    stringResource(R.string.settings_name_change),
                    onClick = { editName = true },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                GenderRow(it.gender, viewModel::setGender)
            }
            viewModel.email?.let {
                Text(stringResource(R.string.settings_signed_in_as, it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        CrayonCard(
            modifier = Modifier.fillMaxWidth(),
            fill = crayon.green,
            seed = 4,
            onClick = onOpenDeliverySetup,
        ) {
            Text(stringResource(R.string.settings_background_delivery), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_background_delivery_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }
        val disappearingChats by viewModel.disappearingChats.collectAsStateWithLifecycle()
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 11) {
            Text(stringResource(R.string.settings_chats), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_chats_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
            SwitchRow(
                title = stringResource(R.string.settings_disappearing),
                detail = stringResource(R.string.settings_disappearing_detail),
                checked = disappearingChats,
                onCheckedChange = viewModel::setDisappearingChats,
            )
        }
        WhityCard()
        BackupCard(backupViewModel)
        val shareModel by viewModel.shareModel.collectAsStateWithLifecycle()
        val liveWallpaper by viewModel.liveWallpaper.collectAsStateWithLifecycle()
        val lockRotates by viewModel.lockRotates.collectAsStateWithLifecycle()
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 14) {
            TapeLabel(stringResource(R.string.settings_my_phone), tape = crayon.blue)
            Text(stringResource(R.string.settings_my_phone_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
            SwitchRow(
                title = stringResource(R.string.settings_live_wallpaper),
                detail = stringResource(R.string.settings_live_wallpaper_detail),
                checked = liveWallpaper,
                onCheckedChange = { viewModel.setPhoneAnswer(liveWallpaper = it) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_lock_rotates),
                detail = stringResource(R.string.settings_lock_rotates_detail),
                checked = lockRotates,
                onCheckedChange = { viewModel.setPhoneAnswer(lockRotates = it) },
            )
            SwitchRow(
                title = stringResource(R.string.settings_share_model),
                detail = stringResource(R.string.settings_share_model_detail),
                checked = shareModel,
                onCheckedChange = { viewModel.setPhoneAnswer(shareModel = it) },
            )
        }
        val homeBase by viewModel.homeBase.collectAsStateWithLifecycle()
        val pickHomeBase = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.setHomeBase(uri)
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 7) {
            TapeLabel(stringResource(R.string.settings_home_base))
            Text(stringResource(R.string.settings_home_base_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
            CrayonButton(
                text = stringResource(if (homeBase) R.string.settings_home_base_change else R.string.settings_home_base_pick),
                onClick = { pickHomeBase.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                kind = ButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (homeBase) {
                CrayonButton(
                    text = stringResource(R.string.settings_home_base_restore),
                    onClick = viewModel::restoreHomeBase,
                    kind = ButtonKind.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 5) {
            LinkRow(stringResource(R.string.settings_privacy), onClick = { SystemSettings.web(context, LegalLinks.PRIVACY) })
            LinkRow(stringResource(R.string.settings_terms), onClick = { SystemSettings.web(context, LegalLinks.TERMS) })
            LinkRow(stringResource(R.string.settings_licenses), onClick = onOpenLicenses)
        }
        var crash by remember { mutableStateOf(CrashLog.read(context)) }
        crash?.let { report ->
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 9) {
                Text(stringResource(R.string.settings_crash_title), style = MaterialTheme.typography.titleMedium)
                Text(report.lineSequence().take(4).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.settings_crash_body), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                CrayonButton(
                    text = stringResource(R.string.settings_crash_copy),
                    onClick = {
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("meyouww crash", report))
                    },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                CrayonButton(
                    text = stringResource(R.string.settings_crash_clear),
                    onClick = {
                        CrashLog.clear(context)
                        crash = null
                    },
                    kind = ButtonKind.Quiet,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        CrayonButton(
            text = stringResource(R.string.settings_sign_out),
            onClick = { confirmSignOut = true },
            kind = ButtonKind.Quiet,
            loading = signingOut,
            modifier = Modifier.fillMaxWidth(),
        )
        version?.let {
            Text(stringResource(R.string.settings_version, it), style = MaterialTheme.typography.labelMedium, color = crayon.textSoft, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_sign_out_confirm_body))
                    // A new phone starts with empty chats: remind them to back up first.
                    Text(stringResource(R.string.backup_sign_out_reminder), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        confirmSignOut = false
                        backupViewModel.backUpNow()
                    }) { Text(stringResource(R.string.backup_first)) }
                    TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.settings_cancel)) }
                }
            },
        )
    }
}

@Composable
internal fun BackRow(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
    }
}

private fun appVersion(context: Context): String? = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull()

/** Gender (from Google, or chosen here). Private: friends never see it. */
@Composable
private fun GenderRow(gender: String?, onChange: (String?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val options = listOf("female" to R.string.gender_female, "male" to R.string.gender_male, "other" to R.string.gender_other, null to R.string.gender_none)
    val current = options.firstOrNull { it.first == gender }?.second ?: R.string.gender_none
    Box {
        TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) {
            Text(stringResource(R.string.settings_gender, stringResource(current)) + " ▾", style = MaterialTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    menu = false
                    onChange(value)
                })
            }
        }
    }
}

/** Whity the pet: show or hide her on Home, People and in chats, and open her corner. */
@Composable
private fun WhityCard() {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val whity by remember(context) { WhityPetStore.state(context) }.collectAsStateWithLifecycle()
    var corner by rememberSaveable { mutableStateOf(false) }
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 12) {
        TapeLabel(stringResource(R.string.settings_whity), tape = crayon.bubblegum)
        Text(stringResource(R.string.settings_whity_body), style = MaterialTheme.typography.bodyMedium)
        SwitchRow(
            title = stringResource(R.string.settings_whity_shown),
            detail = stringResource(R.string.settings_whity_shown_detail),
            checked = whity.shown,
            onCheckedChange = { on -> WhityPetStore.update(context) { it.copy(shown = on) } },
        )
        if (whity.shown) {
            SwitchRow(
                title = stringResource(R.string.settings_whity_roams),
                detail = stringResource(R.string.settings_whity_roams_detail),
                checked = whity.roams,
                onCheckedChange = { on -> WhityPetStore.update(context) { it.copy(roams = on) } },
            )
        }
        CrayonButton(
            stringResource(R.string.settings_whity_corner),
            onClick = { corner = true },
            kind = ButtonKind.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (corner) WhityCorner(onDismiss = { corner = false })
}
