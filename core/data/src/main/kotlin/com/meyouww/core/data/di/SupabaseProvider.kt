package com.meyouww.core.data.di

import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.common.BackendNotConfiguredException
import com.meyouww.core.data.session.EncryptedSessionManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single Supabase client. It is created lazily so that a push that
 * cold-starts the process does not pay for it unless a worker needs it.
 */
@Singleton
class SupabaseProvider @Inject constructor(
    private val sessionManager: EncryptedSessionManager,
) {
    val isConfigured: Boolean get() = BackendConfig.isSupabaseConfigured

    private val lazyClient: SupabaseClient? by lazy {
        if (!isConfigured) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = BackendConfig.supabaseUrl,
                supabaseKey = BackendConfig.supabasePublishableKey,
            ) {
                install(Auth) {
                    sessionManager = this@SupabaseProvider.sessionManager
                    autoLoadFromStorage = true
                    alwaysAutoRefresh = true
                }
                install(Postgrest)
                install(Functions)
            }
        }
    }

    /** The client, or [BackendNotConfiguredException] if this build has no backend settings. */
    fun client(): SupabaseClient = lazyClient ?: throw BackendNotConfiguredException()

    fun clientOrNull(): SupabaseClient? = lazyClient
}
