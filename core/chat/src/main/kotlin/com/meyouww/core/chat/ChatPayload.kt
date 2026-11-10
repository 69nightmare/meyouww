package com.meyouww.core.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What travels INSIDE the Signal encryption. The server never sees any of it.
 * Receipts, reactions, edits and deletions are messages too, so they are private.
 */
@Serializable
data class ChatPayload(
    val v: Int = 1,
    /** Unique per payload; for text and stickers it is also the message ID. */
    val id: String,
    /** text, sticker, file, poll, vote, reminder, group_meta, reaction, edit, delete, receipt, save */
    val kind: String,
    val text: String? = null,
    val sticker: String? = null,
    @SerialName("reply_to") val replyTo: String? = null,
    /** The message a reaction, edit or deletion refers to. */
    val target: String? = null,
    /** Messages a receipt covers. */
    val targets: List<String>? = null,
    /** Reaction emoji; empty removes it. */
    val emoji: String? = null,
    /** delivered or read */
    val receipt: String? = null,
    /** For "save": true keeps the target message for both people, false lets it disappear. */
    val saved: Boolean? = null,
    /** For "file": the encrypted photo, video or PDF and the key to open it. */
    val file: Attachment? = null,
    /** For "poll": the choices ([text] is the question). */
    val options: List<String>? = null,
    /** For "poll": more than one choice allowed. */
    val multi: Boolean? = null,
    /** For "vote": the chosen options of poll [target] (empty takes the vote back). */
    val choices: List<Int>? = null,
    /** For "reminder": when, as a local date-time ("2026-11-20T18:30"); [text] is what. */
    val start: String? = null,
    /** In a group: its ID, checked against the envelope so a message can't be moved to another chat. */
    val group: String? = null,
    @SerialName("sent_at") val sentAt: Long,
) {
    val visible: Boolean get() = kind == KIND_TEXT || kind == KIND_STICKER || kind == KIND_FILE || kind == KIND_POLL || kind == KIND_REMINDER

    companion object {
        const val KIND_TEXT = "text"
        const val KIND_STICKER = "sticker"
        const val KIND_FILE = "file"
        const val KIND_POLL = "poll"
        const val KIND_VOTE = "vote"
        const val KIND_REMINDER = "reminder"
        const val KIND_GROUP_META = "group_meta"
        const val KIND_REACTION = "reaction"
        const val KIND_EDIT = "edit"
        const val KIND_DELETE = "delete"
        const val KIND_RECEIPT = "receipt"
        const val KIND_SAVE = "save"
        const val MAX_TEXT = 4000

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        fun encode(p: ChatPayload): String = json.encodeToString(serializer(), p)

        fun decode(text: String): ChatPayload? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

/**
 * Pads plaintext to a multiple of 160 bytes (0x80 then zeros) so message
 * lengths leak less, the same scheme Signal uses.
 */
object Padding {
    private const val BLOCK = 160

    fun pad(input: ByteArray): ByteArray {
        val size = ((input.size + 1 + BLOCK - 1) / BLOCK) * BLOCK
        val out = ByteArray(size)
        input.copyInto(out)
        out[input.size] = 0x80.toByte()
        return out
    }

    fun unpad(input: ByteArray): ByteArray? {
        var i = input.size - 1
        while (i >= 0 && input[i] == 0.toByte()) i--
        if (i < 0 || input[i] != 0x80.toByte()) return null
        return input.copyOfRange(0, i)
    }
}
