package com.meyouww.feature.chat

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What another app handed to meyouww through Android's share sheet: photos, videos, PDFs or
 * Word files, and / or some text. Nothing is sent until the person picks who it is for.
 */
data class IncomingShare(val uris: List<Uri>, val text: String?) {
    companion object {
        /** The most files one share sends (the rest are left out and counted as not sent). */
        const val MAX_FILES = 10
    }
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
) : ViewModel() {
    val conversations: StateFlow<List<ChatConversation>?> =
        chat.conversations().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val friendList = friends.friends

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    init {
        viewModelScope.launch { friends.refreshFriends() }
    }

    /**
     * Sends everything to one chat (an existing one, or a friend's, opened first).
     * [onDone]: the chat it went to (null if it could not be opened) and how many items failed.
     */
    fun send(context: Context, share: IncomingShare, conversationId: String?, friendId: String?, onDone: (String?, Int) -> Unit) {
        if (_sending.value) return
        val app = context.applicationContext
        viewModelScope.launch {
            _sending.value = true
            val id = conversationId ?: friendId?.let { friend ->
                val name = friends.friends.value?.firstOrNull { it.id == friend }?.card?.name.orEmpty()
                (chat.openWith(friend, name) as? Outcome.Success)?.value?.id
            }
            var failed = (share.uris.size - IncomingShare.MAX_FILES).coerceAtLeast(0)
            if (id == null) {
                failed = share.uris.size + if (share.text.isNullOrBlank()) 0 else 1
            } else {
                // Copied straight away: the other app's permission to read them ends with this screen.
                for (uri in share.uris.take(IncomingShare.MAX_FILES)) {
                    when (val prepared = ChatMediaPreparer.prepare(app, uri)) {
                        is PrepareResult.Ready -> {
                            val m = prepared.media
                            if (chat.sendFile(id, m.file, m.mime, m.name, m.width, m.height, m.durationMs, m.thumb) == null) failed++
                        }
                        else -> failed++
                    }
                }
                share.text?.takeIf { it.isNotBlank() }?.let { if (chat.sendText(id, it) == null) failed++ }
            }
            _sending.value = false
            onDone(id, failed)
        }
    }
}

/** "Send to…": pick the chat or friend that the shared photos, videos, documents or text go to. */
@Composable
fun ShareToChatDialog(
    share: IncomingShare,
    onDismiss: () -> Unit,
    onSent: (conversationId: String) -> Unit,
    viewModel: ShareViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friends by viewModel.friendList.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    var query by rememberSaveable { mutableStateOf("") }
    val failedText = stringResource(R.string.share_failed)

    fun send(conversationId: String?, friendId: String?) {
        viewModel.send(context, share, conversationId, friendId) { id, failed ->
            if (failed > 0) Toast.makeText(context, failedText, Toast.LENGTH_LONG).show()
            if (id != null) onSent(id) else onDismiss()
        }
    }

    Dialog(onDismissRequest = { if (!sending) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CrayonCard(modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(), seed = 917) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(onClick = onDismiss, enabled = !sending) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel))
                    }
                }
                val what = buildList {
                    if (share.uris.isNotEmpty()) add(pluralStringResource(R.plurals.share_files, share.uris.size, share.uris.size))
                    share.text?.takeIf { it.isNotBlank() }?.let { add("“" + it.take(80) + "”") }
                }.joinToString(" · ")
                Text(what, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (sending) {
                    WhityLoading(stringResource(R.string.share_sending), Modifier.fillMaxWidth().padding(vertical = 12.dp))
                } else {
                    CrayonTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.people_search_hint),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val matches = peopleMatching(conversations.orEmpty(), friends.orEmpty(), query.trim())
                    if (matches.isEmpty) {
                        Text(
                            stringResource(if (query.isBlank()) R.string.share_nobody else R.string.people_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    ) {
                        matches.chats.forEach { (conversation, friend) ->
                            val name = conversation.nickname?.ifBlank { null }
                                ?: friend?.card?.name?.ifBlank { null }
                                ?: conversation.peerName.ifBlank { stringResource(if (conversation.isGroup) R.string.group_default_name else R.string.chat_friend) }
                            ShareRow(name, conversation.peerId, friend?.card?.avatar) { send(conversation.id, null) }
                        }
                        matches.newFriends.forEach { friend ->
                            ShareRow(friend.card.name, friend.id, friend.card.avatar) { send(null, friend.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareRow(name: String, seed: String, photo: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.share_send_to, name), onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        PersonAvatar(name.take(1).uppercase(), seed.hashCode(), size = 44.dp, photo = photo)
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
