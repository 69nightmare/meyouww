package com.meyouww.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.meyouww.core.designsystem.component.CountBadge
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatListScreen(
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onAddFriend: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val friends by viewModel.friendList.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var picking by rememberSaveable { mutableStateOf(false) }

    CrayonScreen(title = stringResource(R.string.chat_list_title)) {
        CrayonButton(stringResource(R.string.chat_list_new), onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
        val list = conversations
        when {
            list == null -> WhityLoading(stringResource(R.string.chat_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
            list.isEmpty() -> StickerMessage(
                WhitySticker.Peek,
                title = stringResource(R.string.chat_list_empty_title),
                body = stringResource(R.string.chat_list_empty_body),
            )
            else -> list.forEach { conversation -> ConversationRow(conversation, onClick = { onOpenChat(conversation.id) }) }
        }
        Text(stringResource(R.string.chat_list_e2ee), style = MaterialTheme.typography.bodySmall, color = MeyouwwTheme.crayon.textSoft)
    }

    if (picking) {
        val people = friends.orEmpty()
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.chat_pick_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (people.isEmpty()) Text(stringResource(R.string.chat_pick_none))
                    people.forEach { friend ->
                        TextButton(onClick = {
                            picking = false
                            onStartChat(friend.id)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(friend.card.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                if (people.isEmpty()) {
                    TextButton(onClick = {
                        picking = false
                        onAddFriend()
                    }) { Text(stringResource(R.string.chat_pick_add_friend)) }
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.chat_cancel)) } },
        )
    }
}

@Composable
private fun ConversationRow(conversation: ChatConversation, onClick: () -> Unit) {
    val name = conversation.peerName.ifBlank { stringResource(R.string.chat_friend) }
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        seed = conversation.id.hashCode(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.chat_list_open, name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 48.dp)
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
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conversation.lastAt)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                CountBadge(conversation.unread)
            }
        }
    }
}
