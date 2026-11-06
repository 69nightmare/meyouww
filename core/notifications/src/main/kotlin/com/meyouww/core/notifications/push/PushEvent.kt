package com.meyouww.core.notifications.push

/**
 * A data-only FCM message. The payload carries opaque IDs only: never message
 * text, names or previews. Anything the app shows is looked up locally or
 * fetched over authenticated HTTPS.
 */
sealed interface PushEvent {
    /** The background-delivery self-test. */
    data class DeliveryTest(val testId: String, val sentAtServerMs: Long) : PushEvent

    /** Something new in the heart inbox. [kind] picks the wording; names are fetched later. */
    data class Activity(val activityId: String, val kind: String) : PushEvent

    /** A friend sent a wallpaper that applies automatically: fetch, check and apply it. */
    data class Wallpaper(val commandId: String) : PushEvent

    /** An encrypted chat message is waiting: fetch and decrypt it. */
    data class Message(val conversationId: String) : PushEvent

    /** Something changed on the server; sync when convenient. */
    data object Sync : PushEvent

    /** A type this app version does not know yet (sent by a newer server). */
    data class Unknown(val type: String?) : PushEvent

    companion object {
        const val KEY_TYPE = "type"
        const val TYPE_DELIVERY_TEST = "delivery_test"
        const val TYPE_SYNC = "sync"
        const val TYPE_ACTIVITY = "activity"
        const val TYPE_WALLPAPER = "wallpaper"
        const val TYPE_MESSAGE = "message"
        private val ID = Regex("^[0-9a-fA-F-]{36}$")

        fun parse(data: Map<String, String>): PushEvent = when (val type = data[KEY_TYPE]) {
            TYPE_DELIVERY_TEST -> {
                val id = data["test_id"]
                val sentAt = data["sent_at_ms"]?.toLongOrNull()
                if (id.isNullOrBlank() || sentAt == null) Unknown(type) else DeliveryTest(id, sentAt)
            }
            TYPE_ACTIVITY -> {
                val id = data["activity_id"]
                if (id == null || !ID.matches(id)) Unknown(type) else Activity(id, data["kind"].orEmpty().take(40))
            }
            TYPE_WALLPAPER -> {
                val id = data["command_id"]
                if (id == null || !ID.matches(id)) Unknown(type) else Wallpaper(id)
            }
            TYPE_MESSAGE -> {
                val id = data["conversation_id"]
                if (id == null || !ID.matches(id)) Unknown(type) else Message(id)
            }
            TYPE_SYNC -> Sync
            else -> Unknown(type)
        }
    }
}
