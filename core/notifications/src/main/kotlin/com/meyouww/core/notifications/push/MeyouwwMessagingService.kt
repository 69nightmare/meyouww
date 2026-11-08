package com.meyouww.core.notifications.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meyouww.core.alarm.AlarmSyncWorker
import com.meyouww.core.data.work.DeviceSyncWorker
import com.meyouww.core.notifications.chat.ChatFetchWorker
import com.meyouww.core.notifications.chat.ChatInbox
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Hands push follow-ups to WorkManager. Enqueueing is a quick local write. */
@Singleton
class WorkManagerPushWork @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val delivery: WallpaperDelivery,
    private val inbox: ChatInbox,
) : PushWork {
    override fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long) =
        DeliveryTestWorker.enqueue(context, testId, sentAtServerMs, receivedAtMs)

    override fun processActivity(activityId: String) = ActivityWorker.enqueue(context, activityId)

    /**
     * A wallpaper goes on right now, from inside the push: FCM keeps the phone awake
     * (with network) for a few seconds, locked or not, even if meyouww was closed.
     * WorkManager alone could wait until the app is opened on some phones. Anything
     * left unfinished (no network yet, a slow download) falls back to the worker.
     */
    override fun processWallpaper(commandId: String) {
        val step = runCatching {
            runBlocking { withTimeoutOrNull(INLINE_BUDGET_MS) { delivery.process(commandId, approved = false, target = null, canRetry = true) } }
        }.getOrNull()
        if (step != WallpaperDelivery.Step.Done) WallpaperWorker.enqueue(context, commandId, approved = false)
    }

    private companion object {
        /** FCM allows about 20 s per message; stay well inside it. */
        const val INLINE_BUDGET_MS = 15_000L
    }

    /** Same idea as wallpapers: messages (and their "delivered" receipts) right away, worker as fallback. */
    override fun processMessages() {
        val step = runCatching { runBlocking { withTimeoutOrNull(INLINE_BUDGET_MS) { inbox.fetch() } } }.getOrNull()
        if (step != ChatInbox.Step.Done) ChatFetchWorker.enqueue(context)
    }

    override fun syncAlarms() = AlarmSyncWorker.enqueue(context)

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
        // The server's display-only copy (generic text; its data only says which screen a tap
        // opens) reaches us only while the app is open; the data message does the real work.
        if (message.notification != null) return
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
