package com.meyouww.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class ChatBackupTest {
    private val conversation = ConversationRow(
        id = "c1", peerId = "sam", peerName = "Sam", lastText = "hi", lastKind = "text", lastFromMe = false,
        lastAt = 10, unread = 2, muted = false, pinnedAt = 5, nickname = "Sammy",
    )
    private val message = MessageRow(
        id = "m1", conversationId = "c1", fromMe = true, senderId = "me", kind = "text", text = "hello", sticker = null,
        replyTo = null, status = "read", seq = 4, sentAt = 9, editedAt = null, deleted = false, reactions = "{}", starred = true,
    )
    private val manifest = BackupManifest(
        owner = "me", createdAt = 123, conversations = listOf(conversation), messages = listOf(message),
        members = listOf(GroupMemberRow("g1", "sam", "Sam", "sam.k", null, "admin")),
    )

    @Test
    fun `a backup keeps chats, pins, nicknames and stars`() {
        val json = ChatJson.encodeToString(BackupManifest.serializer(), manifest)
        val back = ChatJson.decodeFromString(BackupManifest.serializer(), json)
        assertThat(back).isEqualTo(manifest)
        assertThat(back.conversations.single().nickname).isEqualTo("Sammy")
        assertThat(back.messages.single().starred).isTrue()
    }

    @Test
    fun `a backup from an older version still opens`() {
        // Fields added later (pins, stars, nicknames, polls) are simply absent.
        val old = """{"owner":"me","createdAt":1,"conversations":[{"id":"c1","peerId":"sam","peerName":"Sam","lastText":null,
            |"lastKind":null,"lastFromMe":false,"lastAt":1,"unread":0,"muted":false}],"messages":[{"id":"m1","conversationId":"c1",
            |"fromMe":false,"senderId":"sam","kind":"text","text":"hi","sticker":null,"replyTo":null,"status":"seen","seq":1,
            |"sentAt":1,"editedAt":null,"deleted":false,"reactions":"{}","someFutureField":true}]}""".trimMargin()
        val back = ChatJson.decodeFromString(BackupManifest.serializer(), old)
        assertThat(back.conversations.single().pinnedAt).isNull()
        assertThat(back.messages.single().starred).isFalse()
        assertThat(back.members).isEmpty()
    }

    @Test
    fun `the backup is unreadable without this account's key`() {
        val key = MediaCrypto.newKey()
        val json = ChatJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray()
        val blob = File.createTempFile("backup", ".bin")
        try {
            blob.outputStream().use { MediaCrypto.encrypt(json.inputStream(), it, key) }
            assertThat(blob.readBytes().toString(Charsets.ISO_8859_1)).doesNotContain("hello")

            val plain = ByteArrayOutputStream()
            MediaCrypto.decrypt(blob, KeyManager.b64(key), MediaCrypto.sha256Of(blob), plain)
            assertThat(ChatJson.decodeFromString(BackupManifest.serializer(), plain.toString("UTF-8"))).isEqualTo(manifest)

            val other = KeyManager.b64(MediaCrypto.newKey())
            assertThrows(Exception::class.java) { MediaCrypto.decrypt(blob, other, MediaCrypto.sha256Of(blob), ByteArrayOutputStream()) }
        } finally {
            blob.delete()
        }
    }
}
