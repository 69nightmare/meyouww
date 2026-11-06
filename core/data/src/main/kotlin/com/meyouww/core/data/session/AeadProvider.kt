package com.meyouww.core.data.session

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tink AEAD whose keyset is wrapped by an Android Keystore master key.
 *
 * The master key is created by Tink without "user authentication required" or
 * "unlocked device required", on purpose: background workers must be able to
 * read the login session while the phone is locked (after the first unlock
 * since boot). The optional app lock only protects the UI.
 */
@Singleton
class AeadProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private companion object {
        const val KEYSET_NAME = "meyouww_session_keyset"
        const val KEYSET_PREFS = "meyouww_session_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://meyouww_session_master_key"
    }
}
