package com.meyouww.core.chat

import androidx.room.withTransaction
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.prefs.AppPreferences
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Lets the repository ask for background work without knowing about WorkManager. */
interface ChatScheduler {
    /** Deliver the outbox as soon as there is a connection. */
    fun send()

    /** Fetch waiting messages as soon as there is a connection. */
    fun fetch()

    /** Fetch and decrypt photos, videos and PDFs that arrived (they can be big). */
    fun media() {}

    /** A chat reminder went into the calendar: plan its notification. */
    fun calendarChanged() {}
}

data class ChatConversation(
    val id: String,
    val peerId: String,
    val peerName: String,
    val lastText: String?,
    val lastKind: String?,
    val lastFromMe: Boolean,
    val lastAt: Long,
    val unread: Int,
    val muted: Boolean,
    /** A group: [peerId] is the group's ID and [peerName] its name. */
    val isGroup: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val fromMe: Boolean,
    /** text, sticker or system */
    val kind: String,
    val text: String?,
    val sticker: String?,
    val replyTo: String?,
    /** Mine: sending, waiting, sent, delivered, read, failed. Theirs: received, seen. System: the event code. */
    val status: String,
    val sentAt: Long,
    val edited: Boolean,
    val deleted: Boolean,
    /** Emoji to the user IDs who reacted with it. */
    val reactions: Map<String, List<String>>,
    /** Saved in the chat for both people; otherwise it disappears once seen. */
    val saved: Boolean = false,
    /** For files: what it is, and the plain file once it is on this phone. */
    val attachment: ChatAttachment? = null,
    /** Who sent it (mine: my ID); groups show their name above the bubble. */
    val senderId: String = "",
    /** For polls: the choices and everyone's votes ([text] is the question). */
    val poll: PollState? = null,
    /** For reminders: when ([text] is what). */
    val reminder: ReminderState? = null,
)

/** A photo, video or PDF as the UI sees it. [file] is null until it has been fetched and decrypted. */
data class ChatAttachment(
    val kind: MediaKind,
    val mime: String,
    val name: String,
    val size: Long,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    /** Base64 JPEG preview, shown until [file] is here. */
    val thumb: String?,
    val file: File?,
    /** It can no longer be fetched (more than 7 days old, or it failed its check). */
    val unavailable: Boolean,
)

/** A new visible message, for the notification. */
data class IncomingNotice(
    val conversationId: String,
    val peerId: String,
    val peerName: String,
    val messageId: String,
    val text: String?,
    val sticker: String?,
    val muted: Boolean,
)

data class SafetyNumber(
    /** 60 digits in 12 groups of 5. */
    val digits: String,
    /** What goes into (and is compared against) the QR code. */
    val qrPayload: String,
    val verified: Boolean,
)

sealed interface DeliveryResult {
    data object Done : DeliveryResult

    /** Something is still waiting (network, rate limit, a friend without the update). */
    data object Retry : DeliveryResult
}

/**
 * End-to-end encrypted chat. Plaintext exists only on the two phones (in the
 * SQLCipher database); the server stores per-device Signal ciphertext until it is
 * fetched and acknowledged. See docs/E2EE.md.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val db: ChatDatabase,
    private val keys: KeyManager,
    private val store: SignalStore,
    private val supabase: SupabaseProvider,
    private val prefs: AppPreferences,
    private val friends: FriendsRepository,
    private val scheduler: ChatScheduler,
    private val media: ChatMediaFiles,
    private val calendar: CalendarRepository,
) {
    private val messages = db.messages()
    private val mediaLock = Mutex()
    private val pollLock = Mutex()
    private val reminderLock = Mutex()
    private val crypto = db.crypto()
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    // ------------------------------------------------------------------ reading

    fun conversations(): Flow<List<ChatConversation>> = messages.observeConversations().map { rows -> rows.map { it.toModel() } }

    fun unreadTotal(): Flow<Int> = messages.observeUnreadTotal()

    fun messages(conversationId: String): Flow<List<ChatMessage>> =
        messages.observeMessages(conversationId).map { rows -> rows.map { it.toModel() } }

    suspend fun conversation(id: String): ChatConversation? = messages.conversation(id)?.toModel()

    suspend fun conversationWith(peerId: String): ChatConversation? = messages.conversationWith(peerId)?.toModel()

    /** Their messages I haven't seen yet, oldest first (for the notification). */
    suspend fun unseen(conversationId: String): List<ChatMessage> =
        messages.recentIncoming(conversationId, 8).filter { it.status == STATUS_RECEIVED }.reversed().map { it.toModel() }

    // ------------------------------------------------------------------ opening

    /** The chat with a friend, created on the server the first time. */
    suspend fun openWith(peerId: String, peerName: String): Outcome<ChatConversation> = withContext(Dispatchers.IO) {
        outcomeOf {
            keys.ensure()
            messages.conversationWith(peerId)?.let { existing ->
                if (peerName.isNotBlank() && existing.peerName != peerName) messages.renamePeer(peerId, peerName)
                return@outcomeOf existing.copy(peerName = peerName.ifBlank { existing.peerName }).toModel()
            }
            val reply = supabase.call("open_conversation", buildJsonObject { put("p_user", peerId) }).jsonObject
            check(reply.str("status") == "ok") { reply.str("status") ?: "failed" }
            val row = ConversationRow(
                id = reply.str("conversation_id")!!,
                peerId = peerId,
                peerName = peerName,
                lastText = null,
                lastKind = null,
                lastFromMe = false,
                lastAt = System.currentTimeMillis(),
                unread = 0,
                muted = false,
            )
            messages.putConversation(row)
            row.toModel()
        }
    }

    // ------------------------------------------------------------------ sending

    suspend fun sendText(conversationId: String, text: String, replyTo: String? = null): String? = io {
        val clean = text.trim().take(ChatPayload.MAX_TEXT)
        if (clean.isEmpty()) return@io null
        queueVisible(conversationId, ChatPayload.KIND_TEXT, clean, null, replyTo)
    }

    suspend fun sendSticker(conversationId: String, sticker: String): String? = io {
        queueVisible(conversationId, ChatPayload.KIND_STICKER, null, sticker.take(64), null)
    }

    /**
     * Sends a photo, video or PDF. [plain] is a private copy the caller prepared (photos
     * resized and stripped of location data); it becomes this phone's copy of the file.
     * The file is encrypted here and uploaded by the send worker before the message goes.
     */
    suspend fun sendFile(
        conversationId: String,
        plain: File,
        mime: String,
        name: String,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        thumb: ByteArray? = null,
    ): String? = io {
        mediaLock.withLock {
            val conv = messages.conversation(conversationId) ?: return@withLock null
            val me = myId() ?: return@withLock null
            val size = plain.length()
            if (size !in 1..Attachment.MAX_BYTES || MediaKind.of(mime) == MediaKind.Other) return@withLock null
            val id = newId()
            val at = now()
            val blob = media.outbox(id)
            val sealed = blob.outputStream().buffered().use { out -> plain.inputStream().buffered().use { MediaCrypto.encrypt(it, out) } }
            val local = "$id.${ChatMediaFiles.extensionFor(mime, name)}"
            val target = media.media(local)
            if (!plain.renameTo(target)) {
                plain.copyTo(target, overwrite = true)
                plain.delete()
            }
            val meta = Attachment(
                path = "$me/${conv.id}/$id", key = sealed.key, sha256 = sealed.sha256, mime = mime,
                name = name.take(120), size = size, width = width, height = height, durationMs = durationMs,
                thumb = thumb?.let(KeyManager::b64)?.takeIf { it.length <= Attachment.MAX_THUMB_CHARS },
            )
            val payload = ChatPayload(id = id, kind = ChatPayload.KIND_FILE, file = meta, sentAt = at)
            db.withTransaction {
                messages.putMessage(
                    MessageRow(
                        id = id, conversationId = conversationId, fromMe = true, senderId = me, kind = ChatPayload.KIND_FILE,
                        text = null, sticker = null, replyTo = null, status = STATUS_SENDING, seq = null, sentAt = at,
                        editedAt = null, deleted = false, reactions = "{}", attachment = StoredAttachment(meta, local).encode(),
                    ),
                )
                messages.putOutbox(OutboxRow(id, conversationId, conv.peerId, ChatPayload.encode(payload), at, 0))
                messages.touch(conversationId, meta.kind.wire, ChatPayload.KIND_FILE, true, at, 0)
            }
            scheduler.send()
            id
        }
    }

    /** Empty [emoji] removes my reaction. */
    suspend fun react(conversationId: String, messageId: String, emoji: String): Unit = io {
        val target = messages.message(messageId) ?: return@io
        if (target.conversationId != conversationId) return@io
        applyReaction(target, myId() ?: return@io, emoji)
        queueControl(conversationId, ChatPayload(id = newId(), kind = ChatPayload.KIND_REACTION, target = messageId, emoji = emoji, sentAt = now()))
    }

    suspend fun edit(conversationId: String, messageId: String, newText: String): Unit = io {
        val target = messages.message(messageId) ?: return@io
        val clean = newText.trim().take(ChatPayload.MAX_TEXT)
        if (!target.fromMe || target.deleted || target.kind != ChatPayload.KIND_TEXT || clean.isEmpty()) return@io
        val at = now()
        messages.putMessage(target.copy(text = clean, editedAt = at))
        queueControl(conversationId, ChatPayload(id = newId(), kind = ChatPayload.KIND_EDIT, target = messageId, text = clean, sentAt = at))
    }

    /** Deletes for everyone. */
    suspend fun delete(conversationId: String, messageId: String): Unit = io {
        val target = messages.message(messageId) ?: return@io
        if (!target.fromMe || target.deleted) return@io
        messages.putMessage(target.copy(deleted = true, text = null, sticker = null, attachment = null))
        if (target.kind == ChatPayload.KIND_REMINDER) cancelReminderSoon(target)
        val pending = messages.outbox(messageId)
        if (pending != null) {
            // Never left the phone: nothing to tell the other side.
            messages.deleteOutbox(messageId)
            pruneMediaSoon()
            return@io
        }
        if (target.kind == ChatPayload.KIND_FILE) pruneMediaSoon()
        queueControl(conversationId, ChatPayload(id = newId(), kind = ChatPayload.KIND_DELETE, target = messageId, sentAt = now()))
    }

    /** Marks what I've seen as read, and tells the sender if read receipts are on. */
    suspend fun markRead(conversationId: String): Unit = io {
        val ids = messages.unreadIncomingIds(conversationId)
        messages.markConversationRead(conversationId)
        if (ids.isEmpty()) return@io
        messages.markSeen(ids)
        if (readReceipts()) {
            queueControl(
                conversationId,
                ChatPayload(id = newId(), kind = ChatPayload.KIND_RECEIPT, receipt = RECEIPT_READ, targets = ids, sentAt = now()),
            )
        }
    }

    suspend fun setMuted(conversationId: String, muted: Boolean): Unit = io { messages.setMuted(conversationId, muted) }

    /** Save (or unsave) a message in the chat; the other person's phone does the same. */
    suspend fun setSaved(conversationId: String, messageId: String, saved: Boolean): Unit = io {
        val target = messages.message(messageId) ?: return@io
        if (target.conversationId != conversationId || target.deleted || target.kind == KIND_SYSTEM) return@io
        messages.setSaved(messageId, saved)
        queueControl(conversationId, ChatPayload(id = newId(), kind = ChatPayload.KIND_SAVE, target = messageId, saved = saved, sentAt = now()))
    }

    /**
     * With "Disappearing chats" on: seen messages that nobody saved disappear when the
     * chat is left, like Snapchat. Off (the default): everything stays on this phone.
     * Either way the server never had them in readable form and already deleted the
     * envelopes once they arrived.
     */
    suspend fun clearSeen(conversationId: String): Unit = io {
        if (!prefs.disappearingChats.first()) return@io
        val at = now()
        val removed = messages.purgeSeen(conversationId, deliveredBefore = at - DAY, sentBefore = at - WEEK)
        if (removed == 0) return@io
        pruneMediaSoon()
        val newest = messages.newest(conversationId)
        if (newest == null) {
            messages.setPreview(conversationId, null, KIND_OPENED, false)
        } else {
            val text = if (newest.kind == ChatPayload.KIND_FILE) StoredAttachment.decode(newest.attachment)?.meta?.kind?.wire else newest.text
            messages.setPreview(conversationId, text.takeIf { !newest.deleted }, if (newest.deleted) "deleted" else newest.kind, newest.fromMe)
        }
    }

    /** Deletes the whole chat from this phone only (the other person keeps theirs). */
    suspend fun clearChat(conversationId: String): Unit = io {
        messages.clearConversation(conversationId)
        messages.setPreview(conversationId, null, null, false)
        pruneMediaSoon()
    }

    /** For leaving a chat (the screen's own scope is already gone by then). */
    fun clearSeenSoon(conversationId: String) {
        cleanup.launch { runCatching { clearSeen(conversationId) } }
    }

    /** Catches anything left over, e.g. when the app was closed from the chat. */
    suspend fun clearAllSeen(): Unit = io { messages.conversationIds().forEach { clearSeen(it) } }

    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun retry(messageId: String): Unit = io {
        val row = messages.message(messageId) ?: return@io
        if (!row.fromMe || messages.outbox(messageId) == null) return@io
        messages.setStatus(messageId, STATUS_SENDING, null)
        scheduler.send()
    }

    /** Room refuses database work on the main thread (it would crash), so every entry point hops to IO. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    suspend fun readReceipts(): Boolean = io { crypto.kv(K_READ_RECEIPTS)?.firstOrNull() != 0.toByte() }

    suspend fun setReadReceipts(on: Boolean): Unit = io { crypto.putKv(KvRow(K_READ_RECEIPTS, byteArrayOf(if (on) 1 else 0))) }

    private suspend fun queueVisible(conversationId: String, kind: String, text: String?, sticker: String?, replyTo: String?): String? {
        val conv = messages.conversation(conversationId) ?: return null
        val me = myId() ?: return null
        val id = newId()
        val at = now()
        val payload = ChatPayload(id = id, kind = kind, text = text, sticker = sticker, replyTo = replyTo, sentAt = at)
        db.withTransaction {
            messages.putMessage(
                MessageRow(
                    id = id, conversationId = conversationId, fromMe = true, senderId = me, kind = kind, text = text,
                    sticker = sticker, replyTo = replyTo, status = STATUS_SENDING, seq = null, sentAt = at,
                    editedAt = null, deleted = false, reactions = "{}",
                ),
            )
            messages.putOutbox(OutboxRow(id, conversationId, conv.peerId, ChatPayload.encode(payload), at, 0))
            messages.touch(conversationId, text, kind, true, at, 0)
        }
        scheduler.send()
        return id
    }

    /** A poll with 2 to 8 choices. */
    suspend fun sendPoll(conversationId: String, question: String, options: List<String>, multi: Boolean): String? = io {
        val q = question.trim().take(MAX_QUESTION)
        val choices = cleanOptions(options) ?: return@io null
        if (q.isEmpty()) return@io null
        queueRich(
            conversationId,
            ChatPayload(id = newId(), kind = ChatPayload.KIND_POLL, text = q, options = choices, multi = multi, sentAt = now()),
            PollState(choices, multi).encode(),
        )
    }

    /** My choices in a poll; empty takes my vote back. */
    suspend fun vote(conversationId: String, pollId: String, choices: List<Int>): Unit = io {
        val me = myId() ?: return@io
        val poll = messages.message(pollId) ?: return@io
        if (poll.conversationId != conversationId || poll.kind != ChatPayload.KIND_POLL || poll.deleted) return@io
        val mine = applyVote(pollId, me, choices) ?: return@io
        queueControl(conversationId, ChatPayload(id = newId(), kind = ChatPayload.KIND_VOTE, target = pollId, choices = mine, sentAt = now()))
    }

    /** A reminder for everyone in the chat: it goes into each person's calendar, mine too. */
    suspend fun sendReminder(conversationId: String, what: String, start: LocalDateTime): String? = io {
        val title = what.trim().take(MAX_REMINDER_TITLE)
        if (title.isEmpty()) return@io null
        val at = start.withSecond(0).withNano(0).toString()
        val id = queueRich(
            conversationId,
            ChatPayload(id = newId(), kind = ChatPayload.KIND_REMINDER, text = title, start = at, sentAt = now()),
            ReminderState(at).encode(),
        )
        if (id != null) syncRemindersSoon()
        id
    }

    /** My user ID (my own poll votes, which member is me). */
    suspend fun myUserId(): String? = myId()

    /** Who is in a group, admins first. */
    fun members(groupId: String): Flow<List<ChatGroupMember>> =
        messages.observeGroupMembers(groupId).map { rows -> rows.map { it.toMember() } }

    /** Group members' names by user ID (for notifications and bubbles). */
    suspend fun memberNames(groupId: String): Map<String, String> = io {
        messages.groupMembers(groupId).associate { it.userId to it.name }
    }

    /** Fetches my groups and who is in them. Groups I left keep their messages on this phone. */
    suspend fun refreshGroups(): Outcome<Unit> = io {
        outcomeOf {
            val raw = supabase.call("list_my_groups", buildJsonObject { })
            val groups = ChatJson.decodeFromJsonElement(ListSerializer(RemoteGroup.serializer()), raw)
            val me = myId()
            val current = groups.map { it.groupId }.toSet()
            db.withTransaction {
                for (g in groups) {
                    val existing = messages.conversation(g.groupId)
                    val title = existing?.peerName?.takeIf { it.isNotBlank() } ?: fallbackTitle(g.members, me)
                    if (existing == null) {
                        messages.putConversation(
                            ConversationRow(g.groupId, g.groupId, title, null, null, false, now(), 0, false, isGroup = true),
                        )
                        if (g.createdBy != me && me != null) addSystem(g.groupId, me, SYSTEM_GROUP_ADDED, "added:${g.groupId}")
                    } else if (existing.peerName.isBlank()) {
                        messages.setTitle(g.groupId, title)
                    }
                    messages.clearGroupMembers(g.groupId)
                    messages.putGroupMembers(g.members.map { GroupMemberRow(g.groupId, it.userId, it.name, it.username, it.avatar, it.role) })
                }
                for (gone in messages.groupConversations().filter { it.id !in current }) {
                    if (messages.groupMembers(gone.id).isEmpty()) continue
                    messages.clearGroupMembers(gone.id)
                    if (me != null) addSystem(gone.id, me, SYSTEM_GROUP_LEFT, "left:${gone.id}:${now()}")
                }
            }
        }
    }

    /** A group with these friends (1 to 31 of them). */
    suspend fun createGroup(title: String, memberIds: List<String>): Outcome<GroupChange> = io {
        outcomeOf {
            keys.ensure()
            val me = myId() ?: error("signed out")
            val name = title.trim().take(MAX_GROUP_TITLE)
            val reply = supabase.call("create_group", buildJsonObject { put("p_members", ids(memberIds)) }).jsonObject
            val status = reply.str("status") ?: "failed"
            if (status != "ok") return@outcomeOf GroupChange.Refused(status, reply.ids("users"))
            val groupId = reply.str("group_id") ?: error("no group")
            refreshGroups()
            if (messages.conversation(groupId) == null) {
                messages.putConversation(ConversationRow(groupId, groupId, name, null, null, false, now(), 0, false, isGroup = true))
            }
            if (name.isNotEmpty()) messages.setTitle(groupId, name)
            addSystem(groupId, me, SYSTEM_GROUP_CREATED, "created:$groupId")
            if (name.isNotEmpty()) queueControl(groupId, groupMeta(name))
            GroupChange.Done(messages.conversation(groupId)!!.toModel())
        }
    }

    /** Adds my friends to a group I run; they get its name too. */
    suspend fun addGroupMembers(groupId: String, memberIds: List<String>): Outcome<GroupChange> = io {
        outcomeOf {
            val reply = supabase.call(
                "add_group_members",
                buildJsonObject {
                    put("p_group", groupId)
                    put("p_members", ids(memberIds))
                },
            ).jsonObject
            val status = reply.str("status") ?: "failed"
            if (status != "ok") return@outcomeOf GroupChange.Refused(status, reply.ids("users"))
            refreshGroups()
            val conv = messages.conversation(groupId) ?: error("no group")
            if (conv.peerName.isNotBlank()) queueControl(groupId, groupMeta(conv.peerName))
            GroupChange.Done(conv.toModel())
        }
    }

    /** Removes someone (admins), or me (leaving). */
    suspend fun removeGroupMember(groupId: String, userId: String): Outcome<GroupChange> = io {
        outcomeOf {
            val reply = supabase.call(
                "remove_group_member",
                buildJsonObject {
                    put("p_group", groupId)
                    put("p_user", userId)
                },
            ).jsonObject
            val status = reply.str("status") ?: "failed"
            if (status != "ok") return@outcomeOf GroupChange.Refused(status)
            if (userId == myId()) {
                // Nothing more goes out to a group I left.
                messages.allOutbox().filter { it.conversationId == groupId }.forEach {
                    messages.deleteOutbox(it.messageId)
                    messages.message(it.messageId)?.takeIf { m -> m.fromMe }?.let { m -> messages.setStatus(m.id, STATUS_FAILED, null) }
                }
            }
            refreshGroups()
            GroupChange.Done(messages.conversation(groupId)?.toModel() ?: error("no group"))
        }
    }

    suspend fun leaveGroup(groupId: String): Outcome<GroupChange> = removeGroupMember(groupId, myId() ?: "")

    /** A new name for everyone in the group. */
    suspend fun renameGroup(groupId: String, title: String): Unit = io {
        val name = title.trim().take(MAX_GROUP_TITLE)
        val conv = messages.conversation(groupId) ?: return@io
        if (!conv.isGroup || name.isEmpty() || name == conv.peerName) return@io
        messages.setTitle(groupId, name)
        queueControl(groupId, groupMeta(name))
    }

    private fun groupMeta(name: String) = ChatPayload(id = newId(), kind = ChatPayload.KIND_GROUP_META, text = name, sentAt = now())

    private fun ids(list: List<String>) = JsonArray(list.distinct().map { JsonPrimitive(it) })

    /** "Riya, Sam and 2 more" until someone names the group. */
    private fun fallbackTitle(members: List<RemoteMember>, me: String?): String {
        val names = members.filter { it.userId != me }.map { it.name.substringBefore(' ').ifBlank { it.username.orEmpty() } }.filter { it.isNotBlank() }
        return when {
            names.isEmpty() -> ""
            names.size <= 3 -> names.joinToString(", ")
            else -> names.take(3).joinToString(", ") + " +" + (names.size - 3)
        }
    }

    /** Polls and reminders: their own row with [extra] state, sent like a text. */
    private suspend fun queueRich(conversationId: String, payload: ChatPayload, extra: String?): String? {
        val conv = messages.conversation(conversationId) ?: return null
        val me = myId() ?: return null
        db.withTransaction {
            messages.putMessage(
                MessageRow(
                    id = payload.id, conversationId = conversationId, fromMe = true, senderId = me, kind = payload.kind,
                    text = payload.text, sticker = null, replyTo = null, status = STATUS_SENDING, seq = null, sentAt = payload.sentAt,
                    editedAt = null, deleted = false, reactions = "{}", extra = extra,
                ),
            )
            messages.putOutbox(OutboxRow(payload.id, conversationId, conv.peerId, ChatPayload.encode(payload), payload.sentAt, 0))
            messages.touch(conversationId, payload.text, payload.kind, true, payload.sentAt, 0)
        }
        scheduler.send()
        return payload.id
    }

    /** Sets [userId]'s choices in a poll; returns what was kept (null if it is not a poll). */
    private suspend fun applyVote(pollId: String, userId: String, choices: List<Int>): List<Int>? = pollLock.withLock {
        val poll = messages.message(pollId) ?: return@withLock null
        val state = PollState.decode(poll.extra) ?: return@withLock null
        val picked = choices.distinct().filter { it in state.options.indices }.let { if (state.multi) it.sorted() else it.take(1) }
        val votes = if (picked.isEmpty()) state.votes - userId else state.votes + (userId to picked)
        messages.setExtra(pollId, state.copy(votes = votes).encode())
        picked
    }

    /** Puts reminders (theirs and mine) into this phone's calendar, once each. */
    suspend fun syncReminders(): Unit = io {
        reminderLock.withLock {
            var added = false
            for (row in messages.reminders()) {
                val state = ReminderState.decode(row.extra) ?: continue
                if (state.added) continue
                val start = runCatching { LocalDateTime.parse(state.start) }.getOrNull()
                if (start == null || start.isBefore(LocalDateTime.now())) {
                    messages.setExtra(row.id, state.copy(added = true).encode()) // already past: nothing to remind
                    continue
                }
                val made = calendar.create(row.text.orEmpty(), start, false, null, REMINDER_EMOJI, 0, emptyList())
                if (made !is Outcome.Success) return@withLock // offline: next time
                if (made.value.ok) {
                    messages.setExtra(row.id, state.copy(added = true, eventId = made.value.id).encode())
                    added = true
                }
            }
            if (added) scheduler.calendarChanged()
        }
    }

    private fun syncRemindersSoon() {
        cleanup.launch { runCatching { syncReminders() } }
    }

    /** The reminder was deleted: take it out of my calendar too. */
    private fun cancelReminderSoon(row: MessageRow) {
        val eventId = ReminderState.decode(row.extra)?.eventId ?: return
        cleanup.launch {
            runCatching {
                calendar.cancel(eventId)
                scheduler.calendarChanged()
            }
        }
    }

    private suspend fun queueControl(conversationId: String, payload: ChatPayload) {
        val conv = messages.conversation(conversationId) ?: return
        messages.putOutbox(OutboxRow(payload.id, conversationId, conv.peerId, ChatPayload.encode(payload), now(), 0))
        scheduler.send()
    }

    /** Encrypts and sends everything queued, oldest first. Called by the send worker. */
    suspend fun deliverOutbox(): DeliveryResult = withContext(Dispatchers.IO) {
        sendLock.withLock {
            val queue = messages.allOutbox()
            if (queue.isEmpty()) return@withLock DeliveryResult.Done
            if (keys.ensure() is Outcome.Failure) return@withLock DeliveryResult.Retry
            val installation = prefs.installationId()
            var retry = false
            val blockedPeers = mutableSetOf<String>()
            for (row in queue) {
                if (row.peerId in blockedPeers) continue // keep each chat in order
                val step = runCatching { deliverOne(row, installation) }.getOrElse { Step.Retry }
                when (step) {
                    is Step.Sent -> {
                        messages.setStatus(row.messageId, STATUS_SENT, step.seq)
                        messages.deleteOutbox(row.messageId)
                    }
                    Step.Rejected -> {
                        messages.setStatus(row.messageId, STATUS_FAILED, null)
                        messages.deleteOutbox(row.messageId)
                    }
                    Step.PeerNotReady -> {
                        messages.setOutboxStatusForPeer(row.peerId, STATUS_WAITING)
                        blockedPeers += row.peerId
                        retry = true
                    }
                    Step.Retry -> {
                        messages.bumpAttempts(row.messageId)
                        blockedPeers += row.peerId
                        retry = true
                    }
                }
            }
            if (retry) DeliveryResult.Retry else DeliveryResult.Done
        }
    }

    private sealed interface Step {
        data class Sent(val seq: Long?) : Step
        data object Rejected : Step
        data object PeerNotReady : Step
        data object Retry : Step
    }

    private suspend fun deliverOne(row: OutboxRow, installation: String): Step {
        // A file goes up (encrypted) before the message that carries its key.
        ChatPayload.decode(row.payload)?.file?.let { uploadIfNeeded(row.messageId, it) }
        if (messages.conversation(row.conversationId)?.isGroup == true) return deliverGroup(row, installation)
        val plaintext = Padding.pad(row.payload.toByteArray(Charsets.UTF_8))
        repeat(2) { attempt ->
            if (attempt > 0 || crypto.peerDevices(row.peerId).isEmpty()) {
                when (refreshPeerDevices(row.peerId)) {
                    PeerKeys.NotFriends -> return Step.Rejected
                    PeerKeys.NoKeys -> return Step.PeerNotReady
                    PeerKeys.Retry -> return Step.Retry
                    PeerKeys.Ok -> Unit
                }
            }
            val devices = crypto.peerDevices(row.peerId)
            if (devices.isEmpty()) return Step.PeerNotReady
            val envelopes = buildJsonArray {
                devices.forEach { device ->
                    val address = SignalProtocolAddress(row.peerId, device.signalId)
                    val message = SessionCipher(store, address).encrypt(plaintext)
                    add(
                        buildJsonObject {
                            put("device_id", device.deviceId)
                            put("type", message.type)
                            put("body", KeyManager.b64(message.serialize()))
                        },
                    )
                }
            }
            val params = buildJsonObject {
                put("p_conversation", row.conversationId)
                put("p_message_id", row.messageId)
                put("p_installation_id", installation)
                put("p_envelopes", envelopes)
            }
            // Receipts, reactions, edits, deletes and saves still wake the other phone (so the
            // delivery light updates) but without a "New message" notification. A server without
            // p_silent yet rejects the unknown parameter, so fall back to a normal send.
            val reply = if (isSilent(row.payload)) {
                runCatching { supabase.call("send_message", JsonObject(params + ("p_silent" to JsonPrimitive(true)))) }
                    .getOrElse { supabase.call("send_message", params) }
            } else {
                supabase.call("send_message", params)
            }.jsonObject
            when (reply.str("status")) {
                "ok" -> return Step.Sent(reply["seq"]?.jsonPrimitive?.longOrNull)
                "stale_devices" -> Unit // someone added or removed a phone: refresh and try once more
                "not_friends", "invalid" -> return Step.Rejected
                else -> return Step.Retry // rate_limited, not_found (registration pending), unknown
            }
        }
        return Step.Retry
    }

    /**
     * One group message: a Signal copy for every phone of every other member, sent in one
     * call so the server can check nobody was left out. The group's ID travels inside the
     * encryption too, so the server can't move a message into another chat.
     */
    private suspend fun deliverGroup(row: OutboxRow, installation: String): Step {
        val me = myId() ?: return Step.Retry
        val payload = ChatPayload.decode(row.payload)?.copy(group = row.conversationId) ?: return Step.Rejected
        val plaintext = Padding.pad(ChatPayload.encode(payload).toByteArray(Charsets.UTF_8))
        var members = messages.groupMembers(row.conversationId)
        if (members.isEmpty()) {
            if (refreshGroups() !is Outcome.Success) return Step.Retry
            members = messages.groupMembers(row.conversationId)
        }
        if (members.none { it.userId == me }) return Step.Rejected // I'm not in it any more
        val others = members.map { it.userId }.filter { it != me }
        if (others.isEmpty()) return Step.Sent(null) // only me left: nothing to send
        repeat(2) { attempt ->
            for (user in others) {
                val stale = attempt > 0 || crypto.peerDevices(user).isEmpty()
                if (!stale || (attempt == 0 && (keylessUntil[user] ?: 0L) > now())) continue
                when (refreshPeerDevices(user)) {
                    PeerKeys.Ok -> keylessUntil.remove(user)
                    PeerKeys.Retry -> return Step.Retry
                    // Blocked, or not on a phone with chat keys yet: they get nothing (the server agrees).
                    PeerKeys.NoKeys, PeerKeys.NotFriends -> {
                        crypto.deletePeerDevices(user)
                        keylessUntil[user] = now() + KEYLESS_RECHECK_MS
                    }
                }
            }
            val reached = mutableSetOf<String>()
            val envelopes = buildJsonArray {
                others.forEach { user ->
                    crypto.peerDevices(user).forEach { device ->
                        val message = SessionCipher(store, SignalProtocolAddress(user, device.signalId)).encrypt(plaintext)
                        reached += user
                        add(
                            buildJsonObject {
                                put("device_id", device.deviceId)
                                put("type", message.type)
                                put("body", KeyManager.b64(message.serialize()))
                            },
                        )
                    }
                }
            }
            if (envelopes.isEmpty()) return Step.PeerNotReady
            // Who it goes to, so the light turns yellow and green once all of them have it.
            messages.message(row.messageId)?.takeIf { it.fromMe }?.let { mine ->
                val before = GroupReceipts.decode(mine.receipts)
                messages.setAudience(mine.id, GroupReceipts.encode(reached.associateWith { before[it] ?: STATUS_SENT }))
            }
            val reply = supabase.call(
                "send_group_message",
                buildJsonObject {
                    put("p_group", row.conversationId)
                    put("p_message_id", row.messageId)
                    put("p_installation_id", installation)
                    put("p_envelopes", envelopes)
                    put("p_silent", !payload.visible)
                },
            ).jsonObject
            when (reply.str("status")) {
                "ok" -> return Step.Sent(reply["seq"]?.jsonPrimitive?.longOrNull)
                "stale_devices" -> Unit // a phone or a member changed: refresh everyone and try once more
                "not_member" -> {
                    runCatching { refreshGroups() }
                    return Step.Rejected
                }
                "invalid" -> return Step.Rejected
                else -> return Step.Retry
            }
        }
        return Step.Retry
    }

    /** Members without chat keys, and when to look again (so each message doesn't ask). */
    private val keylessUntil = ConcurrentHashMap<String, Long>()

    /** Uploads a file's ciphertext once; throws (so the send is retried) if that fails. */
    private suspend fun uploadIfNeeded(messageId: String, meta: Attachment) {
        val blob = media.outbox(messageId)
        if (!blob.exists()) return
        try {
            supabase.client().storage.from(MEDIA_BUCKET).upload(meta.path, blob.readBytes()) {
                upsert = false
                contentType = ContentType.Application.OctetStream
            }
        } catch (e: Exception) {
            // Already there from an earlier attempt whose reply got lost: that is fine.
            val text = e.message.orEmpty()
            if (!text.contains("Duplicate", ignoreCase = true) && !text.contains("already exists", ignoreCase = true)) throw e
        }
        blob.delete()
    }

    /** Only texts, stickers and files are worth a notification on the other phone. */
    private fun isSilent(payload: String): Boolean = ChatPayload.decode(payload)?.visible == false

    private enum class PeerKeys { Ok, NoKeys, NotFriends, Retry }

    /**
     * Fetches a prekey bundle for each of the friend's phones and starts a Signal
     * session with any phone that has none (or whose identity changed). libsignal
     * checks the signed prekey and Kyber signatures against the identity key.
     */
    private suspend fun refreshPeerDevices(peerId: String): PeerKeys {
        val reply = supabase.call("claim_bundles", buildJsonObject { put("p_user", peerId) }).jsonObject
        when (reply.str("status")) {
            "ok" -> Unit
            "not_friends" -> return PeerKeys.NotFriends
            else -> return PeerKeys.Retry
        }
        val bundles = reply["bundles"]?.jsonArray.orEmpty()
            .mapNotNull { runCatching { ChatJson.decodeFromJsonElement(Bundle.serializer(), it) }.getOrNull() }
        if (bundles.isEmpty()) return PeerKeys.NoKeys
        db.withTransaction {
            crypto.deletePeerDevices(peerId)
            bundles.forEach { crypto.putPeerDevice(PeerDeviceRow(peerId, it.signalId, it.deviceId)) }
        }
        for (b in bundles) {
            val address = SignalProtocolAddress(peerId, b.signalId)
            val identity = IdentityKey(KeyManager.unb64(b.identityKey))
            val known = store.getIdentity(address)
            if (store.containsSession(address) && known == identity) continue
            val bundle = PreKeyBundle(
                b.registrationId,
                b.signalId,
                b.prekeyId ?: PreKeyBundle.NULL_PRE_KEY_ID,
                b.prekey?.let { ECPublicKey(KeyManager.unb64(it)) },
                b.signedPrekeyId,
                ECPublicKey(KeyManager.unb64(b.signedPrekey)),
                KeyManager.unb64(b.signedPrekeySig),
                identity,
                b.kyberId,
                KEMPublicKey(KeyManager.unb64(b.kyber)),
                KeyManager.unb64(b.kyberSig),
            )
            SessionBuilder(store, address).process(bundle)
        }
        // Phones they no longer have.
        val current = bundles.map { it.signalId }.toSet()
        store.getSubDeviceSessions(peerId).plus(1).filter { it !in current }
            .forEach { store.deleteSession(SignalProtocolAddress(peerId, it)) }
        noteIdentityChanges()
        return PeerKeys.Ok
    }

    @Serializable
    private data class Bundle(
        @SerialName("device_id") val deviceId: String,
        @SerialName("signal_id") val signalId: Int,
        @SerialName("registration_id") val registrationId: Int,
        @SerialName("identity_key") val identityKey: String,
        @SerialName("signed_prekey_id") val signedPrekeyId: Int,
        @SerialName("signed_prekey") val signedPrekey: String,
        @SerialName("signed_prekey_sig") val signedPrekeySig: String,
        @SerialName("prekey_id") val prekeyId: Int? = null,
        val prekey: String? = null,
        @SerialName("kyber_id") val kyberId: Int,
        val kyber: String,
        @SerialName("kyber_sig") val kyberSig: String,
    )

    // ------------------------------------------------------------------ receiving

    @Serializable
    private data class Envelope(
        val id: Long,
        @SerialName("message_id") val messageId: String,
        @SerialName("conversation_id") val conversationId: String? = null,
        @SerialName("group_id") val groupId: String? = null,
        @SerialName("sender_id") val senderId: String,
        @SerialName("sender_device_id") val senderDeviceId: String? = null,
        @SerialName("sender_signal_id") val senderSignalId: Int = 1,
        val seq: Long,
        val type: Int,
        val body: String,
    )

    /** Fetches, decrypts and stores waiting messages; returns the new visible ones. */
    suspend fun receive(): Outcome<List<IncomingNotice>> = withContext(Dispatchers.IO) {
        receiveLock.withLock {
            outcomeOf {
                keys.ensure()
                val installation = prefs.installationId()
                val notices = mutableListOf<IncomingNotice>()
                var usedPreKey = false
                while (true) {
                    val raw = supabase.call(
                        "fetch_messages",
                        buildJsonObject {
                            put("p_installation_id", installation)
                            put("p_limit", BATCH)
                        },
                    ) as? JsonArray ?: break
                    if (raw.isEmpty()) break
                    val ack = mutableListOf<Long>()
                    val delivered = mutableMapOf<String, MutableList<String>>()
                    for (element in raw) {
                        val env = runCatching { ChatJson.decodeFromJsonElement(Envelope.serializer(), element) }.getOrNull()
                        if (env == null) {
                            element.jsonObject["id"]?.jsonPrimitive?.longOrNull?.let(ack::add)
                            continue
                        }
                        ack += env.id
                        if (env.conversationId == null && env.groupId == null) continue
                        if (env.type == TYPE_PREKEY) usedPreKey = true
                        val plain = decrypt(env) ?: continue
                        env.senderDeviceId?.let { crypto.putPeerDevice(PeerDeviceRow(env.senderId, env.senderSignalId, it)) }
                        val payload = Padding.unpad(plain)?.let { ChatPayload.decode(it.toString(Charsets.UTF_8)) } ?: continue
                        // Sealed inside the encryption: a group message names its group, a 1:1 message none.
                        if (payload.group != env.groupId) continue
                        val notice = applyPayload(env, payload)
                        if (notice != null) {
                            notices += notice
                            delivered.getOrPut(notice.conversationId) { mutableListOf() } += notice.messageId
                        }
                    }
                    supabase.call(
                        "ack_messages",
                        buildJsonObject {
                            put("p_installation_id", installation)
                            put("p_ids", JsonArray(ack.map { JsonPrimitive(it) }))
                        },
                    )
                    noteIdentityChanges()
                    delivered.forEach { (conversationId, ids) ->
                        queueControl(
                            conversationId,
                            ChatPayload(id = newId(), kind = ChatPayload.KIND_RECEIPT, receipt = RECEIPT_DELIVERED, targets = ids, sentAt = now()),
                        )
                    }
                    if (raw.size < BATCH) break
                }
                // Prekeys were used up: publish more. A friend's message may also unblock my queue.
                if (usedPreKey) keys.ensure()
                if (groupsStale) {
                    groupsStale = false
                    refreshGroups()
                }
                if (remindersArrived) {
                    remindersArrived = false
                    syncRemindersSoon()
                }
                if (filesArrived) {
                    filesArrived = false
                    scheduler.media()
                }
                if (notices.isNotEmpty() && messages.allOutbox().isNotEmpty()) scheduler.send()
                notices
            }
        }
    }

    /** Set while receiving when a photo, video or PDF arrived, so the media worker runs after. */
    @Volatile private var filesArrived = false

    /** A message came from a group this phone doesn't know yet: fetch its name and members after. */
    @Volatile private var groupsStale = false

    /** A reminder arrived: put it into the calendar after. */
    @Volatile private var remindersArrived = false

    /**
     * Fetches, checks and decrypts every file that arrived, then deletes the server copy.
     * Called by the media worker; safe to run again (anything done is skipped).
     */
    suspend fun downloadMedia(): DeliveryResult = withContext(Dispatchers.IO) {
        mediaLock.withLock {
            var retry = false
            for (row in messages.incomingFiles()) {
                val stored = StoredAttachment.decode(row.attachment) ?: continue
                if (stored.local != null || stored.unavailable) continue
                // In a group the others still need the server's copy; it is swept after 7 days.
                val shared = messages.conversation(row.conversationId)?.isGroup == true
                when (val got = fetchOne(row.id, stored.meta, keepOnServer = shared)) {
                    is Fetch.Done -> messages.setAttachment(row.id, stored.copy(local = got.name).encode())
                    Fetch.Gone -> messages.setAttachment(row.id, stored.copy(unavailable = true).encode())
                    Fetch.Retry -> retry = true
                }
            }
            pruneMedia()
            if (retry) DeliveryResult.Retry else DeliveryResult.Done
        }
    }

    /** Asks the media worker to fetch anything still missing (e.g. when a chat is opened). */
    fun fetchMediaSoon() = scheduler.media()

    private sealed interface Fetch {
        data class Done(val name: String) : Fetch
        data object Gone : Fetch
        data object Retry : Fetch
    }

    private suspend fun fetchOne(messageId: String, meta: Attachment, keepOnServer: Boolean = false): Fetch {
        val bucket = supabase.client().storage.from(MEDIA_BUCKET)
        val bytes = try {
            bucket.downloadAuthenticated(meta.path)
        } catch (e: Exception) {
            val text = e.message.orEmpty()
            return if (text.contains("not found", ignoreCase = true) || text.contains("404")) Fetch.Gone else Fetch.Retry
        }
        val temp = File(media.tempDir, "$messageId.bin")
        val name = "$messageId.${ChatMediaFiles.extensionFor(meta.mime, meta.name)}"
        val out = media.media(name)
        try {
            temp.writeBytes(bytes)
            out.outputStream().buffered().use { MediaCrypto.decrypt(temp, meta.key, meta.sha256, it) }
        } catch (e: Exception) {
            // Changed on the way, or not what the message said: never show it.
            out.delete()
            return Fetch.Gone
        } finally {
            temp.delete()
        }
        // Delivered: the server copy has done its job (in a 1:1 chat).
        if (!keepOnServer) runCatching { bucket.delete(meta.path) }
        return Fetch.Done(name)
    }

    /** Deletes local files no message points to any more (deleted, disappeared, chat cleared). */
    private suspend fun pruneMedia() {
        val live = messages.liveAttachments().mapNotNull { StoredAttachment.decode(it)?.local }.toSet()
        media.mediaDir.listFiles()?.forEach { if (it.name !in live) it.delete() }
        val pending = messages.allOutbox().map { it.messageId }.toSet()
        media.outboxDir.listFiles()?.forEach { if (it.nameWithoutExtension !in pending) it.delete() }
    }

    private fun pruneMediaSoon() {
        cleanup.launch { runCatching { mediaLock.withLock { pruneMedia() } } }
    }

    private suspend fun decrypt(env: Envelope): ByteArray? {
        val address = SignalProtocolAddress(env.senderId, env.senderSignalId)
        val bytes = runCatching { KeyManager.unb64(env.body) }.getOrNull() ?: return null
        return try {
            val cipher = SessionCipher(store, address)
            if (env.type == TYPE_PREKEY) cipher.decrypt(PreKeySignalMessage(bytes)) else cipher.decrypt(SignalMessage(bytes))
        } catch (e: DuplicateMessageException) {
            null // already handled
        } catch (e: Exception) {
            // Can't be read (lost session, reinstall). Say so in the chat rather than vanish.
            val conv = chatFor(env)
            addSystem(conv.id, env.senderId, SYSTEM_UNREADABLE, "unreadable:${env.messageId}")
            null
        }
    }

    /** Applies one decrypted payload. Returns a notice for new visible messages. */
    private suspend fun applyPayload(env: Envelope, p: ChatPayload): IncomingNotice? {
        val conv = chatFor(env)
        val at = now()
        when (p.kind) {
            ChatPayload.KIND_TEXT, ChatPayload.KIND_STICKER -> {
                if (messages.message(p.id) != null) return null
                val text = p.text?.take(ChatPayload.MAX_TEXT)
                if (p.kind == ChatPayload.KIND_TEXT && text.isNullOrBlank()) return null
                val sticker = p.sticker?.take(64)
                messages.putMessage(
                    MessageRow(
                        id = p.id, conversationId = conv.id, fromMe = false, senderId = env.senderId, kind = p.kind,
                        text = if (p.kind == ChatPayload.KIND_TEXT) text else null,
                        sticker = if (p.kind == ChatPayload.KIND_STICKER) sticker else null,
                        replyTo = p.replyTo, status = STATUS_RECEIVED, seq = env.seq,
                        // Their clock may be off; never show a message from the future.
                        sentAt = p.sentAt.coerceAtMost(at), editedAt = null, deleted = false, reactions = "{}",
                    ),
                )
                messages.touch(conv.id, text, p.kind, false, at, 1)
                return IncomingNotice(conv.id, env.senderId, conv.peerName, p.id, text, sticker, conv.muted)
            }
            ChatPayload.KIND_FILE -> {
                if (messages.message(p.id) != null) return null
                // The file must be in the sender's own folder for this chat, and of a kind the app shows.
                val meta = p.file?.takeIf { it.isAcceptable(env.senderId, env.groupId ?: env.conversationId.orEmpty(), p.id) } ?: return null
                messages.putMessage(
                    MessageRow(
                        id = p.id, conversationId = conv.id, fromMe = false, senderId = env.senderId, kind = ChatPayload.KIND_FILE,
                        text = null, sticker = null, replyTo = p.replyTo, status = STATUS_RECEIVED, seq = env.seq,
                        sentAt = p.sentAt.coerceAtMost(at), editedAt = null, deleted = false, reactions = "{}",
                        attachment = StoredAttachment(meta).encode(),
                    ),
                )
                messages.touch(conv.id, meta.kind.wire, ChatPayload.KIND_FILE, false, at, 1)
                filesArrived = true
                return IncomingNotice(conv.id, env.senderId, conv.peerName, p.id, null, null, conv.muted)
            }
            ChatPayload.KIND_REACTION -> {
                val target = p.target?.let { messages.message(it) } ?: return null
                if (target.conversationId == conv.id) applyReaction(target, env.senderId, p.emoji.orEmpty())
            }
            ChatPayload.KIND_EDIT -> {
                val target = p.target?.let { messages.message(it) } ?: return null
                val text = p.text?.trim()?.take(ChatPayload.MAX_TEXT)
                if (target.conversationId == conv.id && target.senderId == env.senderId && !target.fromMe &&
                    !target.deleted && target.kind == ChatPayload.KIND_TEXT && !text.isNullOrEmpty()
                ) {
                    messages.putMessage(target.copy(text = text, editedAt = p.sentAt.coerceAtMost(at)))
                }
            }
            ChatPayload.KIND_DELETE -> {
                val target = p.target?.let { messages.message(it) } ?: return null
                if (target.conversationId == conv.id && target.senderId == env.senderId && !target.fromMe) {
                    messages.putMessage(target.copy(deleted = true, text = null, sticker = null, attachment = null))
                    if (target.kind == ChatPayload.KIND_FILE) pruneMediaSoon()
                    if (target.kind == ChatPayload.KIND_REMINDER) cancelReminderSoon(target)
                }
            }
            ChatPayload.KIND_SAVE -> {
                val target = p.target?.let { messages.message(it) } ?: return null
                if (target.conversationId == conv.id && !target.deleted) messages.setSaved(target.id, p.saved == true)
            }
            ChatPayload.KIND_RECEIPT -> {
                val ids = p.targets.orEmpty().take(500)
                val status = when (p.receipt) {
                    RECEIPT_READ -> STATUS_READ
                    RECEIPT_DELIVERED -> STATUS_DELIVERED
                    else -> return null
                }
                if (ids.isEmpty()) return null
                if (conv.isGroup) applyGroupReceipts(conv.id, env.senderId, ids, status) else messages.advanceStatus(conv.id, ids, status)
            }
            ChatPayload.KIND_POLL -> {
                if (messages.message(p.id) != null) return null
                val question = p.text?.trim()?.take(MAX_QUESTION)?.takeIf { it.isNotEmpty() } ?: return null
                val options = cleanOptions(p.options) ?: return null
                messages.putMessage(
                    MessageRow(
                        id = p.id, conversationId = conv.id, fromMe = false, senderId = env.senderId, kind = ChatPayload.KIND_POLL,
                        text = question, sticker = null, replyTo = null, status = STATUS_RECEIVED, seq = env.seq,
                        sentAt = p.sentAt.coerceAtMost(at), editedAt = null, deleted = false, reactions = "{}",
                        extra = PollState(options, p.multi == true).encode(),
                    ),
                )
                messages.touch(conv.id, question, ChatPayload.KIND_POLL, false, at, 1)
                return IncomingNotice(conv.id, env.senderId, conv.peerName, p.id, question, null, conv.muted)
            }
            ChatPayload.KIND_VOTE -> {
                val target = p.target?.let { messages.message(it) } ?: return null
                if (target.conversationId == conv.id && target.kind == ChatPayload.KIND_POLL && !target.deleted) {
                    applyVote(target.id, env.senderId, p.choices.orEmpty().take(PollState.MAX_OPTIONS))
                }
            }
            ChatPayload.KIND_REMINDER -> {
                if (messages.message(p.id) != null) return null
                val what = p.text?.trim()?.take(MAX_REMINDER_TITLE)?.takeIf { it.isNotEmpty() } ?: return null
                val start = p.start?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: return null
                messages.putMessage(
                    MessageRow(
                        id = p.id, conversationId = conv.id, fromMe = false, senderId = env.senderId, kind = ChatPayload.KIND_REMINDER,
                        text = what, sticker = null, replyTo = null, status = STATUS_RECEIVED, seq = env.seq,
                        sentAt = p.sentAt.coerceAtMost(at), editedAt = null, deleted = false, reactions = "{}",
                        extra = ReminderState(start.withSecond(0).withNano(0).toString()).encode(),
                    ),
                )
                messages.touch(conv.id, what, ChatPayload.KIND_REMINDER, false, at, 1)
                remindersArrived = true
                return IncomingNotice(conv.id, env.senderId, conv.peerName, p.id, what, null, conv.muted)
            }
            ChatPayload.KIND_GROUP_META -> {
                val name = p.text?.trim()?.take(MAX_GROUP_TITLE)?.takeIf { it.isNotEmpty() } ?: return null
                if (conv.isGroup) messages.setTitle(conv.id, name)
            }
        }
        return null
    }

    /** One member's receipt for my group messages: the light moves once everyone it went to has it. */
    private suspend fun applyGroupReceipts(groupId: String, from: String, ids: List<String>, status: String) {
        for (id in ids) {
            val row = messages.message(id) ?: continue
            if (row.conversationId != groupId || !row.fromMe) continue
            val receipts = GroupReceipts.advance(GroupReceipts.decode(row.receipts), from, status)
            val next = if (row.status == STATUS_READ) STATUS_READ else GroupReceipts.status(receipts, row.status)
            messages.setReceipts(id, GroupReceipts.encode(receipts), next)
        }
    }

    /** The chat an envelope belongs to: a group (known or not yet), or the 1:1 chat with its sender. */
    private suspend fun chatFor(env: Envelope): ConversationRow {
        val group = env.groupId ?: return conversationFor(env.senderId, env.conversationId.orEmpty())
        messages.conversation(group)?.let { return it }
        // Added to a group just now: its name and members come with the refresh after this fetch.
        val row = ConversationRow(group, group, "", null, null, false, now(), 0, false, isGroup = true)
        messages.putConversation(row)
        groupsStale = true
        return row
    }

    private suspend fun applyReaction(target: MessageRow, userId: String, emoji: String) {
        val map = decodeReactions(target.reactions).mapValues { (_, users) -> users - userId }.toMutableMap()
        if (emoji.isNotEmpty() && emoji.length <= 16) map[emoji] = map[emoji].orEmpty() + userId
        messages.putMessage(target.copy(reactions = encodeReactions(map.filterValues { it.isNotEmpty() })))
    }

    private suspend fun conversationFor(peerId: String, serverId: String): ConversationRow {
        messages.conversationWith(peerId)?.let { return it }
        val name = friendName(peerId)
        val row = ConversationRow(serverId, peerId, name, null, null, false, now(), 0, false)
        messages.putConversation(row)
        return row
    }

    private suspend fun friendName(peerId: String): String {
        val known = friends.friends.value ?: (friends.refreshFriends() as? Outcome.Success)?.value
        return known?.firstOrNull { it.id == peerId }?.card?.name.orEmpty()
    }

    /** "Riya's security code changed" in every chat whose peer has a new identity. */
    private suspend fun noteIdentityChanges() {
        val changed = store.changedIdentities.toList()
        if (changed.isEmpty()) return
        store.changedIdentities.removeAll(changed.toSet())
        changed.forEach { peerId ->
            val conv = messages.conversationWith(peerId) ?: return@forEach
            addSystem(conv.id, peerId, SYSTEM_SAFETY_CHANGED, "safety:${UUID.randomUUID()}")
        }
    }

    private suspend fun addSystem(conversationId: String, peerId: String, event: String, id: String) {
        if (messages.message(id) != null) return
        val at = now()
        messages.putMessage(
            MessageRow(
                id = id, conversationId = conversationId, fromMe = false, senderId = peerId, kind = KIND_SYSTEM,
                text = null, sticker = null, replyTo = null, status = event, seq = null, sentAt = at,
                editedAt = null, deleted = false, reactions = "{}",
            ),
        )
    }

    // ------------------------------------------------------------------ safety numbers

    /** The safety number with a friend, once a session exists. */
    suspend fun safetyNumber(peerId: String): SafetyNumber? = withContext(Dispatchers.IO) {
        val me = myId() ?: return@withContext null
        val theirs = crypto.identitiesFor(peerId).minByOrNull { it.address } ?: return@withContext null
        val fingerprint = NumericFingerprintGenerator(FINGERPRINT_ITERATIONS).createFor(
            FINGERPRINT_VERSION,
            me.toByteArray(Charsets.UTF_8),
            store.identityKeyPair.publicKey,
            peerId.toByteArray(Charsets.UTF_8),
            IdentityKey(theirs.identityKey),
        )
        SafetyNumber(
            digits = fingerprint.displayableFingerprint.displayText,
            qrPayload = KeyManager.b64(fingerprint.scannableFingerprint.serialized),
            verified = theirs.verified,
        )
    }

    /** True if the scanned code matches; then the friend is marked verified. */
    suspend fun verifyScanned(peerId: String, scanned: String): Boolean = withContext(Dispatchers.IO) {
        val me = myId() ?: return@withContext false
        val theirs = crypto.identitiesFor(peerId).minByOrNull { it.address } ?: return@withContext false
        val matches = runCatching {
            NumericFingerprintGenerator(FINGERPRINT_ITERATIONS).createFor(
                FINGERPRINT_VERSION,
                me.toByteArray(Charsets.UTF_8),
                store.identityKeyPair.publicKey,
                peerId.toByteArray(Charsets.UTF_8),
                IdentityKey(theirs.identityKey),
            ).scannableFingerprint.compareTo(KeyManager.unb64(scanned.trim()))
        }.getOrDefault(false)
        if (matches) crypto.setVerified(peerId, true)
        matches
    }

    suspend fun setVerified(peerId: String, verified: Boolean) = withContext(Dispatchers.IO) { crypto.setVerified(peerId, verified) }

    // ------------------------------------------------------------------ helpers

    private suspend fun myId(): String? = supabase.clientOrNull()?.auth?.currentUserOrNull()?.id ?: io { keys.ownerId() }

    private fun ConversationRow.toModel() =
        ChatConversation(id, peerId, peerName, lastText, lastKind, lastFromMe, lastAt, unread, muted, isGroup)

    private fun GroupMemberRow.toMember() = ChatGroupMember(userId, name, username, avatar, role == ROLE_ADMIN)

    private fun MessageRow.toModel() = ChatMessage(
        id = id,
        fromMe = fromMe,
        kind = kind,
        text = text,
        sticker = sticker,
        replyTo = replyTo,
        status = status,
        sentAt = sentAt,
        edited = editedAt != null,
        deleted = deleted,
        reactions = decodeReactions(reactions),
        saved = saved,
        attachment = StoredAttachment.decode(attachment)?.let { stored ->
            val meta = stored.meta
            ChatAttachment(
                kind = meta.kind, mime = meta.mime, name = meta.name, size = meta.size, width = meta.width,
                height = meta.height, durationMs = meta.durationMs, thumb = meta.thumb,
                file = stored.local?.let(media::media)?.takeIf { it.exists() },
                unavailable = stored.unavailable,
            )
        },
        senderId = senderId,
        poll = if (kind == ChatPayload.KIND_POLL) PollState.decode(extra) else null,
        reminder = if (kind == ChatPayload.KIND_REMINDER) ReminderState.decode(extra) else null,
    )

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.ids(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()

    companion object {
        const val STATUS_SENDING = "sending"
        const val STATUS_WAITING = "waiting"
        const val STATUS_SENT = "sent"
        const val STATUS_DELIVERED = "delivered"
        const val STATUS_READ = "read"
        const val STATUS_FAILED = "failed"
        const val STATUS_RECEIVED = "received"
        const val STATUS_SEEN = "seen"
        const val KIND_SYSTEM = "system"
        /** Conversation preview once everything in it has disappeared. */
        const val KIND_OPENED = "opened"
        const val SYSTEM_SAFETY_CHANGED = "safety_changed"
        const val SYSTEM_UNREADABLE = "unreadable"
        const val SYSTEM_GROUP_CREATED = "group_created"
        const val SYSTEM_GROUP_ADDED = "group_added"
        const val SYSTEM_GROUP_LEFT = "group_left"
        const val ROLE_ADMIN = "admin"
        const val MAX_GROUP_TITLE = 60
        const val MAX_QUESTION = 200
        const val MAX_REMINDER_TITLE = 120

        /** Trimmed, distinct, 2 to 8 choices of up to 80 characters; null if that leaves too few. */
        fun cleanOptions(options: List<String>?): List<String>? = options.orEmpty()
            .map { it.trim().take(PollState.MAX_OPTION_CHARS) }.filter { it.isNotEmpty() }.distinct()
            .takeIf { it.size >= 2 }?.take(PollState.MAX_OPTIONS)

        private const val DAY = 86_400_000L
        private const val WEEK = 7 * DAY
        private const val RECEIPT_DELIVERED = "delivered"
        private const val RECEIPT_READ = "read"
        private const val TYPE_PREKEY = 3
        private const val BATCH = 200
        private const val MEDIA_BUCKET = "chat-media"
        private const val K_READ_RECEIPTS = "read_receipts"
        private const val FINGERPRINT_ITERATIONS = 5200
        private const val FINGERPRINT_VERSION = 2
        private const val REMINDER_EMOJI = "\u23F0"
        private const val KEYLESS_RECHECK_MS = 10 * 60_000L

        private fun now() = System.currentTimeMillis()

        private fun newId() = UUID.randomUUID().toString()

        fun decodeReactions(json: String): Map<String, List<String>> = runCatching {
            ChatJson.parseToJsonElement(json).jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyMap())

        fun encodeReactions(map: Map<String, List<String>>): String =
            JsonObject(map.mapValues { (_, users) -> JsonArray(users.distinct().map { JsonPrimitive(it) }) }).toString()
    }
}
