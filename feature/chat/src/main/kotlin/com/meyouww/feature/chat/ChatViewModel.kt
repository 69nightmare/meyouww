package com.meyouww.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.ChatScheduler
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.Friend
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.model.parseInstant
import com.meyouww.core.notifications.chat.ChatNotifications
import com.meyouww.core.notifications.push.WallpaperGallery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
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
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Wallpapers as the server knows them (status), by command ID. */
    private val serverWalls = MutableStateFlow<Map<String, TimelineItem.Wallpaper>>(emptyMap())
    private var opened = false

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
                scheduler.fetch()
                if (friends.friends.value == null) friends.refreshFriends()
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
            .map { TimelineItem.Wallpaper(it.id, fromMe = false, status = it.status, at = millis(it.createdAt), target = WallpaperTarget.fromWire(it.target)) }
        val all = sent + received
        if (all.isEmpty() && serverWalls.value.isNotEmpty()) return // offline: keep what we had
        // Remember the newest status on this phone too.
        all.forEach { gallery.setStatus(it.id, it.status) }
        serverWalls.value = all.associateBy { it.id }
    }

    private fun millis(text: String): Long = parseInstant(text)?.toEpochMilli() ?: 0L

    private companion object {
        const val WALLPAPER_POLL_MS = 10_000L
    }

    /** The chat is on screen: no notifications for it, and what's visible counts as read. */
    fun onVisible(visible: Boolean) {
        val id = _state.value.conversation?.id
        if (visible) {
            visibility.openConversationId = id
            if (id != null) markRead()
        } else if (visibility.openConversationId == id) {
            visibility.openConversationId = null
        }
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
        _state.update { it.copy(replyTo = null, editing = null) }
        viewModelScope.launch {
            if (editing != null) chat.edit(id, editing.id, text) else chat.sendText(id, text, reply?.id)
        }
    }

    fun sendSticker(key: String) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.sendSticker(id, key) }
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

    fun toggleSaved(message: ChatMessage) {
        val id = _state.value.conversation?.id ?: return
        viewModelScope.launch { chat.setSaved(id, message.id, !message.saved) }
    }

    /** Left the chat (back, or the app went away): seen, unsaved messages disappear. */
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

    fun refresh() = viewModelScope.launch {
        scheduler.fetch()
        scheduler.send()
        friends.refreshFriends()
    }
}
