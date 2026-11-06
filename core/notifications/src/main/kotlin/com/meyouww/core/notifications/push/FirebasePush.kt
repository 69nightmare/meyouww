package com.meyouww.core.notifications.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.meyouww.core.data.BackendConfig
import com.meyouww.core.data.device.PushTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initializes Firebase from build settings instead of google-services.json,
 * so no Firebase file lives in the repository. Only Cloud Messaging is used:
 * no Analytics, Crashlytics or other Firebase products.
 */
object FirebaseBootstrap {
    @Synchronized
    fun initialize(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) return true
        if (!BackendConfig.isFirebaseConfigured) return false
        val options = FirebaseOptions.Builder()
            .setProjectId(BackendConfig.firebaseProjectId)
            .setApplicationId(BackendConfig.firebaseAppId)
            .setApiKey(BackendConfig.firebaseApiKey)
            .setGcmSenderId(BackendConfig.firebaseSenderId)
            .build()
        FirebaseApp.initializeApp(context, options)
        return true
    }
}

@Singleton
class FcmTokenProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PushTokenProvider {

    override suspend fun currentToken(): String? {
        if (!FirebaseBootstrap.initialize(context)) return null
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // Play services missing or temporarily unavailable; the next sync retries.
        }
    }

    override suspend fun deleteToken() {
        if (!FirebaseBootstrap.initialize(context)) return
        FirebaseMessaging.getInstance().deleteToken().await()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun bindPushTokenProvider(impl: FcmTokenProvider): PushTokenProvider
}
