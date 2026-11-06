package com.meyouww.core.data.auth

import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object NotConfigured : SessionState
    data class SignedIn(val userId: String, val email: String?) : SessionState
}

/** Passwordless email sign-in with a 6-digit code (Supabase Auth). */
@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    val sessionState: Flow<SessionState>
        get() {
            val client = supabase.clientOrNull() ?: return flowOf(SessionState.NotConfigured)
            return client.auth.sessionStatus.map { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val user = status.session.user
                        if (user != null) SessionState.SignedIn(user.id, user.email) else fromCurrent()
                    }
                    is SessionStatus.NotAuthenticated -> SessionState.SignedOut
                    is SessionStatus.Initializing -> SessionState.Loading
                    else -> fromCurrent()
                }
            }
        }

    private fun fromCurrent(): SessionState {
        val user = supabase.clientOrNull()?.auth?.currentUserOrNull() ?: return SessionState.SignedOut
        return SessionState.SignedIn(user.id, user.email)
    }

    fun currentUserId(): String? = supabase.clientOrNull()?.auth?.currentUserOrNull()?.id

    fun currentEmail(): String? = supabase.clientOrNull()?.auth?.currentUserOrNull()?.email

    suspend fun sendCode(email: String): Outcome<Unit> = outcomeOf(classify = ::classifySend) {
        supabase.client().auth.signInWith(OTP) {
            this.email = email.trim().lowercase()
            createUser = true
        }
    }

    suspend fun verifyCode(email: String, code: String): Outcome<Unit> = outcomeOf(classify = ::classifyVerify) {
        supabase.client().auth.verifyEmailOtp(
            type = OtpType.Email.EMAIL,
            email = email.trim().lowercase(),
            token = code.trim(),
        )
    }

    /**
     * Makes sure a usable access token exists, refreshing it if it expires
     * within a minute. Safe to call from background workers with no UI.
     */
    suspend fun ensureFreshSession(): Boolean {
        val client = supabase.clientOrNull() ?: return false
        return try {
            client.auth.awaitInitialization()
            val session = client.auth.currentSessionOrNull() ?: return false
            val nowSeconds = System.currentTimeMillis() / 1000
            if (session.expiresAt.epochSeconds < nowSeconds + 60) {
                client.auth.refreshCurrentSession()
            }
            client.auth.currentSessionOrNull() != null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            client.auth.currentSessionOrNull() != null
        }
    }

    /** Signs out on the server when reachable, and always clears the local session. */
    suspend fun signOut(): Outcome<Unit> = outcomeOf {
        val client = supabase.client()
        try {
            client.auth.signOut()
        } finally {
            client.auth.clearSession()
        }
    }

    private fun classifySend(e: Throwable): AppError? =
        (e as? RestException)?.let { if (it.statusCode == 429) AppError.RateLimited else null }

    private fun classifyVerify(e: Throwable): AppError? = (e as? RestException)?.let {
        when (it.statusCode) {
            429 -> AppError.RateLimited
            400, 401, 403, 422 -> AppError.InvalidCode
            else -> null
        }
    }
}
