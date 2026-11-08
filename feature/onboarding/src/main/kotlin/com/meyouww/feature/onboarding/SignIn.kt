package com.meyouww.feature.onboarding

import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.prefs.PendingAgeGate
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.GooglePeople
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SignInUiState(
    val email: String = "",
    val code: String = "",
    val codeSent: Boolean = false,
    val busy: Boolean = false,
    val emailInvalid: Boolean = false,
    val error: AppError? = null,
    val resendInSeconds: Int = 0,
    val resent: Boolean = false,
    val signedIn: Boolean = false,
    val googleBusy: Boolean = false,
    /** A Google-specific problem to explain (string resource), separate from server errors. */
    val googleMessage: Int? = null,
    /** Google says they are under 18: meyouww is for adults only. */
    val underage: Boolean = false,
) {
    val anyBusy: Boolean get() = busy || googleBusy
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val prefs: AppPreferences,
) : ViewModel() {
    /** The Google sign-in waiting while Google is asked for age and gender. */
    private var pendingGoogle: GoogleSignInResult.Token? = null
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()
    private var countdown: Job? = null

    fun onEmail(value: String) = _state.update { it.copy(email = value.trim(), emailInvalid = false, error = null) }

    fun onCode(value: String) {
        val digits = value.filter(Char::isDigit).take(CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null, resent = false) }
        if (digits.length == CODE_LENGTH && !_state.value.busy) verify()
    }

    fun sendCode(isResend: Boolean = false) {
        val email = _state.value.email
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _state.update { it.copy(emailInvalid = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val outcome = auth.sendCode(email)) {
                is Outcome.Success -> {
                    _state.update { it.copy(busy = false, codeSent = true, code = "", resent = isResend) }
                    startCountdown()
                }
                is Outcome.Failure -> _state.update { it.copy(busy = false, error = outcome.error) }
            }
        }
    }

    fun verify() {
        val s = _state.value
        if (s.code.length != CODE_LENGTH) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            when (val outcome = auth.verifyCode(s.email, s.code)) {
                // The app reacts to the new session: it records the age gate and registers this phone.
                is Outcome.Success -> _state.update { it.copy(busy = false, signedIn = true) }
                is Outcome.Failure -> _state.update { it.copy(busy = false, error = outcome.error, code = "") }
            }
        }
    }

    val googleAvailable: Boolean get() = BackendConfig.isGoogleSignInConfigured
    val googleWebClientId: String get() = BackendConfig.googleWebClientId

    fun onGoogleStarted() = _state.update { it.copy(googleBusy = true, error = null, googleMessage = null) }

    /** Called with the outcome of Google's account picker. */
    fun onGoogleResult(result: GoogleSignInResult) {
        when (result) {
            GoogleSignInResult.Cancelled -> _state.update { it.copy(googleBusy = false) }
            GoogleSignInResult.NoAccount -> _state.update { it.copy(googleBusy = false, googleMessage = R.string.signin_google_no_account) }
            GoogleSignInResult.Failed -> _state.update { it.copy(googleBusy = false, googleMessage = R.string.signin_google_failed) }
            is GoogleSignInResult.Token -> {
                pendingGoogle = result
                finishGoogle()
            }
        }
    }

    /** Step 2 of Google sign-in: remember the account while Google is asked for age and gender. */
    fun onGoogleAccount(token: GoogleSignInResult.Token) {
        pendingGoogle = token
    }

    /**
     * Step 3: with the access token (null if they said no or Google couldn't be asked),
     * read the birthday and gender, keep them for right after sign-in, then sign in.
     * Without a birthday, the age question is simply asked by hand afterwards.
     */
    fun onGoogleAccess(accessToken: String?) = viewModelScope.launch {
        val details = accessToken?.let { GoogleAccess.details(it) }
        if (details != null) {
            when (GooglePeople.age(details, LocalDate.now())) {
                GooglePeople.Age.Underage -> {
                    pendingGoogle = null
                    _state.update { it.copy(googleBusy = false, underage = true) }
                    return@launch
                }
                GooglePeople.Age.Adult -> {
                    val year = details.birthday?.year ?: details.birthYear
                    if (year != null) prefs.savePendingAgeGate(PendingAgeGate(year, true, System.currentTimeMillis()))
                }
                GooglePeople.Age.Unknown -> Unit
            }
            details.gender?.let { prefs.savePendingGender(it) }
        }
        finishGoogle()
    }

    private fun finishGoogle() {
        val token = pendingGoogle ?: return
        pendingGoogle = null
        viewModelScope.launch {
            when (val outcome = auth.signInWithGoogle(token.idToken, token.rawNonce)) {
                // As with email, the app moves on by itself once the session exists.
                is Outcome.Success -> _state.update { it.copy(googleBusy = false, signedIn = true) }
                is Outcome.Failure -> _state.update { it.copy(googleBusy = false, error = outcome.error) }
            }
        }
    }

    fun changeEmail() {
        countdown?.cancel()
        _state.update { SignInUiState(email = it.email) }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (s in RESEND_SECONDS downTo 0) {
                _state.update { it.copy(resendInSeconds = s) }
                if (s > 0) delay(1_000)
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val RESEND_SECONDS = 60
    }
}

/** Email, then the 6-digit code. Successful verification creates the account if it is new. */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    viewModel: SignInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    val crayon = MeyouwwTheme.crayon
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
        Sticker(if (state.codeSent) WhitySticker.Peek else WhitySticker.Hi, decorative = true, size = 140.dp)
        if (!state.codeSent) {
            Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            // The age question used to come first; now Google answers it (or it is asked after sign-in).
            Text(stringResource(R.string.signin_terms), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenTerms) { Text(stringResource(R.string.age_open_terms), color = crayon.link) }
                TextButton(onClick = onOpenPrivacy) { Text(stringResource(R.string.age_open_privacy), color = crayon.link) }
            }
            if (viewModel.googleAvailable) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                // Google's own "share your birthday and gender with meyouww?" screen.
                val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                    viewModel.onGoogleAccess(GoogleAccess.tokenFrom(context, result.data))
                }
                CrayonButton(
                    text = stringResource(R.string.signin_google),
                    onClick = {
                        viewModel.onGoogleStarted()
                        scope.launch {
                            when (val picked = GoogleSignIn.requestIdToken(context, viewModel.googleWebClientId)) {
                                is GoogleSignInResult.Token -> {
                                    viewModel.onGoogleAccount(picked)
                                    val access = GoogleAccess.authorize(context, picked.email)
                                    val intent = access?.pendingIntent
                                    when {
                                        access == null -> viewModel.onGoogleAccess(null)
                                        access.hasResolution() && intent != null ->
                                            consent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                                        else -> viewModel.onGoogleAccess(access.accessToken)
                                    }
                                }
                                else -> viewModel.onGoogleResult(picked)
                            }
                        }
                    },
                    kind = ButtonKind.Quiet,
                    enabled = !state.anyBusy,
                    loading = state.googleBusy,
                    loadingDescription = stringResource(R.string.signin_google_busy),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.googleMessage?.let {
                    Text(
                        "⚠ " + stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                OrDivider()
            }
            Text(stringResource(R.string.signin_body), style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft, textAlign = TextAlign.Center)
            CrayonTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.signin_email),
                placeholder = stringResource(R.string.signin_email_hint),
                errorText = if (state.emailInvalid) stringResource(R.string.signin_email_invalid) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendCode() }),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorLine(state.error)
            CrayonButton(
                text = stringResource(R.string.signin_send_code),
                onClick = { viewModel.sendCode() },
                enabled = !state.googleBusy,
                loading = state.busy,
                loadingDescription = stringResource(R.string.signin_sending),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(stringResource(R.string.signin_code_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.signin_code_body, state.email),
                style = MaterialTheme.typography.bodyLarge,
                color = crayon.textSoft,
                textAlign = TextAlign.Center,
            )
            CrayonTextField(
                value = state.code,
                onValueChange = viewModel::onCode,
                label = stringResource(R.string.signin_code),
                placeholder = "••••••",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.verify() }),
                textStyle = TextStyle(fontSize = 28.sp, letterSpacing = 8.sp),
                modifier = Modifier.fillMaxWidth(),
            )
            ErrorLine(state.error)
            if (state.resent) {
                Text(
                    stringResource(R.string.signin_code_resent),
                    color = crayon.success,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            CrayonButton(
                text = stringResource(R.string.signin_verify),
                onClick = viewModel::verify,
                loading = state.busy,
                loadingDescription = stringResource(R.string.signin_verifying),
                enabled = state.code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            )
            CrayonButton(
                text = if (state.resendInSeconds > 0) {
                    stringResource(R.string.signin_resend_wait, state.resendInSeconds)
                } else {
                    stringResource(R.string.signin_resend)
                },
                onClick = { viewModel.sendCode(isResend = true) },
                enabled = state.resendInSeconds == 0 && !state.busy,
                kind = ButtonKind.Quiet,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = viewModel::changeEmail) {
                Text(stringResource(R.string.signin_change_email), color = crayon.link)
            }
        }
    }
}

@Composable
private fun OrDivider() {
    val crayon = MeyouwwTheme.crayon
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.weight(1f), color = crayon.outline.copy(alpha = 0.4f))
        Text(
            stringResource(R.string.signin_or_email),
            style = MaterialTheme.typography.labelMedium,
            color = crayon.textSoft,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = crayon.outline.copy(alpha = 0.4f))
    }
}

@Composable
internal fun ErrorLine(error: AppError?) {
    if (error == null) return
    Text(
        "⚠ " + stringResource(error.messageRes()),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive },
    )
}
