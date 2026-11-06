package com.meyouww.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.auth.AccountSession
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.auth.SessionState
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.model.OnboardingStep
import com.meyouww.core.model.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Which part of the app to show. */
enum class AppPhase { Loading, SignedOut, NeedsAgeGate, NeedsProfile, Ready }

/** Pure mapping, unit-tested. A profile that can't be loaded (offline) still lets a signed-in person in. */
internal fun phaseFor(profile: Profile?, loadFailed: Boolean): AppPhase {
    if (loadFailed && profile == null) return AppPhase.Ready
    return when (OnboardingStep.from(signedIn = true, profile = profile)) {
        OnboardingStep.AGE_GATE -> AppPhase.NeedsAgeGate
        OnboardingStep.PROFILE -> AppPhase.NeedsProfile
        else -> AppPhase.Ready
    }
}

@HiltViewModel
class AppViewModel @Inject constructor(
    auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val account: AccountSession,
) : ViewModel() {

    private val recheckTick = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val phase: StateFlow<AppPhase> = combine(auth.sessionState.distinctUntilChanged(), recheckTick) { session, _ -> session }
        .mapLatest { session ->
            when (session) {
                SessionState.Loading -> AppPhase.Loading
                SessionState.SignedOut, SessionState.NotConfigured -> AppPhase.SignedOut
                is SessionState.SignedIn -> resolveSignedIn()
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPhase.Loading)

    private suspend fun resolveSignedIn(): AppPhase {
        // Records any age-gate answers given before sign-in and registers this phone,
        // before the profile is read, so the age gate is never asked twice.
        account.afterSignIn()
        return when (val outcome = profiles.refresh()) {
            is Outcome.Success -> phaseFor(outcome.value, loadFailed = false)
            is Outcome.Failure -> phaseFor(profiles.profile.value, loadFailed = true)
        }
    }

    /** Called after the age gate or profile setup completes. */
    fun recheck() {
        recheckTick.value++
    }
}
