package com.meyouww.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.data.profile.UsernameClaim
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.UserIdCheck
import com.meyouww.core.model.UserIdRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupUiState(
    val username: String = "",
    val displayName: String = "",
    val nickname: String = "",
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val claim: UsernameClaim? = null,
    val error: AppError? = null,
    val done: Boolean = false,
) {
    val idCheck: UserIdCheck get() = UserIdRules.check(username)
    val displayNameValid: Boolean get() = displayName.trim().length in 1..40
}

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileSetupUiState())
    val state: StateFlow<ProfileSetupUiState> = _state.asStateFlow()

    init {
        // Pre-fill if a previous attempt saved part of the profile.
        profiles.profile.value?.let { p ->
            _state.update { it.copy(username = p.username.orEmpty(), displayName = p.displayName.orEmpty(), nickname = p.nickname.orEmpty()) }
        }
        // Signed in with Google: start from the Google name (still editable).
        if (_state.value.displayName.isBlank()) {
            auth.providerDisplayName()?.let { name -> _state.update { it.copy(displayName = name) } }
        }
    }

    fun onUsername(v: String) = _state.update { it.copy(username = UserIdRules.normalize(v).take(24), claim = null, error = null) }
    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v.take(40)) }
    fun onNickname(v: String) = _state.update { it.copy(nickname = v.take(24)) }

    fun save() {
        val s = _state.value
        val check = s.idCheck
        if (check !is UserIdCheck.Valid || !s.displayNameValid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, claim = null) }
            when (val outcome = profiles.claimUsername(check.normalized, s.displayName, s.nickname)) {
                is Outcome.Success -> if (outcome.value == UsernameClaim.Ok) {
                    profiles.refresh()
                    _state.update { it.copy(saving = false, done = true) }
                } else {
                    _state.update { it.copy(saving = false, claim = outcome.value) }
                }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = outcome.error) }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(
    onDone: () -> Unit,
    viewModel: ProfileSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val crayon = MeyouwwTheme.crayon

    val check = state.idCheck
    val idError = when {
        state.claim == UsernameClaim.Taken -> stringResource(R.string.profile_taken)
        state.claim == UsernameClaim.Reserved -> stringResource(R.string.profile_reserved)
        state.claim == UsernameClaim.Invalid -> stringResource(R.string.profile_invalid)
        state.claim == UsernameClaim.TooSoon -> stringResource(R.string.profile_too_soon)
        state.claim == UsernameClaim.RateLimited -> stringResource(R.string.profile_rate_limited)
        check is UserIdCheck.Invalid && (state.showErrors || state.username.length >= 3) -> stringResource(check.reason.messageRes())
        else -> null
    }
    val idSupport = if (check is UserIdCheck.Valid) {
        stringResource(R.string.uid_looks_good, check.normalized)
    } else {
        stringResource(R.string.profile_username_rules)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Sticker(WhitySticker.Cool, decorative = true, size = 130.dp)
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.profile_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft, textAlign = TextAlign.Center)
        CrayonTextField(
            value = state.username,
            onValueChange = viewModel::onUsername,
            label = stringResource(R.string.profile_username),
            placeholder = "@" + stringResource(R.string.profile_username_hint),
            supportingText = idSupport,
            errorText = idError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        CrayonTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayName,
            label = stringResource(R.string.profile_display_name),
            placeholder = stringResource(R.string.profile_display_name_hint),
            errorText = if (state.showErrors && !state.displayNameValid) stringResource(R.string.profile_display_name_required) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        CrayonTextField(
            value = state.nickname,
            onValueChange = viewModel::onNickname,
            label = stringResource(R.string.profile_nickname),
            placeholder = stringResource(R.string.profile_nickname_hint),
            supportingText = stringResource(R.string.profile_nickname_support),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        ErrorLine(state.error)
        CrayonButton(
            text = stringResource(R.string.profile_save),
            onClick = viewModel::save,
            loading = state.saving,
            loadingDescription = stringResource(R.string.profile_saving),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun UserIdCheck.Reason.messageRes(): Int = when (this) {
    UserIdCheck.Reason.TOO_SHORT -> R.string.uid_too_short
    UserIdCheck.Reason.TOO_LONG -> R.string.uid_too_long
    UserIdCheck.Reason.INVALID_CHARACTER -> R.string.uid_invalid_character
    UserIdCheck.Reason.MUST_START_WITH_LETTER -> R.string.uid_must_start_with_letter
    UserIdCheck.Reason.CONSECUTIVE_DOTS -> R.string.uid_consecutive_dots
    UserIdCheck.Reason.TRAILING_DOT -> R.string.uid_trailing_dot
    UserIdCheck.Reason.RESERVED -> R.string.uid_reserved
}
