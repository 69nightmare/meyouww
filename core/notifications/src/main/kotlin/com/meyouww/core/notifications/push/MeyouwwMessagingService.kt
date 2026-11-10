package com.meyouww.core.notifications.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.meyouww.core.alarm.AlarmPlanner
import com.meyouww.core.alarm.AlarmScheduler
import com.meyouww.core.alarm.AlarmSyncWorker
import com.meyouww.core.alarm.CalendarSyncWorker
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.work.DeviceSyncWorker
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.notifications.R
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
    private val friends: FriendsRepository,
    private val notifications: AndroidPushNotifications,
    private val auth: AuthRepository,
    private val activities: ActivityRepository,
    private val alarms: AlarmRepository,
) : PushWork {
    override fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long) =
        DeliveryTestWorker.enqueue(context, testId, sentAtServerMs, receivedAtMs)

    /** Who sent it and the Accept / Remove buttons, right away; the worker only if this can't finish. */
    override fun processActivity(activityId: String) {
        if (!inline { activityNow(activityId) }) ActivityWorker.enqueue(context, activityId)
    }

    private suspend fun activityNow(id: String): Boolean {
        if (!auth.ensureFreshSession()) return false
        val outcome = activities.get(id) as? Outcome.Success ?: return false
        val item = outcome.value
        if (item == null) {
            // Taken back (a cancelled request) or dismissed elsewhere.
            notifications.cancelActivity(id)
            return true
        }
        notifications.showActivityDetails(item)
        // An alarm set for you is armed on this phone straight away.
        if (item.kindValue == ActivityKind.AlarmAdded && !alarmsNow()) AlarmSyncWorker.enqueue(context)
        if (item.kindValue == ActivityKind.EventAdded || item.kindValue == ActivityKind.EventCancelled) CalendarSyncWorker.enqueue(context)
        activities.refreshUnread()
        return true
    }

    /** Fetches this phone's alarms and arms them. False if that couldn't be done now. */
    private suspend fun alarmsNow(): Boolean {
        if (!auth.ensureFreshSession()) return false
        val list = (alarms.mine() as? Outcome.Success)?.value ?: return false
        AlarmScheduler(context).replaceAll(list.mapNotNull(AlarmPlanner::toLocal))
        return true
    }

    /** Runs [block] inside the push's few seconds; false if it timed out or failed. */
    private fun inline(block: suspend () -> Boolean): Boolean =
        runCatching { runBlocking { withTimeoutOrNull(INLINE_BUDGET_MS) { block() } } }.getOrNull() == true

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

    override fun applyRinger(mode: String, fromUserId: String) {
        val previous = RingerControl.current(context)
        val applied = RingerControl.apply(context, mode)
        val name = friends.friends.value?.firstOrNull { it.id == fromUserId }?.card?.name
            ?: runCatching {
                runBlocking {
                    withTimeoutOrNull(5_000) {
                        (friends.refreshFriends() as? Outcome.Success)?.value?.firstOrNull { it.id == fromUserId }?.card?.name
                    }
                }
            }.getOrNull()
            ?: context.getString(R.string.ringer_someone)
        notifications.showRingerChanged(name, mode, previous, applied)
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

    override fun tidyMessagesLater() = ChatFetchWorker.enqueueLater(context)

    /** Added to a group, or its members changed: the chat list shows it straight away. */
    override fun processGroups() {
        if (!inline { inbox.refreshGroups() }) ChatFetchWorker.enqueue(context)
    }

    override fun syncAlarms() {
        if (!inline { alarmsNow() }) AlarmSyncWorker.enqueue(context)
    }

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
