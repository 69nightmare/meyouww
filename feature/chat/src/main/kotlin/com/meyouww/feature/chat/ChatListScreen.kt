package com.meyouww.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CountBadge
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import java.text.DateFormat
import java.util.Date

/** Friends and existing conversations share one entry point; all actions use the existing repositories. */
@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onAddFriend: () -> Unit,
    onManageFriends: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friends by viewModel.friendList.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var query by rememberSaveable { mutableStateOf("") }

    CrayonScreen(title = stringResource(R.string.people_title)) {
        Text(stringResource(R.string.people_subtitle), style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
        CrayonTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.people_search),
            placeholder = stringResource(R.string.people_search_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CrayonButton(stringResource(R.string.people_add), onClick = onAddFriend, modifier = Modifier.weight(1f))
            CrayonButton(stringResource(R.string.people_requests), onClick = onManageFriends, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
        }
        SectionTitle(stringResource(R.string.people_conversations))
        val list = conversations
        if (list == null) {
            WhityLoading(stringResource(R.string.chat_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
        } else {
            val people = friends.orEmpty()
            val byId = people.associateBy { it.id }
            val withChat = list.mapTo(mutableSetOf()) { it.peerId }
            val term = query.trim()
            val visibleChats = list.filter { conversation ->
                val person = byId[conversation.peerId]
                term.isEmpty() || conversation.peerName.contains(term, ignoreCase = true) ||
                    person?.card?.name?.contains(term, ignoreCase = true) == true ||
                    person?.card?.handle?.contains(term, ignoreCase = true) == true
            }
            val newFriends = people.filter { friend ->
                friend.id !in withChat && (term.isEmpty() || friend.card.name.contains(term, ignoreCase = true) ||
                    friend.card.handle.contains(term, ignoreCase = true))
            }
            if (visibleChats.isEmpty() && newFriends.isEmpty()) {
                if (term.isNotEmpty()) {
                    Text(stringResource(R.string.people_no_match), style = MaterialTheme.typography.bodyMedium)
                } else {
                    StickerMessage(WhitySticker.Peek, title = stringResource(R.string.people_empty_title), body = stringResource(R.string.people_empty_body))
                }
            }
            visibleChats.forEach { conversation ->
                ConversationRow(conversation, byId[conversation.peerId], onClick = { onOpenChat(conversation.id) })
            }
            newFriends.forEach { friend -> FriendChatRow(friend, onClick = { onStartChat(friend.id) }) }
        }
        Text(stringResource(R.string.chat_list_e2ee), style = MaterialTheme.typography.bodySmall, color = MeyouwwTheme.crayon.textSoft)
    }
}

@Composable
private fun ConversationRow(conversation: ChatConversation, friend: Friend?, onClick: () -> Unit) {
    val name = friend?.card?.name?.ifBlank { null } ?: conversation.peerName.ifBlank { stringResource(R.string.chat_friend) }
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        seed = conversation.id.hashCode(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.chat_list_open, name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 48.dp, photo = friend?.card?.avatar)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conversationPreview(conversation.lastText, conversation.lastKind, conversation.lastFromMe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeyouwwTheme.crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (conversation.lastKind != null) {
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conversation.lastAt)), style = MaterialTheme.typography.labelSmall)
                }
                CountBadge(conversation.unread)
            }
        }
    }
}

@Composable
private fun FriendChatRow(friend: Friend, onClick: () -> Unit) {
    val card = friend.card
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        seed = friend.id.hashCode(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.chat_list_open, card.name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(card.initial, friend.id.hashCode(), size = 48.dp, photo = card.avatar)
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.chat_list_empty_preview), style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
            }
        }
    }
}
