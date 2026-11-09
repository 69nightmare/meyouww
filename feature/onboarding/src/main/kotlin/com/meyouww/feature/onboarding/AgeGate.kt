package com.meyouww.feature.onboarding

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
