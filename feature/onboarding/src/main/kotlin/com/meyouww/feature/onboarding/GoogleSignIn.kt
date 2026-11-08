package com.meyouww.feature.onboarding

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.meyouww.core.model.SignInNonce

/** What happened when the person tapped "Continue with Google". */
sealed interface GoogleSignInResult {
    /** [email] is the Google account picked, used to ask that same account for age and gender. */
    data class Token(val idToken: String, val rawNonce: String, val email: String? = null) : GoogleSignInResult

    /** They closed the Google sheet. Nothing to say. */
    data object Cancelled : GoogleSignInResult

    /** No Google account on this phone (or Google Play services missing). */
    data object NoAccount : GoogleSignInResult

    data object Failed : GoogleSignInResult
}

/**
 * Shows Google's own account picker through Credential Manager and returns an
 * ID token for meyouww's web OAuth client. Must be called with an Activity
 * context. meyouww never sees the Google password.
 */
object GoogleSignIn {
    suspend fun requestIdToken(activityContext: Context, webClientId: String): GoogleSignInResult {
        val nonce = SignInNonce.create()
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(nonce.hashed)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Token(google.idToken, nonce.raw, google.id)
            } else {
                GoogleSignInResult.Failed
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.NoAccount
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed
        }
    }
}
