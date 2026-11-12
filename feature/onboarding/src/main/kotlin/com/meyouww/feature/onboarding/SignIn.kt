package com.meyouww.feature.onboarding

import com.meyouww.core.designsystem.component.PastelIcons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
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

    /** This phone was signed out because the account signed in on another phone. */
    val replacedElsewhere = prefs.replacedElsewhere
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
            // A fresh sign-in makes this the account's only phone (set before the session appears).
            prefs.setClaimDevicePending(true)
            when (val outcome = auth.verifyCode(s.email, s.code)) {
                // The app reacts to the new session: it records the age gate and registers this phone.
                is Outcome.Success -> _state.update { it.copy(busy = false, signedIn = true) }
                is Outcome.Failure -> {
                    prefs.setClaimDevicePending(false)
                    _state.update { it.copy(busy = false, error = outcome.error, code = "") }
                }
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
            // Never signs in without Google's birthday (see onGoogleAccess).
            is GoogleSignInResult.Token -> {
                pendingGoogle = null
                _state.update { it.copy(googleBusy = false, googleMessage = R.string.signin_google_needs_birthday) }
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
     * meyouww is 18+ and Google is the only age check (the owner removed the typed age
     * gate), so without an adult birthday from Google nobody is signed in.
     */
    fun onGoogleAccess(accessToken: String?) = viewModelScope.launch {
        val details = accessToken?.let { GoogleAccess.details(it) }
        val age = details?.let { GooglePeople.age(it, LocalDate.now()) } ?: GooglePeople.Age.Unknown
        if (age == GooglePeople.Age.Underage) {
            pendingGoogle = null
            _state.update { it.copy(googleBusy = false, underage = true) }
            return@launch
        }
        val year = details?.let { it.birthday?.year ?: it.birthYear }
        if (age != GooglePeople.Age.Adult || year == null) {
            pendingGoogle = null
            _state.update { it.copy(googleBusy = false, googleMessage = R.string.signin_google_needs_birthday) }
            return@launch
        }
        prefs.savePendingAgeGate(PendingAgeGate(year, true, System.currentTimeMillis()))
        details?.gender?.let { prefs.savePendingGender(it) }
        finishGoogle()
    }

    private fun finishGoogle() {
        val token = pendingGoogle ?: return
        pendingGoogle = null
        viewModelScope.launch {
            // A fresh sign-in makes this the account's only phone (set before the session appears).
            prefs.setClaimDevicePending(true)
            prefs.setReplacedElsewhere(false)
            when (val outcome = auth.signInWithGoogle(token.idToken, token.rawNonce)) {
                // As with email, the app moves on by itself once the session exists.
                is Outcome.Success -> _state.update { it.copy(googleBusy = false, signedIn = true) }
                is Outcome.Failure -> {
                    prefs.setClaimDevicePending(false)
                    _state.update { it.copy(googleBusy = false, error = outcome.error) }
                }
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

/**
 * Sign in with Google (the only way in; every account is a Google account). The email-code
 * form only appears in a build without Google configured, for development.
 */
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
    CrayonScreen(title = stringResource(if (state.codeSent) R.string.signin_code_title else R.string.signin_title)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(170.dp).background(crayon.blue, CircleShape),
        ) {
            Sticker(if (state.codeSent) WhitySticker.Peek else WhitySticker.ToonHiIAmHere, decorative = true, size = 150.dp)
        }
        val replaced by viewModel.replacedElsewhere.collectAsStateWithLifecycle(initialValue = false)
        if (replaced) {
            CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 902) {
                Text(stringResource(R.string.signin_replaced_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.signin_replaced_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
        CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 901) {
            if (!state.codeSent) {
                // No age form: Google's birthday is the age check (see SignInViewModel.onGoogleAccess).
                if (!viewModel.googleAvailable) {
                    Text(stringResource(R.string.signin_terms), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft, textAlign = TextAlign.Center)
                    LegalButtons(onOpenTerms = onOpenTerms, onOpenPrivacy = onOpenPrivacy)
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
                        enabled = !state.anyBusy,
                        loading = state.googleBusy,
                        loadingDescription = stringResource(R.string.signin_google_busy),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.googleMessage?.let {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(crayon.errorFill, RoundedCornerShape(18.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                        ) {
                            Icon(PastelIcons.Alert, contentDescription = null, tint = crayon.onError)
                            Text(stringResource(it), color = crayon.onError, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(crayon.green, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(PastelIcons.Shield, contentDescription = null, tint = crayon.onFill)
                        Text(stringResource(R.string.signin_birthday_note), style = MaterialTheme.typography.bodyMedium, color = crayon.onFill)
                    }
                    Text(stringResource(R.string.signin_google_only), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.signin_terms), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft, textAlign = TextAlign.Center)
                    LegalButtons(onOpenTerms = onOpenTerms, onOpenPrivacy = onOpenPrivacy)
                    return@CrayonCard
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
