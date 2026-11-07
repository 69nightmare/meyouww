package com.meyouww.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import java.text.DateFormat
import java.util.Date

private val QUICK_REACTIONS = listOf("❤️", "😂", "😮", "😢", "👍", "🙏")

/** Sticker keys in messages: "whity:<name>". */
internal fun stickerFor(key: String?): WhitySticker? =
    key?.removePrefix("whity:")?.let { name -> WhitySticker.entries.firstOrNull { it.name == name } }

@Composable
fun ChatScreen(
    conversationId: String?,
    peerId: String?,
    onBack: () -> Unit,
    onOpenSafety: (String) -> Unit,
    onDraw: (String) -> Unit,
    onSendPhoto: (String) -> Unit,
    onAlarm: ((String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId, peerId) { viewModel.open(conversationId, peerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onVisible(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onVisible(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.leave() }
    // New messages that arrive while the chat is on screen count as read straight away.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timeline.size, state.conversation?.id) {
        if (state.conversation != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) viewModel.onVisible(true)
    }

    val crayon = MeyouwwTheme.crayon
    val conversation = state.conversation
    val name = conversation?.peerName?.ifBlank { null } ?: stringResource(R.string.chat_friend)

    Column(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
            }
            if (conversation != null) {
                PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 40.dp)
            }
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 10.dp).semantics { heading() },
            )
            if (conversation != null) {
                IconButton(onClick = { onOpenSafety(conversation.peerId) }) {
                    Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.chat_safety_open))
                }
                var menu by rememberSaveable { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (conversation.muted) R.string.chat_unmute else R.string.chat_mute)) },
                            onClick = {
                                menu = false
                                viewModel.setMuted(!conversation.muted)
                            },
                        )
                    }
                }
            }
        }

        when {
            state.loading -> WhityLoading(stringResource(R.string.chat_loading), Modifier.fillMaxWidth().weight(1f).padding(top = 48.dp))
            conversation == null -> Box(Modifier.weight(1f).padding(20.dp)) {
                StickerMessage(
                    WhitySticker.Confused,
                    title = stringResource(R.string.chat_cannot_open),
                    body = state.error?.let { stringResource(it.messageRes()) }.orEmpty(),
                )
            }
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(timeline.size) { if (timeline.isNotEmpty()) listState.animateScrollToItem(timeline.size) }
                val byId = remember(timeline) { timeline.filterIsInstance<TimelineItem.Message>().associateBy { it.message.id } }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    item(key = "e2ee") {
                        CrayonChip(
                            text = stringResource(R.string.chat_e2ee_notice, name),
                            fill = crayon.yellow,
                            onClick = { onOpenSafety(conversation.peerId) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        )
                        Text(
                            stringResource(R.string.chat_disappearing_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = crayon.textSoft,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    items(timeline, key = { it.key }) { item ->
                        when (item) {
                            is TimelineItem.Wallpaper -> WallpaperRow(item, name)
                            is TimelineItem.Message -> if (item.message.kind == ChatRepository.KIND_SYSTEM) {
                                SystemRow(item.message, name, onOpenSafety = { onOpenSafety(conversation.peerId) })
                            } else {
                                MessageRow(
                                    message = item.message,
                                    peerName = name,
                                    quoted = item.message.replyTo?.let { byId[it]?.message },
                                    onReact = { emoji -> viewModel.react(item.message, emoji) },
                                    onReply = { viewModel.replyTo(item.message) },
                                    onEdit = { viewModel.startEdit(item.message) },
                                    onDelete = { viewModel.delete(item.message) },
                                    onRetry = { viewModel.retry(item.message) },
                                    onSave = { viewModel.toggleSaved(item.message) },
                                )
                            }
                        }
                    }
                }
                Composer(
                    peerName = name,
                    replyTo = state.replyTo,
                    editing = state.editing,
                    onCancelContext = {
                        viewModel.replyTo(null)
                        viewModel.startEdit(null)
                    },
                    onSend = viewModel::send,
                    onSticker = viewModel::sendSticker,
                    onDraw = { onDraw(conversation.peerId) },
                    onPhoto = { onSendPhoto(conversation.peerId) },
                    onAlarm = onAlarm?.let { alarm -> { alarm(conversation.peerId) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    peerName: String,
    quoted: ChatMessage?,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    var menu by rememberSaveable(message.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Column(
        horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            val sticker = stickerFor(message.sticker)
            // About four fifths of the screen, whatever the phone.
            val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp.coerceIn(220.dp, 420.dp)
            val bubbleModifier = Modifier
                .widthIn(max = maxBubble)
                .combinedClickable(
                    onClick = { if (message.status == ChatRepository.STATUS_FAILED) onRetry() },
                    onLongClick = { if (!message.deleted) menu = true },
                    onLongClickLabel = stringResource(R.string.chat_message_actions),
                )
            if (sticker != null && !message.deleted) {
                Sticker(sticker, modifier = bubbleModifier, size = 120.dp)
            } else {
                CrayonCard(
                    modifier = bubbleModifier,
                    fill = if (message.fromMe) crayon.blue else MaterialTheme.colorScheme.surface,
                    seed = message.id.hashCode(),
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (quoted != null) {
                        Text(
                            (if (quoted.fromMe) stringResource(R.string.chat_you) else peerName) + ": " +
                                previewText(quoted),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (message.deleted) {
                        Text(stringResource(R.string.chat_deleted), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    QUICK_REACTIONS.forEach { emoji ->
                        TextButton(onClick = {
                            menu = false
                            onReact(emoji)
                        }) { Text(emoji) }
                    }
                }
                DropdownMenuItem(
                    text = { Text(stringResource(if (message.saved) R.string.chat_unsave else R.string.chat_save)) },
                    onClick = {
                        menu = false
                        onSave()
                    },
                )
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_reply)) }, onClick = {
                    menu = false
                    onReply()
                })
                if (message.text != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_copy)) }, onClick = {
                        menu = false
                        clipboard.setText(AnnotatedString(message.text.orEmpty()))
                    })
                }
                if (message.fromMe && message.kind == "text") {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_edit)) }, onClick = {
                        menu = false
                        onEdit()
                    })
                }
                if (message.fromMe) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_delete)) }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                message.reactions.forEach { (emoji, users) ->
                    Text(if (users.size > 1) "$emoji ${users.size}" else emoji, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            metaText(message, peerName),
            style = MaterialTheme.typography.labelSmall,
            color = if (message.status == ChatRepository.STATUS_FAILED) MaterialTheme.colorScheme.error else crayon.textSoft,
        )
    }
}

@Composable
private fun previewText(message: ChatMessage): String = when {
    message.deleted -> stringResource(R.string.chat_deleted)
    message.sticker != null -> stringResource(R.string.chat_sticker)
    else -> message.text.orEmpty()
}

@Composable
private fun metaText(message: ChatMessage, peerName: String): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt))
    val edited = (if (message.edited) " · " + stringResource(R.string.chat_edited) else "") +
        (if (message.saved) " · " + stringResource(R.string.chat_saved) else "")
    if (!message.fromMe) return time + edited
    val status = when (message.status) {
        ChatRepository.STATUS_SENDING -> stringResource(R.string.chat_status_sending)
        ChatRepository.STATUS_WAITING -> stringResource(R.string.chat_status_waiting, peerName)
        ChatRepository.STATUS_SENT -> stringResource(R.string.chat_status_sent)
        ChatRepository.STATUS_DELIVERED -> stringResource(R.string.chat_status_delivered)
        ChatRepository.STATUS_READ -> stringResource(R.string.chat_status_read)
        ChatRepository.STATUS_FAILED -> stringResource(R.string.chat_status_failed)
        else -> ""
    }
    return "$time$edited · $status"
}

@Composable
private fun SystemRow(message: ChatMessage, peerName: String, onOpenSafety: () -> Unit) {
    val text = when (message.status) {
        ChatRepository.SYSTEM_SAFETY_CHANGED -> stringResource(R.string.chat_system_safety_changed, peerName)
        ChatRepository.SYSTEM_UNREADABLE -> stringResource(R.string.chat_system_unreadable, peerName)
        else -> return
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CrayonChip(
            text = text,
            fill = MeyouwwTheme.crayon.peach,
            onClick = if (message.status == ChatRepository.SYSTEM_SAFETY_CHANGED) onOpenSafety else null,
        )
    }
}

@Composable
private fun WallpaperRow(item: TimelineItem.Wallpaper, peerName: String) {
    val status = when (item.status) {
        "applied" -> stringResource(R.string.chat_wp_applied)
        "declined" -> stringResource(R.string.chat_wp_declined)
        "failed", "expired", "cancelled", "replaced" -> stringResource(R.string.chat_wp_not_applied)
        "scheduled", "held" -> stringResource(R.string.chat_wp_scheduled)
        else -> stringResource(R.string.chat_wp_on_its_way)
    }
    val text = if (item.fromMe) {
        stringResource(R.string.chat_wp_sent, peerName, status)
    } else {
        stringResource(R.string.chat_wp_received, peerName, status)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CrayonChip(text = text, fill = MeyouwwTheme.crayon.green)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    peerName: String,
    replyTo: ChatMessage?,
    editing: ChatMessage?,
    onCancelContext: () -> Unit,
    onSend: (String) -> Unit,
    onSticker: (String) -> Unit,
    onDraw: () -> Unit,
    onPhoto: () -> Unit,
    onAlarm: (() -> Unit)?,
) {
    val crayon = MeyouwwTheme.crayon
    var text by rememberSaveable { mutableStateOf("") }
    var tray by rememberSaveable { mutableStateOf(false) }
    var stickers by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editing?.id) { if (editing != null) text = editing.text.orEmpty() }

    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val context = when {
            editing != null -> stringResource(R.string.chat_editing)
            replyTo != null -> stringResource(R.string.chat_replying_to, if (replyTo.fromMe) stringResource(R.string.chat_you) else peerName) +
                ": " + previewText(replyTo)
            else -> null
        }
        if (context != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(context, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    onCancelContext()
                    text = ""
                }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel)) }
            }
        }
        if (tray) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CrayonButton(stringResource(R.string.chat_attach_doodle), onClick = {
                    tray = false
                    onDraw()
                }, kind = ButtonKind.Secondary)
                CrayonButton(stringResource(R.string.chat_attach_photo), onClick = {
                    tray = false
                    onPhoto()
                }, kind = ButtonKind.Secondary)
                if (onAlarm != null) {
                    CrayonButton(stringResource(R.string.chat_attach_alarm), onClick = {
                        tray = false
                        onAlarm()
                    }, kind = ButtonKind.Secondary)
                }
                CrayonButton(stringResource(R.string.chat_attach_sticker), onClick = {
                    tray = false
                    stickers = !stickers
                }, kind = ButtonKind.Secondary)
            }
            Text(stringResource(R.string.chat_attach_hint, peerName), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
        }
        if (stickers) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                WhitySticker.entries.forEach { sticker ->
                    Sticker(
                        sticker,
                        modifier = Modifier.clickable(onClickLabel = stringResource(sticker.label)) {
                            stickers = false
                            onSticker("whity:" + sticker.name)
                        },
                        size = 64.dp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { tray = !tray }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_attach))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 4000) text = it },
                placeholder = { Text(stringResource(R.string.chat_message_hint)) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
            )
            IconButton(
                onClick = {
                    val toSend = text.trim()
                    if (toSend.isNotEmpty()) {
                        onSend(toSend)
                        text = ""
                    }
                },
                enabled = text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

/** Shared with the list: "You: hi", "🐶 Sticker", … */
@Composable
internal fun conversationPreview(lastText: String?, lastKind: String?, lastFromMe: Boolean): String {
    val body = when (lastKind) {
        null -> stringResource(R.string.chat_list_empty_preview)
        "sticker" -> stringResource(R.string.chat_sticker)
        ChatRepository.KIND_OPENED -> return stringResource(R.string.chat_list_opened)
        else -> lastText.orEmpty()
    }
    return if (lastFromMe && lastKind != null) stringResource(R.string.chat_you) + ": " + body else body
}
