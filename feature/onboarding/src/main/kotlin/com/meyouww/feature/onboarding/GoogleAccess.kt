package com.meyouww.feature.onboarding

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.meyouww.core.model.GoogleDetails
import com.meyouww.core.model.GooglePeople
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Asks Google, with the person's OK on Google's own screen, for their birthday and
 * gender, so they don't have to type them. Only these two read-only scopes; the
 * access token is used once, here on the phone, and never stored or sent anywhere.
 */
object GoogleAccess {
    private fun request(email: String?): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GooglePeople.SCOPE_BIRTHDAY), Scope(GooglePeople.SCOPE_GENDER)))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()

    /** Null when Google can't be asked at all; otherwise may need Google's consent screen first. */
    suspend fun authorize(context: Context, email: String?): AuthorizationResult? = suspendCancellableCoroutine { cont ->
        try {
            Identity.getAuthorizationClient(context).authorize(request(email))
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** The token from Google's consent screen, or null if they said no. */
    fun tokenFrom(context: Context, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    /** Reads the birthday and gender. Null on any problem (the age question is asked by hand instead). */
    suspend fun details(accessToken: String): GoogleDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(GooglePeople.ME_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                if (connection.responseCode != 200) return@runCatching null
                GooglePeople.parse(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
