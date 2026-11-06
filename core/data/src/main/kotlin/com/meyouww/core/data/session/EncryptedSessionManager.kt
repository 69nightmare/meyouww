package com.meyouww.core.data.session

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionStore by preferencesDataStore(name = "meyouww_session")

/**
 * Stores the Supabase session encrypted with Tink AEAD in DataStore.
 * Replaces the deprecated EncryptedSharedPreferences approach.
 */
@Singleton
class EncryptedSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aeadProvider: AeadProvider,
) : SessionManager {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("session_v1")
    private val associatedData = "meyouww-supabase-session".encodeToByteArray()

    override suspend fun saveSession(session: UserSession) {
        val plaintext = json.encodeToString(UserSession.serializer(), session).encodeToByteArray()
        val ciphertext = aeadProvider.aead.encrypt(plaintext, associatedData)
        val encoded = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        context.sessionStore.edit { it[key] = encoded }
    }

    override suspend fun loadSession(): UserSession? {
        val encoded = context.sessionStore.data.first()[key] ?: return null
        return runCatching {
            val plaintext = aeadProvider.aead.decrypt(Base64.decode(encoded, Base64.NO_WRAP), associatedData)
            json.decodeFromString(UserSession.serializer(), plaintext.decodeToString())
        }.getOrElse {
            // Corrupt or undecryptable (for example after a Keystore reset): sign out cleanly.
            deleteSession()
            null
        }
    }

    override suspend fun deleteSession() {
        context.sessionStore.edit { it.remove(key) }
    }
}
