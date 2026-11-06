package com.meyouww.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.auth.AccountSession
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Public URLs of the legal drafts (the repository is public). */
object LegalLinks {
    const val PRIVACY = "https://github.com/69nightmare/meyouww/blob/main/docs/legal/PRIVACY.md"
    const val TERMS = "https://github.com/69nightmare/meyouww/blob/main/docs/legal/TERMS.md"
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    profiles: ProfileRepository,
    auth: AuthRepository,
    private val account: AccountSession,
) : ViewModel() {
    val profile: StateFlow<Profile?> = profiles.profile
    val email: String? = auth.currentEmail()

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    fun signOut() = viewModelScope.launch {
        _signingOut.value = true
        account.signOut()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
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
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 5, onClick = { SystemSettings.web(context, LegalLinks.PRIVACY) }) {
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 6, onClick = { SystemSettings.web(context, LegalLinks.TERMS) }) {
            Text(stringResource(R.string.settings_terms), style = MaterialTheme.typography.titleMedium)
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 8, onClick = onOpenLicenses) {
            Text(stringResource(R.string.settings_licenses), style = MaterialTheme.typography.titleMedium)
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
