package com.meyouww.feature.settings

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySegmented
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import com.meyouww.core.designsystem.component.CardLevel
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import com.meyouww.core.designsystem.component.FoldingSection
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
import com.meyouww.core.designsystem.component.PastelIcons
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
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.pet.WhityCorner
import com.meyouww.core.designsystem.pet.WhityPetStore
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette
import com.meyouww.core.designsystem.theme.MeyouwwLook
import com.meyouww.core.designsystem.theme.LookStore
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.LocalSky
import com.meyouww.core.designsystem.theme.SkyMotion
import com.meyouww.core.designsystem.theme.SkyPalette
import com.meyouww.core.designsystem.theme.SkyPhase
import com.meyouww.core.designsystem.theme.SkyStore
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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
        if (crayon.living) {
            // You, on the sky: your coin, name and ID, and Edit.
            profile?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 2.dp)) {
                    PersonAvatar(it.displayName.orEmpty().take(1).uppercase(), it.username.orEmpty().hashCode(), size = 68.dp)
                    Column(Modifier.weight(1f)) {
                        Text(it.displayName.orEmpty(), style = SkyType.h3.copy(shadow = skyHalo()), color = SkyInk.onSky)
                        Text(
                            "@" + it.username.orEmpty() + (viewModel.email?.let { _ -> " · " + stringResource(R.string.settings_sky_google) } ?: ""),
                            style = SkyType.sm.copy(shadow = skyHalo()),
                            color = SkyInk.onSkySoft,
                        )
                        GenderRow(it.gender, viewModel::setGender)
                    }
                    SkyButton(stringResource(R.string.settings_sky_edit), { editName = true }, small = true, tone = SkyButtonTone.Quiet)
                }
            }
            SkyLookCard()
            // Background delivery, on butter with its fix.
            SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Butter, level = CardLevel.Raised, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), onClick = onOpenDeliverySetup) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(SkyIcons.Bell, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_background_delivery), style = SkyType.tt, color = SkyInk.onPastel)
                        Text(stringResource(R.string.settings_background_delivery_body), style = SkyType.sm, color = SkyInk.onPastel)
                    }
                    SkyButton(stringResource(R.string.settings_sky_fix), onOpenDeliverySetup, small = true)
                }
            }
            FoldingSection(stringResource(R.string.settings_whity), stringResource(R.string.settings_whity_body), SkyIcons.Heart, crayon.bubblegum) { WhityCard() }
            val disappearing by viewModel.disappearingChats.collectAsStateWithLifecycle()
            FoldingSection(stringResource(R.string.settings_sky_chats_backup), stringResource(R.string.backup_body), SkyIcons.Chat, crayon.lilac) {
                SwitchRow(
                    title = stringResource(R.string.settings_disappearing),
                    detail = stringResource(R.string.settings_disappearing_detail),
                    checked = disappearing,
                    onCheckedChange = viewModel::setDisappearingChats,
                )
                BackupCard(backupViewModel)
            }
            val shareModel by viewModel.shareModel.collectAsStateWithLifecycle()
            val liveWallpaper by viewModel.liveWallpaper.collectAsStateWithLifecycle()
            val lockRotates by viewModel.lockRotates.collectAsStateWithLifecycle()
            FoldingSection(stringResource(R.string.settings_my_phone), stringResource(R.string.settings_my_phone_body), SkyIcons.Phone, crayon.blue) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow(stringResource(R.string.settings_live_wallpaper), liveWallpaper, { viewModel.setPhoneAnswer(liveWallpaper = it) }, detail = stringResource(R.string.settings_live_wallpaper_detail))
                    SwitchRow(stringResource(R.string.settings_lock_rotates), lockRotates, { viewModel.setPhoneAnswer(lockRotates = it) }, detail = stringResource(R.string.settings_lock_rotates_detail))
                    SwitchRow(stringResource(R.string.settings_share_model), shareModel, { viewModel.setPhoneAnswer(shareModel = it) }, detail = stringResource(R.string.settings_share_model_detail))
                }
            }
            val homeBase by viewModel.homeBase.collectAsStateWithLifecycle()
            val pickHome = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) viewModel.setHomeBase(uri)
            }
            FoldingSection(stringResource(R.string.settings_home_base), stringResource(R.string.settings_home_base_body), SkyIcons.Wallpaper, crayon.green) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkyButton(
                        stringResource(if (homeBase) R.string.settings_home_base_change else R.string.settings_home_base_pick),
                        { pickHome.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f),
                        small = true,
                        tone = SkyButtonTone.Mint,
                    )
                    if (homeBase) SkyButton(stringResource(R.string.settings_home_base_restore), viewModel::restoreHomeBase, modifier = Modifier.weight(1f), small = true, tone = SkyButtonTone.Quiet)
                }
            }
            FoldingSection(stringResource(R.string.settings_sky_about), stringResource(R.string.settings_sky_about_body), SkyIcons.Info, crayon.peach) {
                LinkRow(stringResource(R.string.settings_privacy), onClick = { SystemSettings.web(context, LegalLinks.PRIVACY) })
                LinkRow(stringResource(R.string.settings_terms), onClick = { SystemSettings.web(context, LegalLinks.TERMS) })
                LinkRow(stringResource(R.string.settings_licenses), onClick = onOpenLicenses)
            }
            var crashReport by remember { mutableStateOf(CrashLog.read(context)) }
            crashReport?.let { report ->
                SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Peach, spacing = 8.dp) {
                    Text(stringResource(R.string.settings_crash_title), style = SkyType.tt, color = SkyInk.onPastel)
                    Text(report.lineSequence().take(4).joinToString("\n"), style = SkyType.hp, color = SkyInk.onPastel)
                    Text(stringResource(R.string.settings_crash_body), style = SkyType.hp, color = SkyInk.onPastel)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkyButton(stringResource(R.string.settings_crash_copy), {
                            context.getSystemService(android.content.ClipboardManager::class.java)
                                ?.setPrimaryClip(android.content.ClipData.newPlainText("meyouww crash", report))
                        }, modifier = Modifier.weight(1f), small = true, tone = SkyButtonTone.Quiet)
                        SkyButton(stringResource(R.string.settings_crash_clear), {
                            CrashLog.clear(context)
                            crashReport = null
                        }, modifier = Modifier.weight(1f), small = true, tone = SkyButtonTone.Text)
                    }
                }
            }
            SkyButton(stringResource(R.string.settings_sign_out), { confirmSignOut = true }, tone = SkyButtonTone.Danger, block = true, icon = SkyIcons.SignOut, loading = signingOut)
            version?.let {
                Text(stringResource(R.string.settings_version, it), style = SkyType.cap.copy(shadow = skyHalo()), color = SkyInk.onSkySoft, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
            }
            return@CrayonScreen
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 3) {
            TapeLabel(stringResource(R.string.settings_account))
            profile?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PersonAvatar(it.displayName.orEmpty().take(1), it.username.orEmpty().hashCode(), size = 64.dp)
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
            Text(stringResource(R.string.settings_background_delivery_body), style = MaterialTheme.typography.bodyMedium)
        }
        val disappearingChats by viewModel.disappearingChats.collectAsStateWithLifecycle()
        // Living Sky: each section rests as a summary row and opens in place (other looks: as before).
        FoldingSection(stringResource(R.string.settings_chats), stringResource(R.string.settings_chats_body), PastelIcons.Chat, crayon.blue) {
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
        }
        val look by remember(context) { LookStore.state(context) }.collectAsStateWithLifecycle()
        val lookName = stringResource(
            when (look) {
                MeyouwwLook.Pastel -> R.string.settings_look_pastel
                MeyouwwLook.Crayon -> R.string.settings_look_crayon
                MeyouwwLook.Sky -> R.string.settings_look_sky
            },
        )
        FoldingSection(stringResource(R.string.settings_look_title), lookName, PastelIcons.Smile, crayon.lilac) { LookCard() }
        FoldingSection(stringResource(R.string.settings_whity), stringResource(R.string.settings_whity_body), PastelIcons.Heart, crayon.bubblegum) { WhityCard() }
        FoldingSection(stringResource(R.string.backup_title), stringResource(R.string.backup_body), PastelIcons.Lock, crayon.green) { BackupCard(backupViewModel) }
        val shareModel by viewModel.shareModel.collectAsStateWithLifecycle()
        val liveWallpaper by viewModel.liveWallpaper.collectAsStateWithLifecycle()
        val lockRotates by viewModel.lockRotates.collectAsStateWithLifecycle()
        FoldingSection(stringResource(R.string.settings_my_phone), stringResource(R.string.settings_my_phone_body), PastelIcons.People, crayon.yellow) {
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
        }
        val homeBase by viewModel.homeBase.collectAsStateWithLifecycle()
        val pickHomeBase = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.setHomeBase(uri)
        }
        FoldingSection(stringResource(R.string.settings_home_base), stringResource(R.string.settings_home_base_body), PastelIcons.Home, crayon.peach) {
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
            kind = ButtonKind.Danger,
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
            Icon(PastelIcons.Back, contentDescription = stringResource(R.string.back))
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
    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 12) {
        TapeLabel(stringResource(R.string.settings_whity), tape = crayon.bubblegum)
        Text(stringResource(R.string.settings_whity_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
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

/** Pastel Whity or the earlier crayon look, for this phone only. Applies at once. */
@Composable
private fun LookCard() {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val look by remember(context) { LookStore.state(context) }.collectAsStateWithLifecycle()
    CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 13) {
        TapeLabel(stringResource(R.string.settings_look_title), tape = crayon.lilac)
        Text(stringResource(R.string.settings_look_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LookOption(
                selected = look == MeyouwwLook.Pastel,
                title = stringResource(R.string.settings_look_pastel),
                detail = stringResource(R.string.settings_look_pastel_detail),
                swatches = listOf(Color(Palette.Strawberry), Color(Palette.Butter), Color(Palette.Cloud)),
                onClick = { LookStore.set(context, MeyouwwLook.Pastel) },
            )
            LookOption(
                selected = look == MeyouwwLook.Crayon,
                title = stringResource(R.string.settings_look_crayon),
                detail = stringResource(R.string.settings_look_crayon_detail),
                swatches = listOf(Color(Palette.Classic.CrayonRed), Color(Palette.Classic.SunnyYellow), Color(Palette.Classic.SkyBlue)),
                onClick = { LookStore.set(context, MeyouwwLook.Crayon) },
            )
            LookOption(
                selected = look == MeyouwwLook.Sky,
                title = stringResource(R.string.settings_look_sky),
                detail = stringResource(R.string.settings_look_sky_detail),
                swatches = listOf(Color(SkyPalette.day.top), Color(SkyPalette.gold.middle), Color(SkyPalette.night.middle)),
                onClick = { LookStore.set(context, MeyouwwLook.Sky) },
            )
        }
        if (look == MeyouwwLook.Sky) SkyOptions()
    }
}

/** Living Sky's own choices: follow the clock or keep one sky, and how much moves. */
@Composable
private fun SkyOptions() {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val sky by remember(context) { SkyStore.state(context) }.collectAsStateWithLifecycle()
    SwitchRow(
        title = stringResource(R.string.settings_sky_follow),
        detail = stringResource(R.string.settings_sky_follow_detail),
        checked = sky.followTime,
        onCheckedChange = { on -> SkyStore.update(context) { it.copy(followTime = on) } },
    )
    if (!sky.followTime) {
        Text(stringResource(R.string.settings_sky_hold), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
            SkyPhase.entries.forEach { phase ->
                CrayonChip(
                    text = stringResource(phaseName(phase)),
                    fill = null,
                    selected = sky.held == phase,
                    onClick = { SkyStore.update(context) { it.copy(held = phase) } },
                )
            }
        }
    }
    Text(stringResource(R.string.settings_sky_motion), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
        SkyMotion.entries.forEach { motion ->
            CrayonChip(
                text = stringResource(
                    when (motion) {
                        SkyMotion.Full -> R.string.settings_sky_motion_full
                        SkyMotion.Calm -> R.string.settings_sky_motion_calm
                        SkyMotion.Off -> R.string.settings_sky_motion_off
                    },
                ),
                fill = null,
                selected = sky.motion == motion,
                onClick = { SkyStore.update(context) { it.copy(motion = motion) } },
            )
        }
    }
    Text(
        stringResource(
            when (sky.motion) {
                SkyMotion.Full -> R.string.settings_sky_motion_full_detail
                SkyMotion.Calm -> R.string.settings_sky_motion_calm_detail
                SkyMotion.Off -> R.string.settings_sky_motion_off_detail
            },
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = crayon.textSoft,
    )
    if (sky.motion == SkyMotion.Full && LocalSky.current?.ambient == false && !LocalReducedMotion.current) {
        Text(stringResource(R.string.settings_sky_battery), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
    }
}

private fun phaseName(phase: SkyPhase): Int = when (phase) {
    SkyPhase.Dawn -> R.string.settings_sky_dawn
    SkyPhase.Day -> R.string.settings_sky_day
    SkyPhase.Gold -> R.string.settings_sky_gold
    SkyPhase.Dusk -> R.string.settings_sky_dusk
    SkyPhase.Night -> R.string.settings_sky_night
}

/** One look to pick: a radio button, its name and what it's like, and three of its colours. */
@Composable
private fun LookOption(selected: Boolean, title: String, detail: String, swatches: List<Color>, onClick: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val shape = RoundedCornerShape(18.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else crayon.edge.copy(alpha = 0.5f), shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
        }
        // A peek at the look's colours (decoration: the words above say which look it is).
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clearAndSetSemantics { }) {
            swatches.forEach { color ->
                Box(Modifier.size(20.dp).clip(CircleShape).background(color).border(1.5.dp, crayon.outline, CircleShape))
            }
        }
    }
}

/**
 * Living Sky: the board's Look card. The three looks as little pictures to tap (the chosen one
 * ringed), following the time of day or holding one time, and how much moves.
 */
@Composable
private fun SkyLookCard() {
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val look by remember(context) { LookStore.state(context) }.collectAsStateWithLifecycle()
    val sky by remember(context) { SkyStore.state(context) }.collectAsStateWithLifecycle()
    SkyCard(modifier = Modifier.fillMaxWidth(), spacing = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_look_title), style = SkyType.t, modifier = Modifier.weight(1f).semantics { heading() })
            SkyPill(stringResource(R.string.settings_sky_new), tone = SkyTone.Blue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().selectableGroup().height(IntrinsicSize.Max)) {
            listOf(
                Triple(MeyouwwLook.Pastel, R.string.settings_look_pastel, listOf(Color.White, Color(Palette.Strawberry))),
                Triple(MeyouwwLook.Crayon, R.string.settings_look_crayon, listOf(Color(Palette.Classic.SunnyYellow), Color(Palette.Classic.CrayonRed))),
                Triple(MeyouwwLook.Sky, R.string.settings_look_sky, listOf(Color(SkyPalette.day.top), Color.White)),
            ).forEach { (option, name, colors) ->
                val on = look == option
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind {
                            if (on) drawRoundRect(crayon.red, cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                        }
                        .padding(3.dp)
                        .skySurface(crayon.card, crayon.hardShadow, Color.Transparent, 0f, cornerRadius = 17.dp, depth = 3.dp, belly = false)
                        .clip(RoundedCornerShape(17.dp))
                        .selectable(selected = on, role = Role.RadioButton) { LookStore.set(context, option) }
                        .padding(8.dp),
                ) {
                    // A little picture of the look (decoration: the name says which it is).
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors[0])
                            .clearAndSetSemantics { },
                    ) {
                        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(width = 34.dp, height = 10.dp).background(colors[1], RoundedCornerShape(5.dp)))
                    }
                    Text(stringResource(name), style = SkyType.cap.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        if (look == MeyouwwLook.Sky) {
            SwitchRow(
                title = stringResource(R.string.settings_sky_follow),
                detail = stringResource(R.string.settings_sky_follow_detail),
                checked = sky.followTime,
                onCheckedChange = { on -> SkyStore.update(context) { it.copy(followTime = on) } },
            )
            Text(stringResource(R.string.settings_sky_or_hold), style = SkyType.hp, color = crayon.textSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
                SkyPhase.entries.forEach { phase ->
                    val on = !sky.followTime && sky.held == phase
                    SkyChip(
                        stringResource(phaseName(phase)),
                        selected = on,
                        height = 36.dp,
                        onClick = { SkyStore.update(context) { it.copy(followTime = false, held = phase) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_sky_motion), style = SkyType.tt)
                SkySegmented(
                    options = SkyMotion.entries.map {
                        stringResource(
                            when (it) {
                                SkyMotion.Full -> R.string.settings_sky_motion_full
                                SkyMotion.Calm -> R.string.settings_sky_motion_calm
                                SkyMotion.Off -> R.string.settings_sky_motion_off
                            },
                        )
                    },
                    selected = SkyMotion.entries.indexOf(sky.motion),
                    onSelect = { i -> SkyStore.update(context) { it.copy(motion = SkyMotion.entries[i]) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(
                    when (sky.motion) {
                        SkyMotion.Full -> R.string.settings_sky_motion_full_detail
                        SkyMotion.Calm -> R.string.settings_sky_motion_calm_detail
                        SkyMotion.Off -> R.string.settings_sky_motion_off_detail
                    },
                ),
                style = SkyType.hp,
                color = crayon.textSoft,
            )
            if (sky.motion == SkyMotion.Full && LocalSky.current?.ambient == false && !LocalReducedMotion.current) {
                Text(stringResource(R.string.settings_sky_battery), style = SkyType.hp, color = crayon.textSoft)
            }
        }
    }
}
