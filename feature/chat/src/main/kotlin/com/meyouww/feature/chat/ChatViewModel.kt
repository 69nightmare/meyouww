package com.meyouww.feature.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.chat.ChatGroupMember
import com.meyouww.core.chat.GroupChange
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.ChatScheduler
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.presence.Presence
import com.meyouww.core.data.presence.PresenceRepository
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.model.parseInstant
import com.meyouww.core.notifications.chat.ChatInbox
import com.meyouww.core.notifications.chat.ChatNotifications
import com.meyouww.core.notifications.push.WallpaperGallery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

/** One row in the chat: a message, or a wallpaper sent either way. */
sealed interface TimelineItem {
    val key: String
    val at: Long

    data class Message(val message: ChatMessage) : TimelineItem {
        override val key: String get() = "m:" + message.id
        override val at: Long get() = message.sentAt
    }

    /** [file] is the copy kept on this phone (null for old ones from before it was kept). */
    data class Wallpaper(
        val id: String,
        val fromMe: Boolean,
        val status: String,
        override val at: Long,
        val file: File? = null,
        val target: WallpaperTarget = WallpaperTarget.Lock,
    ) : TimelineItem {
        override val key: String get() = "w:$id"
    }
}

data class ChatUiState(
    /** A short note shown in the chat (e.g. "Riya's phone is on vibrate now"). */
    val note: Int? = null,
    /** A relationship change is on its way to the server. */
    val busy: Boolean = false,
    val loading: Boolean = true,
    val conversation: ChatConversation? = null,
    val error: AppError? = null,
    val replyTo: ChatMessage? = null,
    val editing: ChatMessage? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
    private val wallpapers: WallpaperRepository,
    private val gallery: WallpaperGallery,
    private val visibility: ChatVisibility,
    private val scheduler: ChatScheduler,
    private val notifications: ChatNotifications,
    private val presence: PresenceRepository,
    private val inbox: ChatInbox,
    prefs: AppPreferences,
) : ViewModel() {

    private val _peerPresence = MutableStateFlow<Presence?>(null)

    /** Online / last seen / typing / has this chat open, for the other person (refreshed while on screen). */
    val peerPresence: StateFlow<Presence?> = _peerPresence.asStateFlow()
    private var presenceJob: Job? = null
    private var lastTypingReport = 0L

    private val _state = MutableStateFlow(ChatUiState())

    /** "Disappearing chats" (Settings). Off by default: chats stay on this phone. */
    val disappearing: StateFlow<Boolean> = prefs.disappearingChats.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Wallpapers as the server knows them (status), by command ID. */
    private val serverWalls = MutableStateFlow<Map<String, TimelineItem.Wallpaper>>(emptyMap())
    private var opened = false

    /** In a group: who is in it, admins first (empty in a 1:1 chat). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val members: StateFlow<List<ChatGroupMember>> = _state
        .map { it.conversation }
        .filterNotNull()
        .distinctUntilChangedBy { it.id }
        .flatMapLatest { c -> if (c.isGroup) chat.members(c.id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _myId = MutableStateFlow<String?>(null)

    /** My user ID: which poll choices are mine, which member is me. */
    val myId: StateFlow<String?> = _myId.asStateFlow()

    /** My friends, for adding people to a group. */
    val friendList = friends.friends

    /** The friend on the other side (name, photo, relationship), for the header. */
    val peer: StateFlow<Friend?> = combine(_state.map { it.conversation?.peerId }, friends.friends) { id, list ->
        list?.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val timeline: StateFlow<List<TimelineItem>> = _state
        .map { it.conversation }
        .filterNotNull()
        .distinctUntilChangedBy { it.id }
        .flatMapLatest { c ->
            combine(chat.messages(c.id), gallery.forPeer(c.peerId), serverWalls) { messages, local, server ->
                // Pictures kept on this phone, with the newest status from the server.
                val kept = local.map { w ->
                    TimelineItem.Wallpaper(w.commandId, w.fromMe, server[w.commandId]?.status ?: w.status, w.at, w.file, w.target)
                }
                val keptIds = kept.mapTo(HashSet()) { it.id }
                val older = server.values.filter { it.id !in keptIds }
                (messages.map { TimelineItem.Message(it) } + kept + older).sortedBy { it.at }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun open(conversationId: String?, peerId: String?) {
        if (opened) return
        opened = true
        viewModelScope.launch {
            val conversation = when {
                conversationId != null -> chat.conversation(conversationId)
                peerId != null -> {
                    val name = (friends.friends.value ?: (friends.refreshFriends() as? Outcome.Success)?.value)
                        ?.firstOrNull { it.id == peerId }?.card?.name.orEmpty()
                    when (val outcome = chat.openWith(peerId, name)) {
                        is Outcome.Success -> outcome.value
                        is Outcome.Failure -> {
                            _state.update { it.copy(loading = false, error = outcome.error) }
                            return@launch
                        }
                    }
                }
                else -> null
            }
            _state.update { it.copy(loading = false, conversation = conversation, error = if (conversation == null) AppError.Unknown else null) }
            if (conversation != null) {
                _myId.value = chat.myUserId()
                scheduler.fetch()
                // Photos, videos or PDFs that haven't been fetched yet.
                chat.fetchMediaSoon()
                if (conversation.isGroup) {
                    // Fresh names and members. Groups have no wallpapers or relationship to follow.
                    if (visibility.openConversationId == conversation.id) markRead()
                    chat.refreshGroups()
                    return@launch
                }
                // Fresh, so a new Best Friend / girlfriend / boyfriend shows (and unlocks) right away.
                friends.refreshFriends()
                if (visibility.openConversationId == conversation.id) markRead()
                // Wallpaper ticks follow the server while the chat is on screen.
                while (isActive) {
                    if (visibility.openConversationId == conversation.id || serverWalls.value.isEmpty()) loadWallpapers(conversation.peerId)
                    delay(WALLPAPER_POLL_MS)
                }
            }
        }
    }

    private suspend fun loadWallpapers(peerId: String) {
        val sent = (wallpapers.sent() as? Outcome.Success)?.value.orEmpty()
            .filter { it.recipient.id == peerId }
            .map { TimelineItem.Wallpaper(it.id, fromMe = true, status = it.status, at = millis(it.createdAt), target = WallpaperTarget.fromWire(it.target)) }
        val received = (wallpapers.received() as? Outcome.Success)?.value.orEmpty()
            .filter { it.sender.id == peerId }
            .map {
                // From before the chat kept its own copy: show the Undo copy if this phone still has it.
                TimelineItem.Wallpaper(
                    it.id,
                    fromMe = false,
                    status = it.status,
                    at = millis(it.createdAt),
                    file = gallery.historyCopy(it.id),
                    target = WallpaperTarget.fromWire(it.target),
                )
            }
        val all = sent + received
        if (all.isEmpty() && serverWalls.value.isNotEmpty()) return // offline: keep what we had
        // Remember the newest status on this phone too.
        all.forEach { gallery.setStatus(it.id, it.status) }
        serverWalls.value = all.associateBy { it.id }
    }

    private fun millis(text: String): Long = parseInstant(text)?.toEpochMilli() ?: 0L

    private companion object {
        const val WALLPAPER_POLL_MS = 10_000L
        const val PRESENCE_POLL_MS = 3_000L
        const val HEARTBEAT_EVERY = 6
        const val TYPING_REPORT_MS = 3_000L
        const val GROUP_RECEIPT_POLL_MS = 6_000L
    }

    /** The chat is on screen: no notifications for it, and what's visible counts as read. */
    fun onVisible(visible: Boolean) {
        val id = _state.value.conversation?.id
        if (visible) {
            visibility.openConversationId = id
            if (id != null) markRead()
            if (id != null) watchPresence(id)
        } else {
            if (visibility.openConversationId == id) visibility.openConversationId = null
            presenceJob?.cancel()
            presenceJob = null
            // Tell the other side this chat is closed again (best effort).
            viewModelScope.launch { presence.heartbeat(null) }
        }
    }

    /** While the chat is on screen: say so, and keep the friend's status fresh. */
    private fun watchPresence(conversationId: String) {
        if (presenceJob?.isActive == true) return
        val conversation = _state.value.conversation ?: return
        val peerId = conversation.peerId
        if (conversation.isGroup) {
            // No "online" in a group; only keep the lights fresh while my messages aren't seen.
            presenceJob = viewModelScope.launch {
                while (isActive) {
                    if (awaitingReceipts()) runCatching { inbox.fetch() }
                    delay(GROUP_RECEIPT_POLL_MS)
                }
            }
            return
        }
        presenceJob = viewModelScope.launch {
            presence.heartbeat(conversationId)
            var beats = 0
            while (isActive) {
                (presence.peer(peerId) as? Outcome.Success)?.value?.let { _peerPresence.value = it }
                // Pushes can be late: while my messages here aren't seen yet, look for receipts
                // directly so the light goes red → yellow → green as it happens.
                if (awaitingReceipts()) runCatching { inbox.fetch() }
                delay(PRESENCE_POLL_MS)
                // Keep "online" and "has the chat open" alive on the other side.
                if (++beats % HEARTBEAT_EVERY == 0) presence.heartbeat(conversationId)
            }
        }
    }

    private fun awaitingReceipts(): Boolean = timeline.value.any {
        it is TimelineItem.Message && it.message.fromMe &&
            (it.message.status == ChatRepository.STATUS_SENT || it.message.status == ChatRepository.STATUS_DELIVERED)
    }

    /** Called as the person types; reported at most every few seconds. */
    fun onTyping() {
        if (_state.value.conversation?.isGroup == true) return
        val id = _state.value.conversation?.id ?: return
        val now = System.currentTimeMillis()
        if (now - lastTypingReport < TYPING_REPORT_MS) return
        lastTypingReport = now
        viewModelScope.launch { presence.heartbeat(id, typing = true) }
    }

    fun markRead() {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch {
            chat.markRead(id)
            notifications.cancel(id)
        }
    }

    fun send(text: String) {
        val id = _state.value.conversation?.id ?: return
        val editing = _state.value.editing
        val reply = _state.value.replyTo
        val group = _state.value.conversation?.isGroup == true
        _state.update { it.copy(replyTo = null, editing = null) }
        lastTypingReport = 0L
        viewModelScope.launch {
            if (editing != null) chat.edit(id, editing.id, text) else chat.sendText(id, text, reply?.id)
            // Sent: no longer typing.
            if (!group) presence.heartbeat(id, typing = false)
        }
    }

    fun sendSticker(key: String) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.sendSticker(id, key) }
    }

    /** A photo, video or PDF picked from the phone: prepared, encrypted and sent. */
    fun sendMedia(context: Context, uri: Uri) {
        val id = _state.value.conversation?.id ?: return
        val app = context.applicationContext
        viewModelScope.launch {
            val note = when (val prepared = ChatMediaPreparer.prepare(app, uri)) {
                is PrepareResult.Ready -> {
                    val m = prepared.media
                    val sent = chat.sendFile(id, m.file, m.mime, m.name, m.width, m.height, m.durationMs, m.thumb)
                    if (sent == null) R.string.chat_media_failed else null
                }
                PrepareResult.TooBig -> R.string.chat_media_too_big
                PrepareResult.Unsupported -> R.string.chat_media_unsupported
                PrepareResult.Failed -> R.string.chat_media_failed
            }
            if (note != null) _state.update { it.copy(note = note) }
        }
    }

    /** A poll for everyone in the chat. */
    fun sendPoll(question: String, options: List<String>, multi: Boolean) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch {
            if (chat.sendPoll(id, question, options, multi) == null) _state.update { it.copy(note = R.string.group_note_poll_failed) }
        }
    }

    /** My choices in a poll (empty takes my vote back). */
    fun vote(message: ChatMessage, choices: List<Int>) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.vote(id, message.id, choices) }
    }

    /** A reminder that goes into everyone's calendar. */
    fun sendGroupReminder(what: String, start: LocalDateTime) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch {
            if (chat.sendReminder(id, what, start) == null) _state.update { it.copy(note = R.string.group_note_reminder_failed) }
        }
    }

    fun renameGroup(title: String) {
        val conversation = _state.value.conversation ?: return
        viewModelScope.launch {
            chat.renameGroup(conversation.id, title)
            chat.conversation(conversation.id)?.let { fresh -> _state.update { it.copy(conversation = fresh) } }
        }
    }

    fun addMembers(ids: List<String>) = groupChange(R.string.group_note_added) { chat.addGroupMembers(it, ids) }

    fun removeMember(userId: String) = groupChange(R.string.group_note_removed) { chat.removeGroupMember(it, userId) }

    /** Leaves the group; [onLeft] once the server agreed (back to the list). */
    fun leaveGroup(onLeft: () -> Unit) = groupChange(null, onLeft) { chat.leaveGroup(it) }

    private fun groupChange(done: Int?, then: () -> Unit = {}, block: suspend (String) -> Outcome<GroupChange>) {
        val id = _state.value.conversation?.id ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val note = when (val outcome = block(id)) {
                is Outcome.Success -> when (val change = outcome.value) {
                    is GroupChange.Done -> {
                        _state.update { it.copy(conversation = change.conversation) }
                        then()
                        done
                    }
                    is GroupChange.Refused -> when (change.reason) {
                        "not_admin" -> R.string.group_note_not_admin
                        "not_friends" -> R.string.group_note_not_friends
                        "full" -> R.string.group_note_full
                        "rate_limited" -> R.string.group_note_slow_down
                        "needs_update" -> R.string.group_note_needs_update_short
                        else -> R.string.group_note_failed
                    }
                }
                is Outcome.Failure -> R.string.group_note_failed
            }
            _state.update { it.copy(busy = false, note = note) }
        }
    }

    fun react(message: ChatMessage, emoji: String) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.react(id, message.id, emoji) }
    }

    fun delete(message: ChatMessage) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.delete(id, message.id) }
    }

    fun retry(message: ChatMessage) {
        viewModelScope.launch { chat.retry(message.id) }
    }

    /** Girlfriend / boyfriend only: switch their phone to ring, vibrate or mute. */
    fun setRinger(mode: String) {
        val peerId = _state.value.conversation?.peerId ?: return
        viewModelScope.launch {
            val note = when (val outcome = friends.setRinger(peerId, mode)) {
                is Outcome.Success -> when (outcome.value) {
                    ChangeResult.Ok -> when (mode) {
                        "mute" -> R.string.chat_ringer_sent_mute
                        "vibrate" -> R.string.chat_ringer_sent_vibrate
                        else -> R.string.chat_ringer_sent_ring
                    }
                    ChangeResult.NeedsCloserLevel -> R.string.chat_ringer_partner_only
                    ChangeResult.RateLimited -> R.string.chat_ringer_slow_down
                    else -> R.string.chat_ringer_unavailable
                }
                is Outcome.Failure -> R.string.chat_ringer_offline
            }
            _state.update { it.copy(note = note) }
        }
    }

    fun noteShown() = _state.update { it.copy(note = null) }

    /** The menu under their name opened: make sure the relationship shown is the latest. */
    fun refreshPeer() {
        viewModelScope.launch { friends.refreshFriends() }
    }

    /** Ask them to be a Best Friend or girlfriend / boyfriend (they have to say yes). */
    fun invite(level: RelationshipLevel, label: String?) = relationship { friends.invite(it, level, label) }

    fun answerInvite(accept: Boolean) = relationship { _ ->
        val invite = peer.value?.invite ?: return@relationship Outcome.Success(ChangeResult.Gone)
        friends.respondInvite(invite.id, accept)
    }

    fun cancelInvite() = relationship { _ ->
        val invite = peer.value?.invite ?: return@relationship Outcome.Success(ChangeResult.Gone)
        friends.cancelInvite(invite.id)
    }

    private fun relationship(block: suspend (String) -> Outcome<ChangeResult>) {
        val peerId = _state.value.conversation?.peerId ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val note = when (val outcome = block(peerId)) {
                is Outcome.Success -> when (outcome.value) {
                    ChangeResult.Pending -> R.string.chat_note_invited
                    ChangeResult.Accepted -> R.string.chat_note_accepted
                    ChangeResult.Declined -> R.string.chat_note_declined
                    ChangeResult.Ok, ChangeResult.Gone -> R.string.chat_note_cancelled
                    ChangeResult.AlreadyPartnered -> R.string.chat_note_already_partnered
                    ChangeResult.AlreadyAsked -> R.string.chat_note_already_asked
                    ChangeResult.AnswerTheirsFirst -> R.string.chat_note_answer_theirs
                    ChangeResult.Unavailable -> R.string.chat_note_unavailable
                    else -> R.string.chat_note_failed
                }
                is Outcome.Failure -> R.string.chat_note_failed
            }
            friends.refreshFriends()
            _state.update { it.copy(busy = false, note = note) }
        }
    }

    fun toggleSaved(message: ChatMessage) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.setSaved(id, message.id, !message.saved) }
    }

    /** Deletes this chat's messages from this phone (theirs stay on their phone). */
    fun clearChat() {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch {
            chat.clearChat(id)
            _state.update { it.copy(replyTo = null, editing = null, note = R.string.chat_note_cleared) }
        }
    }

    /** Left the chat (back, or the app went away): with Disappearing chats on, seen, unsaved messages go. */
    fun leave() {
        _state.value.conversation?.id?.let(chat::clearSeenSoon)
    }

    fun replyTo(message: ChatMessage?) = _state.update { it.copy(replyTo = message, editing = null) }

    fun startEdit(message: ChatMessage?) = _state.update { it.copy(editing = message, replyTo = null) }

    fun setMuted(muted: Boolean) {
        val conversation = _state.value.conversation ?: return
        viewModelScope.launch {
            chat.setMuted(conversation.id, muted)
            _state.update { it.copy(conversation = conversation.copy(muted = muted)) }
        }
    }

    override fun onCleared() {
        val id = _state.value.conversation?.id
        if (id != null && visibility.openConversationId == id) visibility.openConversationId = null
        if (id != null) chat.clearSeenSoon(id)
    }
}

data class NewGroupState(val busy: Boolean = false, val error: Int? = null, val errorArg: String = "", val openId: String? = null)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
    private val scheduler: ChatScheduler,
) : ViewModel() {
    init {
        // No chat is open here, so anything already seen (and not saved) can go.
        viewModelScope.launch { runCatching { chat.clearAllSeen() } }
    }

    val conversations: StateFlow<List<ChatConversation>?> =
        chat.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val friendList = friends.friends

    private val _newGroup = MutableStateFlow(NewGroupState())

    /** Making a group: busy, why it failed, or the new chat to open. */
    val newGroup: StateFlow<NewGroupState> = _newGroup.asStateFlow()

    fun createGroup(title: String, memberIds: List<String>) {
        if (_newGroup.value.busy) return
        viewModelScope.launch {
            _newGroup.value = NewGroupState(busy = true)
            _newGroup.value = when (val outcome = chat.createGroup(title, memberIds)) {
                is Outcome.Success -> when (val change = outcome.value) {
                    is GroupChange.Done -> NewGroupState(openId = change.conversation.id)
                    is GroupChange.Refused -> NewGroupState(
                        error = when (change.reason) {
                            "not_friends" -> R.string.group_note_not_friends
                            "rate_limited" -> R.string.group_note_slow_down
                            "invalid" -> R.string.group_note_too_many
                            "needs_update" -> R.string.group_note_needs_update
                            else -> R.string.group_note_failed
                        },
                        // Who has to update meyouww first.
                        errorArg = friends.friends.value.orEmpty().filter { it.id in change.users }.joinToString { it.card.name },
                    )
                }
                is Outcome.Failure -> NewGroupState(error = R.string.group_note_failed)
            }
        }
    }

    fun groupOpened() {
        _newGroup.value = NewGroupState()
    }

    fun refresh() = viewModelScope.launch {
        scheduler.fetch()
        scheduler.send()
        friends.refreshFriends()
    }
}
