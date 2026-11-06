package com.meyouww.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatPayloadTest {

    @Test
    fun `padding hides exact lengths and round trips`() {
        for (n in listOf(0, 1, 158, 159, 160, 161, 500)) {
            val input = ByteArray(n) { (it % 251 + 1).toByte() }
            val padded = Padding.pad(input)
            assertThat(padded.size % 160).isEqualTo(0)
            assertThat(padded.size).isGreaterThan(n)
            assertThat(Padding.unpad(padded)).isEqualTo(input)
        }
        assertThat(Padding.pad(ByteArray(10)).size).isEqualTo(Padding.pad(ByteArray(100)).size)
    }

    @Test
    fun `text ending in zero bytes survives padding`() {
        val input = byteArrayOf(1, 2, 0, 0)
        assertThat(Padding.unpad(Padding.pad(input))).isEqualTo(input)
    }

    @Test
    fun `garbage without the marker is rejected`() {
        assertThat(Padding.unpad(ByteArray(160))).isNull()
        assertThat(Padding.unpad(byteArrayOf(1, 2, 3))).isNull()
    }

    @Test
    fun `payloads round trip and ignore unknown fields`() {
        val p = ChatPayload(id = "m1", kind = ChatPayload.KIND_TEXT, text = "hi 💖", replyTo = "m0", sentAt = 42)
        val text = ChatPayload.encode(p)
        assertThat(ChatPayload.decode(text)).isEqualTo(p)
        assertThat(text).doesNotContain("\"emoji\"")
        val future = """{"v":2,"id":"x","kind":"text","text":"yo","sent_at":1,"new_field":true}"""
        assertThat(ChatPayload.decode(future)?.text).isEqualTo("yo")
        assertThat(ChatPayload.decode("not json")).isNull()
    }

    @Test
    fun `only text and stickers are visible messages`() {
        assertThat(ChatPayload(id = "a", kind = "text", sentAt = 0).visible).isTrue()
        assertThat(ChatPayload(id = "a", kind = "sticker", sentAt = 0).visible).isTrue()
        assertThat(ChatPayload(id = "a", kind = "receipt", sentAt = 0).visible).isFalse()
        assertThat(ChatPayload(id = "a", kind = "reaction", sentAt = 0).visible).isFalse()
    }
}
