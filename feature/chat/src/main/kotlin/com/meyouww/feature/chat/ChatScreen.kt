package com.meyouww.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import com.meyouww.core.designsystem.component.FullImageDialog
import com.meyouww.core.designsystem.component.FullPhotoDialog
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.rememberFileImage
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
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
    /** Their profile: relationship (Best Friend, girlfriend / boyfriend) and settings. */
    onOpenFriend: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId, peerId) { viewModel.open(conversationId, peerId) }
    val peer by viewModel.peer.collectAsStateWithLifecycle()
    var showPhoto by rememberSaveable { mutableStateOf(false) }
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
    if (showPhoto) FullPhotoDialog(photo = peer?.avatar, caption = name, onDismiss = { showPhoto = false })

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
                // Tap the photo to see it full size.
                PersonAvatar(
                    name.take(1).uppercase(),
                    conversation.peerId.hashCode(),
                    size = 42.dp,
                    photo = peer?.avatar,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = stringResource(R.string.chat_view_photo), role = Role.Button) { showPhoto = true },
                )
            }
            // Tap the name for their profile: Best Friend, girlfriend / boyfriend, settings.
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
                    .clickable(enabled = conversation != null, onClickLabel = stringResource(R.string.chat_open_profile), role = Role.Button) {
                        conversation?.let { onOpenFriend(it.peerId) }
                    },
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    peer?.let { levelLine(it) } ?: stringResource(R.string.chat_tap_for_profile),
                    style = MaterialTheme.typography.labelSmall,
                    color = crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                        // A girlfriend / boyfriend can switch the other's phone sound.
                        if (peer?.levelValue == RelationshipLevel.Partner) {
                            listOf(
                                "ring" to R.string.chat_ringer_ring,
                                "vibrate" to R.string.chat_ringer_vibrate,
                                "mute" to R.string.chat_ringer_mute,
                            ).forEach { (mode, label) ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(label, name)) },
                                    onClick = {
                                        menu = false
                                        viewModel.setRinger(mode)
                                    },
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(if (conversation.muted) R.string.chat_unmute else R.string.chat_mute)) },
                            onClick = {
                                menu = false
                                viewModel.setMuted(!conversation.muted)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_open_profile)) },
                            onClick = {
                                menu = false
                                onOpenFriend(conversation.peerId)
                            },
                        )
                    }
                }
            }
        }

        state.note?.let { note ->
            CrayonChip(
                text = stringResource(note, name),
                fill = crayon.yellow,
                onClick = viewModel::noteShown,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
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
                    fill = if (message.fromMe) crayon.yellow else MaterialTheme.colorScheme.surface,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(metaText(message), style = MaterialTheme.typography.labelSmall, color = crayon.textSoft)
            if (message.fromMe) {
                val (light, label) = deliveryOf(message, peerName)
                DeliveryLight(light, label, error = message.status == ChatRepository.STATUS_FAILED)
            }
        }
    }
}

/** Traffic-light ticks: red = not delivered yet (their phone is offline), yellow = delivered, green = read. */
private enum class Light { Red, Yellow, Green, Grey }

@Composable
private fun deliveryOf(message: ChatMessage, peerName: String): Pair<Light, String> = when (message.status) {
    ChatRepository.STATUS_SENDING -> Light.Red to stringResource(R.string.chat_status_sending)
    ChatRepository.STATUS_WAITING -> Light.Red to stringResource(R.string.chat_status_waiting, peerName)
    ChatRepository.STATUS_SENT -> Light.Red to stringResource(R.string.chat_status_sent)
    ChatRepository.STATUS_DELIVERED -> Light.Yellow to stringResource(R.string.chat_status_delivered)
    ChatRepository.STATUS_READ -> Light.Green to stringResource(R.string.chat_status_read)
    ChatRepository.STATUS_FAILED -> Light.Red to stringResource(R.string.chat_status_failed)
    else -> Light.Grey to ""
}

/**
 * A tiny traffic light: three lamps in a dark housing, the current one lit.
 * Red = not delivered yet, yellow = delivered, green = read. Grey = didn't go through.
 */
@Composable
private fun DeliveryLight(light: Light, label: String, error: Boolean = false) {
    val crayon = MeyouwwTheme.crayon
    val lamps = listOf(Light.Red to Color(0xFFE23D2D), Light.Yellow to Color(0xFFFFC72C), Light.Green to Color(0xFF3DB24A))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF231B1E))
                .border(1.dp, crayon.outline, RoundedCornerShape(6.dp))
                .padding(horizontal = 3.dp, vertical = 2.dp),
        ) {
            lamps.forEach { (which, color) ->
                val on = which == light
                Box(
                    Modifier
                        .size(if (on) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (on) color else color.copy(alpha = 0.22f)),
                )
            }
        }
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else crayon.textSoft)
        }
    }
}

/** "Best Friend 💛 · tap for profile". */
@Composable
private fun levelLine(friend: Friend): String {
    val level = when (friend.levelValue) {
        RelationshipLevel.Friend -> stringResource(R.string.chat_level_friend)
        RelationshipLevel.BestFriend -> stringResource(R.string.chat_level_bestie)
        RelationshipLevel.Partner -> stringResource(R.string.chat_level_partner, friend.label?.ifBlank { null } ?: stringResource(R.string.chat_level_partner_default))
    }
    return stringResource(R.string.chat_level_line, level)
}

@Composable
private fun previewText(message: ChatMessage): String = when {
    message.deleted -> stringResource(R.string.chat_deleted)
    message.sticker != null -> stringResource(R.string.chat_sticker)
    else -> message.text.orEmpty()
}

@Composable
private fun metaText(message: ChatMessage): String {
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt))
    return time + (if (message.edited) " · " + stringResource(R.string.chat_edited) else "") +
        (if (message.saved) " · " + stringResource(R.string.chat_saved) else "")
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

/** A wallpaper in the chat: the picture itself (kept on this phone), where it went, and its light. */
@Composable
private fun WallpaperRow(item: TimelineItem.Wallpaper, peerName: String) {
    val crayon = MeyouwwTheme.crayon
    var full by rememberSaveable(item.id) { mutableStateOf(false) }
    val thumb = rememberFileImage(item.file, maxPx = 720)
    val screen = stringResource(
        when (item.target) {
            WallpaperTarget.Home -> R.string.chat_wp_home
            WallpaperTarget.Lock -> R.string.chat_wp_lock
            WallpaperTarget.Both -> R.string.chat_wp_both
        },
    )
    val title = if (item.fromMe) stringResource(R.string.chat_wp_sent_title, screen) else stringResource(R.string.chat_wp_received_title, peerName, screen)
    val (light, label) = when (item.status) {
        "applied" -> Light.Green to stringResource(if (item.fromMe) R.string.chat_wp_applied_them else R.string.chat_wp_applied_me)
        "delivered", "awaiting_approval", "applying" -> Light.Yellow to stringResource(R.string.chat_wp_delivered)
        "declined" -> Light.Grey to stringResource(R.string.chat_wp_declined)
        "failed", "expired", "cancelled", "replaced" -> Light.Grey to stringResource(R.string.chat_wp_not_applied)
        "scheduled", "held" -> Light.Red to stringResource(R.string.chat_wp_scheduled)
        else -> Light.Red to stringResource(R.string.chat_wp_on_its_way)
    }
    if (full && thumb != null) FullImageDialog(thumb, title, onDismiss = { full = false })
    Column(horizontalAlignment = if (item.fromMe) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        CrayonCard(
            modifier = Modifier.widthIn(max = 240.dp),
            fill = if (item.fromMe) crayon.yellow else MaterialTheme.colorScheme.surface,
            seed = item.id.hashCode(),
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(10.dp),
            onClick = if (thumb != null) ({ full = true }) else null,
            onClickLabel = stringResource(R.string.chat_wp_view),
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(thumb.width.toFloat() / thumb.height.coerceAtLeast(1))
                        .heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, crayon.outline, RoundedCornerShape(12.dp)),
                )
            } else {
                Text("🎨", style = MaterialTheme.typography.displaySmall)
            }
            Text(title, style = MaterialTheme.typography.bodyMedium)
            DeliveryLight(light, label)
        }
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
