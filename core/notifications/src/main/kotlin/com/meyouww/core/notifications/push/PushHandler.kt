package com.meyouww.core.notifications.push

/** Posts notifications. Must be local-only: no network calls. */
interface PushNotifications {
    fun showDeliveryTestArrived(testId: String)
}

/** Hands follow-up work to WorkManager. Enqueueing is fast and local. */
interface PushWork {
    fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long)
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
