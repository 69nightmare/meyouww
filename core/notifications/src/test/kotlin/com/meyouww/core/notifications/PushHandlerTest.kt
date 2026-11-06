package com.meyouww.core.notifications

import com.google.common.truth.Truth.assertThat
import com.meyouww.core.notifications.push.PushEvent
import com.meyouww.core.notifications.push.PushHandler
import com.meyouww.core.notifications.push.PushNotifications
import com.meyouww.core.notifications.push.PushWork
import org.junit.Test

class PushHandlerTest {

    /** Records every call in order. The handler gets no network client at all. */
    private class Recorder : PushNotifications, PushWork {
        val calls = mutableListOf<String>()
        override fun showDeliveryTestArrived(testId: String) {
            calls += "notify:$testId"
        }
        override fun processDeliveryTest(testId: String, sentAtServerMs: Long, receivedAtMs: Long) {
            calls += "work:$testId:$sentAtServerMs:$receivedAtMs"
        }
        override fun sync() {
            calls += "sync"
        }
        override fun showActivity(activityId: String, kind: String) {
            calls += "notify-activity:$activityId:$kind"
        }
        override fun processActivity(activityId: String) {
            calls += "work-activity:$activityId"
        }
        override fun showWallpaperIncoming(commandId: String) {
            calls += "notify-wallpaper:$commandId"
        }
        override fun processWallpaper(commandId: String) {
            calls += "work-wallpaper:$commandId"
        }
    }

    private val recorder = Recorder()
    private val handler = PushHandler(recorder, recorder)

    @Test
    fun `delivery test posts the notification before any work is scheduled`() {
        handler.onMessage(mapOf("type" to "delivery_test", "test_id" to "t1", "sent_at_ms" to "1000"), receivedAtMs = 3100)
        assertThat(recorder.calls).containsExactly("notify:t1", "work:t1:1000:3100").inOrder()
    }

    @Test
    fun `inbox pushes notify first, then fetch the details`() {
        val id = "aaaaaaaa-0000-0000-0000-000000000001"
        handler.onMessage(mapOf("type" to "activity", "activity_id" to id, "kind" to "friend_request", "v" to "1"), 0)
        assertThat(recorder.calls).containsExactly("notify-activity:$id:friend_request", "work-activity:$id").inOrder()
    }

    @Test
    fun `inbox pushes without a valid id are treated as unknown`() {
        handler.onMessage(mapOf("type" to "activity", "activity_id" to "../../etc", "kind" to "friend_request"), 0)
        handler.onMessage(mapOf("type" to "activity"), 0)
        assertThat(recorder.calls).containsExactly("sync", "sync")
    }

    @Test
    fun `automatic wallpapers announce themselves before any network call`() {
        val id = "cccccccc-0000-0000-0000-000000000003"
        handler.onMessage(mapOf("type" to "wallpaper", "command_id" to id, "mode" to "auto"), 0)
        assertThat(recorder.calls).containsExactly("notify-wallpaper:$id", "work-wallpaper:$id").inOrder()
    }

    @Test
    fun `sync messages only schedule work`() {
        handler.onMessage(mapOf("type" to "sync"), 0)
        assertThat(recorder.calls).containsExactly("sync")
    }

    @Test
    fun `unknown or malformed messages fall back to a sync and never crash`() {
        handler.onMessage(mapOf("type" to "from_the_future"), 0)
        handler.onMessage(emptyMap(), 0)
        handler.onMessage(mapOf("type" to "delivery_test", "test_id" to "t2"), 0) // missing sent_at_ms
        assertThat(recorder.calls).containsExactly("sync", "sync", "sync")
    }

    @Test
    fun `new token and deleted messages trigger a sync`() {
        handler.onNewToken()
        handler.onDeletedMessages()
        assertThat(recorder.calls).containsExactly("sync", "sync")
    }

    @Test
    fun `parse ignores extra keys and never reads content fields`() {
        val event = PushEvent.parse(mapOf("type" to "delivery_test", "test_id" to "x", "sent_at_ms" to "5", "v" to "1"))
        assertThat(event).isEqualTo(PushEvent.DeliveryTest("x", 5))
    }

    @Test
    fun `latency corrects for clock offset and is never negative`() {
        // Device clock 2 s behind the server: offset = +2000.
        assertThat(PushHandler.latencyMs(receivedAtDeviceMs = 10_000, clockOffsetMs = 2_000, sentAtServerMs = 11_500)).isEqualTo(500)
        assertThat(PushHandler.latencyMs(receivedAtDeviceMs = 10_000, clockOffsetMs = 0, sentAtServerMs = 12_000)).isEqualTo(0)
    }
}
