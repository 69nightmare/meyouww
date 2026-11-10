package com.meyouww.feature.chat

import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import java.text.DateFormat
import java.util.Date

/**
 * Friends and existing conversations share one entry point; all actions use the existing repositories.
 * Searching, adding a friend and requests live in a pop-up behind the + button, so the screen
 * opens straight onto the conversations.
 */
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
    var finding by rememberSaveable { mutableStateOf(false) }
    var makingGroup by rememberSaveable { mutableStateOf(false) }
    var listQuery by rememberSaveable { mutableStateOf("") }
    val listContext = LocalContext.current
    val pinLimit = stringResource(R.string.chat_pin_limit)
    val newGroup by viewModel.newGroup.collectAsStateWithLifecycle()
    val crayon = MeyouwwTheme.crayon
    // Made: open the new group's chat.
    LaunchedEffect(newGroup.openId) {
        val id = newGroup.openId ?: return@LaunchedEffect
        makingGroup = false
        viewModel.groupOpened()
        onOpenChat(id)
    }
    if (makingGroup) {
        PickFriendsDialog(
            title = stringResource(R.string.group_new),
            friends = friends.orEmpty(),
            confirm = stringResource(R.string.group_create),
            askName = true,
            busy = newGroup.busy,
            error = newGroup.error?.let { stringResource(it, newGroup.errorArg) },
            maxPick = GROUP_MAX - 1,
            onDismiss = {
                makingGroup = false
                viewModel.groupOpened()
            },
            onConfirm = { name, ids -> viewModel.createGroup(name, ids) },
        )
    }

    if (finding) {
        FindPersonDialog(
            conversations = conversations.orEmpty(),
            friends = friends.orEmpty(),
            onDismiss = { finding = false },
            onOpenChat = {
                finding = false
                onOpenChat(it)
            },
            onStartChat = {
                finding = false
                onStartChat(it)
            },
            onAddFriend = {
                finding = false
                onAddFriend()
            },
            onManageFriends = {
                finding = false
                onManageFriends()
            },
            onNewGroup = {
                finding = false
                makingGroup = true
            },
        )
    }

    CrayonScreen(
        title = stringResource(R.string.people_title),
        trailing = {
            RoundButton(fill = crayon.yellow, label = stringResource(R.string.people_find_open), onClick = { finding = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = crayon.onFill)
            }
        },
    ) {
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 612) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.people_subtitle), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Sticker(WhitySticker.Hi, size = 64.dp, decorative = true)
            }
        }
        CrayonTextField(
            value = listQuery,
            onValueChange = { listQuery = it },
            label = stringResource(R.string.people_search_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle(stringResource(R.string.people_conversations))
        val list = conversations
        if (list == null) {
            WhityLoading(stringResource(R.string.chat_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
        } else {
            val term = listQuery.trim()
            val matches = peopleMatching(list, friends.orEmpty(), term = term)
            if (matches.isEmpty && term.isEmpty()) {
                StickerMessage(WhitySticker.Peek, title = stringResource(R.string.people_empty_title), body = stringResource(R.string.people_empty_body))
            } else if (matches.isEmpty) {
                Text(stringResource(R.string.people_no_match), style = MaterialTheme.typography.bodyMedium)
            }
            PeopleRows(
                matches,
                onOpenChat = onOpenChat,
                onStartChat = onStartChat,
                onPin = { conversation ->
                    viewModel.setPinned(conversation.id, !conversation.pinned) {
                        Toast.makeText(listContext, pinLimit, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        Text(stringResource(R.string.chat_list_e2ee), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }
}

/** The pop-up behind the + button: search your people, add a friend, or see requests. */
@Composable
private fun FindPersonDialog(
    conversations: List<ChatConversation>,
    friends: List<Friend>,
    onDismiss: () -> Unit,
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onAddFriend: () -> Unit,
    onManageFriends: () -> Unit,
    onNewGroup: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CrayonCard(
            modifier = Modifier.padding(16.dp).widthIn(max = 480.dp).fillMaxWidth(),
            seed = 613,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.people_search), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.people_find_close))
                    }
                }
                CrayonTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.people_search_hint),
                    modifier = Modifier.fillMaxWidth(),
                )
                val term = query.trim()
                if (term.isNotEmpty()) {
                    val matches = peopleMatching(conversations, friends, term)
                    if (matches.isEmpty) {
                        Text(stringResource(R.string.people_no_match), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        ) {
                            PeopleRows(matches, onOpenChat = onOpenChat, onStartChat = onStartChat)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CrayonButton(stringResource(R.string.people_add), onClick = onAddFriend, modifier = Modifier.weight(1f))
                    CrayonButton(stringResource(R.string.people_requests), onClick = onManageFriends, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                }
                CrayonButton(stringResource(R.string.group_new), onClick = onNewGroup, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

internal class PeopleMatches(
    val chats: List<Pair<ChatConversation, Friend?>>,
    val newFriends: List<Friend>,
) {
    val isEmpty get() = chats.isEmpty() && newFriends.isEmpty()
}

/** Conversations, then friends you haven't chatted with yet, filtered by name or @handle. */
internal fun peopleMatching(conversations: List<ChatConversation>, friends: List<Friend>, term: String): PeopleMatches {
    val byId = friends.associateBy { it.id }
    val withChat = conversations.mapTo(mutableSetOf()) { it.peerId }
    val chats = conversations.filter { conversation ->
        val person = byId[conversation.peerId]
        // peerName is the nickname when I gave one, so both names are searched.
        term.isEmpty() || conversation.peerName.contains(term, ignoreCase = true) ||
            person?.card?.name?.contains(term, ignoreCase = true) == true ||
            person?.card?.handle?.contains(term, ignoreCase = true) == true
    }.map { it to byId[it.peerId] }
    val newFriends = friends.filter { friend ->
        friend.id !in withChat && (term.isEmpty() || friend.card.name.contains(term, ignoreCase = true) ||
            friend.card.handle.contains(term, ignoreCase = true))
    }
    return PeopleMatches(chats, newFriends)
}

@Composable
private fun PeopleRows(
    matches: PeopleMatches,
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onPin: ((ChatConversation) -> Unit)? = null,
) {
    matches.chats.forEach { (conversation, friend) ->
        ConversationRow(conversation, friend, onClick = { onOpenChat(conversation.id) }, onPin = onPin?.let { pin -> { pin(conversation) } })
    }
    matches.newFriends.forEach { friend -> FriendChatRow(friend, onClick = { onStartChat(friend.id) }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(conversation: ChatConversation, friend: Friend?, onClick: () -> Unit, onPin: (() -> Unit)? = null) {
    // A nickname I gave wins over their own name.
    val name = conversation.nickname?.ifBlank { null }
        ?: friend?.card?.name?.ifBlank { null }
        ?: conversation.peerName.ifBlank { stringResource(if (conversation.isGroup) R.string.group_default_name else R.string.chat_friend) }
    var menu by remember { mutableStateOf(false) }
    val pinLabel = stringResource(if (conversation.pinned) R.string.chat_unpin else R.string.chat_pin)
    Box {
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(
            text = { Text(pinLabel) },
            onClick = {
                menu = false
                onPin?.invoke()
            },
        )
    }
    CrayonCard(
        // Tap opens the chat; press and hold pins it to the top (or lets it go).
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).combinedClickable(
            onClickLabel = stringResource(R.string.chat_list_open, name),
            onLongClickLabel = pinLabel,
            onLongClick = { if (onPin != null) menu = true },
            onClick = onClick,
        ),
        seed = conversation.id.hashCode(),
        fill = if (conversation.unread > 0) MeyouwwTheme.crayon.blue.copy(alpha = 0.32f) else MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 48.dp, photo = friend?.card?.avatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conversationPreview(conversation.lastText, conversation.lastKind, conversation.lastFromMe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MeyouwwTheme.crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conversation.lastKind != null) {
                    Text(
                        (if (conversation.pinned) "📌 " else "") + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(conversation.lastAt)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else if (conversation.pinned) {
                    Text("📌", style = MaterialTheme.typography.labelSmall)
                }
                CountBadge(conversation.unread)
            }
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
