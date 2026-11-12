package com.meyouww.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.model.GooglePeople
import java.time.LocalDate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.prefs.PendingAgeGate
import com.meyouww.core.data.profile.AgeGateRecord
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.AgeGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

/** Before sign-in the answers are kept on the phone; after sign-in they go straight to the server. */
enum class AgeGateMode { BeforeSignIn, AfterSignIn }

data class AgeGateUiState(
    val birthYear: String = "",
    val confirmedAdult: Boolean = false,
    val acceptedTerms: Boolean = false,
    val underage: Boolean = false,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val error: AppError? = null,
    val done: Boolean = false,
    /** Google shared no birthday (they said no, or there is none in their Google account). */
    val missingBirthday: Boolean = false,
) {
    val yearValid: Boolean get() = birthYear.length == 4 && birthYear.toIntOrNull() != null
}

@HiltViewModel
class AgeGateViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AgeGateUiState())
    val state: StateFlow<AgeGateUiState> = _state.asStateFlow()

    fun onBirthYear(value: String) = _state.update {
        it.copy(birthYear = value.filter(Char::isDigit).take(4), underage = false, error = null)
    }

    fun onConfirmAdult(value: Boolean) = _state.update { it.copy(confirmedAdult = value) }

    fun onAcceptTerms(value: Boolean) = _state.update { it.copy(acceptedTerms = value) }

    fun submit(mode: AgeGateMode) {
        val s = _state.value
        val year = s.birthYear.toIntOrNull()
        val result = AgeGate.evaluate(year, s.confirmedAdult, Year.now().value)
        when {
            result == AgeGate.Result.Underage -> _state.update { it.copy(underage = true) }
            result != AgeGate.Result.Eligible || !s.acceptedTerms -> _state.update { it.copy(showErrors = true) }
            else -> save(mode, year!!)
        }
    }

    private fun save(mode: AgeGateMode, year: Int) = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null) }
        when (mode) {
            AgeGateMode.BeforeSignIn -> {
                prefs.savePendingAgeGate(PendingAgeGate(year, true, System.currentTimeMillis()))
                _state.update { it.copy(saving = false, done = true) }
            }
            AgeGateMode.AfterSignIn -> when (val outcome = profiles.recordAgeGate(year, true)) {
                is Outcome.Success -> when (outcome.value) {
                    AgeGateRecord.Ok -> {
                        profiles.refresh()
                        _state.update { it.copy(saving = false, done = true) }
                    }
                    AgeGateRecord.Underage -> _state.update { it.copy(saving = false, underage = true) }
                    AgeGateRecord.Invalid -> _state.update { it.copy(saving = false, showErrors = true) }
                }
                is Outcome.Failure -> _state.update { it.copy(saving = false, error = outcome.error) }
            }
        }
    }

    fun consumeDone() = _state.update { it.copy(done = false) }

    fun onGoogleStarted() = _state.update { it.copy(saving = true, error = null, missingBirthday = false) }

    /**
     * The age check after sign-in: Google's birthday (with the person's OK on Google's own
     * screen) is the only way in. [accessToken] is null when they said no or Google couldn't
     * be asked.
     */
    fun fromGoogle(accessToken: String?) = viewModelScope.launch {
        _state.update { it.copy(saving = true, error = null, missingBirthday = false) }
        val details = accessToken?.let { GoogleAccess.details(it) }
        val age = details?.let { GooglePeople.age(it, LocalDate.now()) } ?: GooglePeople.Age.Unknown
        val year = details?.let { it.birthday?.year ?: it.birthYear }
        when {
            age == GooglePeople.Age.Underage -> _state.update { it.copy(saving = false, underage = true) }
            age != GooglePeople.Age.Adult || year == null -> _state.update { it.copy(saving = false, missingBirthday = true) }
            else -> save(AgeGateMode.AfterSignIn, year)
        }
    }
}

/**
 * The age step for a signed-in account that has no age on record yet. meyouww asks Google for
 * the birthday; the typed form remains only in a development build without Google sign-in.
 */
@Composable
fun AgeCheckScreen(
    onDone: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    if (BackendConfig.isGoogleSignInConfigured) {
        GoogleBirthdayScreen(onDone = onDone)
    } else {
        AgeGateScreen(AgeGateMode.AfterSignIn, onDone = onDone, onOpenTerms = onOpenTerms, onOpenPrivacy = onOpenPrivacy)
    }
}

/** One button: Google shares the birthday, so nobody types an age. */
@Composable
fun GoogleBirthdayScreen(
    onDone: () -> Unit,
    viewModel: AgeGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.consumeDone()
            onDone()
        }
    }
    if (state.underage) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.Center) {
            StickerMessage(
                sticker = WhitySticker.Burrito,
                title = stringResource(R.string.age_underage_title),
                body = stringResource(R.string.age_underage_body),
            )
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Google's own "share your birthday with meyouww?" screen.
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.fromGoogle(GoogleAccess.tokenFrom(context, result.data))
    }
    CrayonScreen(title = stringResource(R.string.birthday_title)) {
        Sticker(WhitySticker.PupCurious, decorative = true, size = 140.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            stringResource(R.string.birthday_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.missingBirthday) {
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.yellow, seed = 21) {
                Text(stringResource(R.string.birthday_missing), style = MaterialTheme.typography.bodyMedium)
            }
        }
        ErrorLine(state.error)
        CrayonButton(
            text = stringResource(R.string.birthday_share),
            onClick = {
                viewModel.onGoogleStarted()
                scope.launch {
                    val access = GoogleAccess.authorize(context, null)
                    val intent = access?.pendingIntent
                    when {
                        access == null -> viewModel.fromGoogle(null)
                        access.hasResolution() && intent != null ->
                            consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        else -> viewModel.fromGoogle(access.accessToken)
                    }
                }
            },
            loading = state.saving,
            loadingDescription = stringResource(R.string.birthday_busy),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.age_birth_year_support),
            style = MaterialTheme.typography.bodySmall,
            color = MeyouwwTheme.crayon.textSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun AgeGateScreen(
    mode: AgeGateMode,
    onDone: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: AgeGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.consumeDone()
            onDone()
        }
    }
    if (state.underage) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), verticalArrangement = Arrangement.Center) {
            StickerMessage(
                sticker = WhitySticker.Burrito,
                title = stringResource(R.string.age_underage_title),
                body = stringResource(R.string.age_underage_body),
            )
        }
        return
    }
    CrayonScreen(title = stringResource(R.string.age_title)) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 18, fill = MeyouwwTheme.crayon.peach) {
            Text(stringResource(R.string.age_body), style = MaterialTheme.typography.bodyLarge)
        }
        CrayonTextField(
            value = state.birthYear,
            onValueChange = viewModel::onBirthYear,
            label = stringResource(R.string.age_birth_year),
            placeholder = stringResource(R.string.age_birth_year_hint),
            supportingText = stringResource(R.string.age_birth_year_support),
            errorText = if (state.showErrors && !state.yearValid) stringResource(R.string.age_birth_year_invalid) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 19) {
            ConsentRow(
                checked = state.confirmedAdult,
                onChange = viewModel::onConfirmAdult,
                text = stringResource(R.string.age_confirm_adult),
            )
            ConsentRow(
                checked = state.acceptedTerms,
                onChange = viewModel::onAcceptTerms,
                text = stringResource(R.string.age_confirm_terms),
            )
            LegalButtons(onOpenTerms = onOpenTerms, onOpenPrivacy = onOpenPrivacy)
        }
        if (state.showErrors && (!state.confirmedAdult || !state.acceptedTerms)) {
            Text(
                "⚠ " + stringResource(R.string.age_must_confirm),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.error?.let {
            Text("⚠ " + stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        CrayonButton(
            text = stringResource(R.string.age_continue),
            onClick = { viewModel.submit(mode) },
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConsentRow(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    val crayon = MeyouwwTheme.crayon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .padding(vertical = 4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = crayon.outline,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
