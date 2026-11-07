package com.meyouww.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.meyouww.core.data.device.DeviceProfileRepository
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.data.wallpaper.WallpaperApplier
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Profile
import com.meyouww.core.notifications.push.CatchUpWorker
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
    profiles: ProfileRepository,
    auth: AuthRepository,
    private val account: AccountSession,
    private val notified: NotifiedStore,
    private val alarmSync: AlarmSync,
    private val calendar: CalendarRepository,
    private val avatars: AvatarRepository,
    private val prefs: AppPreferences,
    private val deviceProfiles: DeviceProfileRepository,
) : ViewModel() {
    val profile: StateFlow<Profile?> = profiles.profile
    val email: String? = auth.currentEmail()

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
        // The app switches back to the welcome screen when the session ends.
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeliverySetup: () -> Unit,
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    val version = remember { appVersion(context) }

    val narrow = LocalConfiguration.current.screenWidthDp < 360
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(if (narrow) 12.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BackRow(onBack)
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 3) {
            Text(stringResource(R.string.settings_account), style = MaterialTheme.typography.labelLarge)
            profile?.let {
                Text(it.displayName.orEmpty(), style = MaterialTheme.typography.headlineSmall)
                Text("@${it.username.orEmpty()}", style = MaterialTheme.typography.titleMedium)
            }
            viewModel.email?.let {
                Text(stringResource(R.string.settings_signed_in_as, it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        CrayonCard(
            modifier = Modifier.fillMaxWidth(),
            seed = 4,
            onClick = onOpenDeliverySetup,
        ) {
            Text(stringResource(R.string.settings_background_delivery), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_background_delivery_body), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }
        val shareModel by viewModel.shareModel.collectAsStateWithLifecycle()
        val liveWallpaper by viewModel.liveWallpaper.collectAsStateWithLifecycle()
        val lockRotates by viewModel.lockRotates.collectAsStateWithLifecycle()
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 4) {
            Text(stringResource(R.string.settings_my_phone), style = MaterialTheme.typography.titleMedium)
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
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 7) {
            Text(stringResource(R.string.settings_home_base), style = MaterialTheme.typography.titleMedium)
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
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 5, onClick = { SystemSettings.web(context, LegalLinks.PRIVACY) }) {
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 6, onClick = { SystemSettings.web(context, LegalLinks.TERMS) }) {
            Text(stringResource(R.string.settings_terms), style = MaterialTheme.typography.titleMedium)
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 8, onClick = onOpenLicenses) {
            Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.titleMedium)
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
            Text(stringResource(R.string.settings_version, it), style = MaterialTheme.typography.labelMedium, color = crayon.textSoft)
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.settings_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.settings_cancel)) }
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
