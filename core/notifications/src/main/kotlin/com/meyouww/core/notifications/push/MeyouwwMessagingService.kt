package com.meyouww.core.notifications.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meyouww.core.data.work.DeviceSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Hands push follow-ups to WorkManager. Enqueueing is a quick local write. */
@Singleton
class WorkManagerPushWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PushWork {
    override fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long) =
        DeliveryTestWorker.enqueue(context, testId, sentAtServerMs, receivedAtMs)

    override fun processActivity(activityId: String) = ActivityWorker.enqueue(context, activityId)

    override fun processWallpaper(commandId: String) = WallpaperWorker.enqueue(context, commandId, approved = false)

    override fun sync() = DeviceSyncWorker.enqueueNow(context)
}

/**
 * Receives data-only FCM messages, including when the app is closed (FCM
 * starts the process if needed). Returns quickly: see [PushHandler].
 */
@AndroidEntryPoint
class MeyouwwMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notifications: AndroidPushNotifications

    @Inject lateinit var work: WorkManagerPushWork

    private val handler by lazy { PushHandler(notifications, work) }

    override fun onMessageReceived(message: RemoteMessage) {
        // The server's display-only copy (generic text, no data) reaches us only while the app
        // is open; the data message that comes with it does the real work.
        if (message.notification != null && message.data.isEmpty()) return
        handler.onMessage(message.data, System.currentTimeMillis())
    }

    override fun onNewToken(token: String) {
        // The token itself is read again by the worker; never log it.
        handler.onNewToken()
    }

    override fun onDeletedMessages() {
        handler.onDeletedMessages()
    }
}
