package com.meyouww.core.data

/**
 * Backend settings baked into this build from local.properties or CI secrets.
 * None of these are secrets: the Supabase publishable key and Firebase client
 * settings are designed to ship inside apps. Row Level Security protects data.
 */
object BackendConfig {
    val supabaseUrl: String = BuildConfig.SUPABASE_URL
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    val firebaseProjectId: String = BuildConfig.FIREBASE_PROJECT_ID
    val firebaseAppId: String = BuildConfig.FIREBASE_APP_ID
    val firebaseApiKey: String = BuildConfig.FIREBASE_API_KEY
    val firebaseSenderId: String = BuildConfig.FIREBASE_SENDER_ID

    /** The Google Cloud "web" OAuth client that Supabase accepts ID tokens for. */
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val isGoogleSignInConfigured: Boolean
        get() = isSupabaseConfigured && googleWebClientId.endsWith(".apps.googleusercontent.com")

    val isSupabaseConfigured: Boolean
        get() = supabaseUrl.startsWith("https://") && supabasePublishableKey.isNotBlank()

    val isFirebaseConfigured: Boolean
        get() = listOf(firebaseProjectId, firebaseAppId, firebaseApiKey, firebaseSenderId).all { it.isNotBlank() }
}
