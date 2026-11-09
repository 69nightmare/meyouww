package com.meyouww.core.chat

import androidx.room.withTransaction
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.prefs.AppPreferences
import io.github.jan.supabase.auth.auth
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Lets the repository ask for background work without knowing about WorkManager. */
interface ChatScheduler {
    /** Deliver the outbox as soon as there is a connection. */
    fun send()

    /** Fetch waiting messages as soon as there is a connection. */
    fun fetch()
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
) {
    private val messages = db.messages()
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
        messages.putMessage(target.copy(deleted = true, text = null, sticker = null))
        val pending = messages.outbox(messageId)
        if (pending != null) {
            // Never left the phone: nothing to tell the other side.
            messages.deleteOutbox(messageId)
            return@io
        }
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
        val newest = messages.newest(conversationId)
        if (newest == null) {
            messages.setPreview(conversationId, null, KIND_OPENED, false)
        } else {
            messages.setPreview(conversationId, newest.text.takeIf { !newest.deleted }, if (newest.deleted) "deleted" else newest.kind, newest.fromMe)
        }
    }

    /** Deletes the whole chat from this phone only (the other person keeps theirs). */
    suspend fun clearChat(conversationId: String): Unit = io {
        messages.clearConversation(conversationId)
        messages.setPreview(conversationId, null, null, false)
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

    /** Only texts and stickers are worth a notification on the other phone. */
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
        @SerialName("conversation_id") val conversationId: String,
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
                        if (env.type == TYPE_PREKEY) usedPreKey = true
                        val plain = decrypt(env) ?: continue
                        env.senderDeviceId?.let { crypto.putPeerDevice(PeerDeviceRow(env.senderId, env.senderSignalId, it)) }
                        val payload = Padding.unpad(plain)?.let { ChatPayload.decode(it.toString(Charsets.UTF_8)) } ?: continue
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
                if (notices.isNotEmpty() && messages.allOutbox().isNotEmpty()) scheduler.send()
                notices
            }
        }
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
            val conv = conversationFor(env.senderId, env.conversationId)
            addSystem(conv.id, env.senderId, SYSTEM_UNREADABLE, "unreadable:${env.messageId}")
            null
        }
    }

    /** Applies one decrypted payload. Returns a notice for new visible messages. */
    private suspend fun applyPayload(env: Envelope, p: ChatPayload): IncomingNotice? {
        val conv = conversationFor(env.senderId, env.conversationId)
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
                    messages.putMessage(target.copy(deleted = true, text = null, sticker = null))
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
                if (ids.isNotEmpty()) messages.advanceStatus(conv.id, ids, status)
            }
        }
        return null
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
        ChatConversation(id, peerId, peerName, lastText, lastKind, lastFromMe, lastAt, unread, muted)

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
    )

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

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

        private const val DAY = 86_400_000L
        private const val WEEK = 7 * DAY
        private const val RECEIPT_DELIVERED = "delivered"
        private const val RECEIPT_READ = "read"
        private const val TYPE_PREKEY = 3
        private const val BATCH = 200
        private const val K_READ_RECEIPTS = "read_receipts"
        private const val FINGERPRINT_ITERATIONS = 5200
        private const val FINGERPRINT_VERSION = 2

        private fun now() = System.currentTimeMillis()

        private fun newId() = UUID.randomUUID().toString()

        fun decodeReactions(json: String): Map<String, List<String>> = runCatching {
            ChatJson.parseToJsonElement(json).jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyMap())

        fun encodeReactions(map: Map<String, List<String>>): String =
            JsonObject(map.mapValues { (_, users) -> JsonArray(users.distinct().map { JsonPrimitive(it) }) }).toString()
    }
}
