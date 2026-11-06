package com.meyouww.core.notifications.push

/** Posts notifications. Must be local-only: no network calls. */
interface PushNotifications {
    fun showDeliveryTestArrived(testId: String)

    /** A generic heart-inbox notification, posted before any network call. */
    fun showActivity(activityId: String, kind: String)

    /** "Someone is decorating your phone…", posted before any network call. */
    fun showWallpaperIncoming(commandId: String)
}

/** Hands follow-up work to WorkManager. Enqueueing is fast and local. */
interface PushWork {
    fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long)

    /** Fetches who and what, then updates the notification. */
    fun processActivity(activityId: String)

    /** Fetches, checks and applies an automatic wallpaper. */
    fun processWallpaper(commandId: String)
    fun sync()
}

/**
 * The logic behind onMessageReceived, kept free of Android types so it can be
 * unit-tested.
 *
 * Rule: notification first, work second. FCM gives the app only a few seconds
 * and deprioritises high-priority messages that do not lead to a visible
 * notification, so anything visible is posted immediately from local data and
 * every network or heavy step goes to an expedited WorkManager job.
 */
class PushHandler(
    private val notifications: PushNotifications,
    private val work: PushWork,
) {
    fun onMessage(data: Map<String, String>, receivedAtMs: Long) {
        when (val event = PushEvent.parse(data)) {
            is PushEvent.DeliveryTest -> {
                notifications.showDeliveryTestArrived(event.testId)
                work.processDeliveryTest(event.testId, event.sentAtServerMs, receivedAtMs)
            }
            is PushEvent.Activity -> {
                notifications.showActivity(event.activityId, event.kind)
                work.processActivity(event.activityId)
            }
            is PushEvent.Wallpaper -> {
                notifications.showWallpaperIncoming(event.commandId)
                work.processWallpaper(event.commandId)
            }
            PushEvent.Sync -> work.sync()
            is PushEvent.Unknown -> work.sync()
        }
    }

    fun onNewToken() = work.sync()

    /** FCM dropped messages while the phone was away: the server is the truth, so sync. */
    fun onDeletedMessages() = work.sync()

    companion object {
        /** Arrival latency corrected by the measured server-minus-device clock offset. Never negative. */
        fun latencyMs(receivedAtDeviceMs: Long, clockOffsetMs: Long, sentAtServerMs: Long): Long =
            (receivedAtDeviceMs + clockOffsetMs - sentAtServerMs).coerceAtLeast(0L)
    }
}
