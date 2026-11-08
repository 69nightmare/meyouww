package com.meyouww.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.FullImageDialog
import com.meyouww.core.designsystem.component.FullPhotoDialog
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.rememberFileImage
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTextStyles
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
import java.text.DateFormat
import java.util.Calendar
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
    var showSheet by rememberSaveable { mutableStateOf(false) }
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
    if (showSheet && conversation != null) {
        PeerSheet(
            peer = peer,
            peerId = conversation.peerId,
            name = name,
            muted = conversation.muted,
            busy = state.busy,
            onDismiss = { showSheet = false },
            onPhoto = { showPhoto = true },
            onInvite = viewModel::invite,
            onAnswerInvite = viewModel::answerInvite,
            onCancelInvite = viewModel::cancelInvite,
            onRinger = {
                showSheet = false
                viewModel.setRinger(it)
            },
            onToggleMute = { viewModel.setMuted(!conversation.muted) },
            onOpenProfile = {
                showSheet = false
                onOpenFriend(conversation.peerId)
            },
            onOpenSafety = {
                showSheet = false
                onOpenSafety(conversation.peerId)
            },
        )
    }

    val mood = chatMood(peer?.levelValue)
    Column(
        Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        ChatHeader(
            name = name,
            peer = peer,
            peerId = conversation?.peerId,
            mood = mood,
            muted = conversation?.muted == true,
            onBack = onBack,
            onPhoto = { showPhoto = true },
            onSheet = {
                viewModel.refreshPeer()
                showSheet = true
            },
            onSafety = { conversation?.let { onOpenSafety(it.peerId) } },
            onRinger = viewModel::setRinger,
            onToggleMute = { conversation?.let { viewModel.setMuted(!it.muted) } },
            onOpenProfile = { conversation?.let { onOpenFriend(it.peerId) } },
        )

        AnimatedVisibility(
            visible = state.note != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val note = state.note
            if (note != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                    CrayonChip(
                        text = stringResource(note, name),
                        fill = crayon.yellow,
                        onClick = viewModel::noteShown,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
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
                ChatTimeline(
                    timeline = timeline,
                    name = name,
                    mood = mood,
                    onOpenSafety = { onOpenSafety(conversation.peerId) },
                    onReact = viewModel::react,
                    onReply = viewModel::replyTo,
                    onEdit = viewModel::startEdit,
                    onDelete = viewModel::delete,
                    onRetry = viewModel::retry,
                    onSave = viewModel::toggleSaved,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Composer(
                    peerName = name,
                    mood = mood,
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

/** The messages and wallpapers, grouped into runs with a day heading for each day. */
@Composable
internal fun ChatTimeline(
    timeline: List<TimelineItem>,
    name: String,
    mood: ChatMood,
    onOpenSafety: () -> Unit,
    onReact: (ChatMessage, String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onEdit: (ChatMessage) -> Unit,
    onDelete: (ChatMessage) -> Unit,
    onRetry: (ChatMessage) -> Unit,
    onSave: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val crayon = MeyouwwTheme.crayon
    val rows = remember(timeline) { chatRows(timeline) }
    val lastMine = remember(timeline) {
        timeline.lastOrNull { it is TimelineItem.Message && it.message.fromMe && it.message.kind != ChatRepository.KIND_SYSTEM }?.key
    }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.size) }
    val byId = remember(timeline) { timeline.filterIsInstance<TimelineItem.Message>().associateBy { it.message.id } }
    // About four fifths of the screen, whatever the phone.
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.78f).dp.coerceIn(220.dp, 440.dp)
    val reducedMotion = LocalReducedMotion.current
    LazyColumn(
        state = listState,
        modifier = modifier.chatDoodles(mood, crayon.outline),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        item(key = "e2ee", contentType = "notice") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                CrayonChip(
                    text = stringResource(R.string.chat_e2ee_notice, name),
                    fill = crayon.yellow,
                    onClick = onOpenSafety,
                )
                Text(
                    stringResource(R.string.chat_disappearing_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = crayon.textSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
        items(rows, key = { it.key }, contentType = { it.contentType }) { row ->
            val motion = if (reducedMotion) {
                Modifier
            } else {
                Modifier.animateItem(fadeInSpec = tween(220), placementSpec = spring(stiffness = Spring.StiffnessMediumLow), fadeOutSpec = tween(160))
            }
            Box(motion.padding(top = row.gapAbove)) {
                when (row) {
                    is ChatRow.Day -> DayChip(row.at)
                    is ChatRow.Item -> when (val item = row.item) {
                        is TimelineItem.Wallpaper -> WallpaperRow(item, name, mood)
                        is TimelineItem.Message -> if (item.message.kind == ChatRepository.KIND_SYSTEM) {
                            SystemRow(item.message, name, onOpenSafety = onOpenSafety)
                        } else {
                            MessageRow(
                                message = item.message,
                                peerName = name,
                                quoted = item.message.replyTo?.let { byId[it]?.message },
                                mood = mood,
                                tail = row.tail,
                                showStatus = item.key == lastMine,
                                maxBubble = maxBubble,
                                onReact = { emoji -> onReact(item.message, emoji) },
                                onReply = { onReply(item.message) },
                                onEdit = { onEdit(item.message) },
                                onDelete = { onDelete(item.message) },
                                onRetry = { onRetry(item.message) },
                                onSave = { onSave(item.message) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One line in the chat: a day heading, or a message / wallpaper with its place in a run. */
private sealed interface ChatRow {
    val key: String
    val contentType: String
    val gapAbove: Dp

    data class Day(val at: Long, val day: Int) : ChatRow {
        override val key: String get() = "d:$day"
        override val contentType: String get() = "day"
        override val gapAbove: Dp get() = 14.dp
    }

    /** [tail] is true for the last bubble of a run from the same person. */
    data class Item(val item: TimelineItem, val tail: Boolean, val first: Boolean) : ChatRow {
        override val key: String get() = item.key
        override val contentType: String get() = when (item) {
            is TimelineItem.Wallpaper -> "wallpaper"
            is TimelineItem.Message -> if (item.message.sticker != null) "sticker" else "message"
        }
        override val gapAbove: Dp get() = if (first) 10.dp else 3.dp
    }
}

private fun dayOf(at: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = at }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
}

/** Who a line is from, for grouping: true = me, false = them, null = the app. */
private fun TimelineItem.sender(): Boolean? = when (this) {
    is TimelineItem.Wallpaper -> fromMe
    is TimelineItem.Message -> if (message.kind == ChatRepository.KIND_SYSTEM) null else message.fromMe
}

private fun chatRows(timeline: List<TimelineItem>): List<ChatRow> {
    val rows = ArrayList<ChatRow>(timeline.size + 4)
    var lastDay = -1
    timeline.forEachIndexed { i, item ->
        val day = dayOf(item.at)
        val newDay = day != lastDay
        if (newDay) rows += ChatRow.Day(item.at, day)
        lastDay = day
        val previous = timeline.getOrNull(i - 1)
        val next = timeline.getOrNull(i + 1)
        val who = item.sender()
        val first = newDay || previous == null || previous.sender() != who || who == null
        val tail = next == null || next.sender() != who || dayOf(next.at) != day || who == null
        rows += ChatRow.Item(item, tail = tail, first = first)
    }
    return rows
}

/** "Today", "Yesterday" or the date, on a strip of tape. */
@Composable
private fun DayChip(at: Long) {
    val label = when (dayOf(at)) {
        dayOf(System.currentTimeMillis()) -> stringResource(R.string.chat_day_today)
        dayOf(System.currentTimeMillis() - DAY_MS) -> stringResource(R.string.chat_day_yesterday)
        else -> remember(at) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at)) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TapeLabel(
            label,
            tape = Color(0xFFBFE6FF),
            tilt = -1f,
            style = MaterialTheme.typography.labelLarge,
            isHeading = false,
        )
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/** The top of the chat: back, their photo in a ring of your level's colour, name and level, menu. */
@Composable
internal fun ChatHeader(
    name: String,
    peer: Friend?,
    peerId: String?,
    mood: ChatMood,
    muted: Boolean,
    onBack: () -> Unit,
    onPhoto: () -> Unit,
    onSheet: () -> Unit,
    onSafety: () -> Unit,
    onRinger: (String) -> Unit,
    onToggleMute: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            .crayonSurface(
                fill = MaterialTheme.colorScheme.surface,
                outline = crayon.outline,
                seed = 4217,
                cornerRadius = 26.dp,
                strokeWidth = 2.5.dp,
                shadow = crayon.hardShadow,
                shadowOffsetX = 3.dp,
                shadowOffsetY = 4.dp,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
        }
        if (peerId != null) {
            // Tap the photo to see it full size.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(mood.accent)
                    .border(2.dp, crayon.outline, CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.chat_view_photo), role = Role.Button, onClick = onPhoto),
            ) {
                PersonAvatar(name.take(1).uppercase(), peerId.hashCode(), size = 40.dp, photo = peer?.avatar)
            }
        }
        // Tap the name: Best Friend / girlfriend / boyfriend, their phone's sound, mute, profile.
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = peerId != null, onClickLabel = stringResource(R.string.chat_open_menu), role = Role.Button, onClick = onSheet)
                .padding(start = 10.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    peer?.let { levelName(it) } ?: stringResource(R.string.chat_friend),
                    style = MaterialTheme.typography.labelMedium,
                    color = crayon.onFill,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(mood.accent)
                        .border(1.5.dp, crayon.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Text(
                    stringResource(if (muted) R.string.chat_header_muted else R.string.chat_header_tap),
                    style = MaterialTheme.typography.labelSmall,
                    color = crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (peerId != null) {
            IconButton(onClick = onSafety) {
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
                                    onRinger(mode)
                                },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute)) },
                        onClick = {
                            menu = false
                            onToggleMute()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_open_profile)) },
                        onClick = {
                            menu = false
                            onOpenProfile()
                        },
                    )
                }
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
    mood: ChatMood,
    tail: Boolean,
    showStatus: Boolean,
    maxBubble: Dp,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val grain = LocalCrayonGrain.current
    var menu by rememberSaveable(message.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val mine = message.fromMe
    val fill = if (mine) mood.accent else mood.theirs
    val ink = if (mine) crayon.onFill else mood.onTheirs
    // A gentle squish while pressed instead of a grey ripple.
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    Column(
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            val sticker = stickerFor(message.sticker)
            val click = Modifier.combinedClickable(
                interactionSource = press,
                indication = null,
                onClick = { if (message.status == ChatRepository.STATUS_FAILED) onRetry() },
                onLongClick = { if (!message.deleted) menu = true },
                onLongClickLabel = stringResource(R.string.chat_message_actions),
            )
            if (sticker != null && !message.deleted) {
                Column(
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            rotationZ = if (mine) 4f else -4f
                        }
                        .then(click)
                        .padding(horizontal = TAIL_WIDTH),
                ) {
                    Sticker(sticker, size = 120.dp)
                    MetaLine(message, peerName, showStatus, crayon.textSoft)
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = maxBubble)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .speechBubble(
                            fill = fill,
                            outline = crayon.outline,
                            shadow = crayon.hardShadow,
                            start = !mine,
                            tail = tail,
                            seed = message.id.hashCode(),
                            grain = grain,
                        )
                        .then(click)
                        .padding(
                            start = if (mine) 14.dp else TAIL_WIDTH + 14.dp,
                            end = if (mine) TAIL_WIDTH + 14.dp else 14.dp,
                            top = 9.dp,
                            bottom = 7.dp,
                        ),
                ) {
                    if (quoted != null) {
                        Row(
                            Modifier
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ink.copy(alpha = 0.08f))
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(Modifier.width(4.dp).fillMaxHeight().background(if (mine) crayon.outline else mood.accent))
                            Text(
                                (if (quoted.fromMe) stringResource(R.string.chat_you) else peerName) + ": " + previewText(quoted),
                                style = MaterialTheme.typography.bodySmall,
                                color = ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    if (message.deleted) {
                        Text(stringResource(R.string.chat_deleted), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium, color = ink)
                    } else {
                        Text(message.text.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = ink)
                    }
                    MetaLine(message, peerName, showStatus, ink.copy(alpha = 0.78f), Modifier.align(Alignment.End))
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
            // Little paper pill tucked under the bubble's corner.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(horizontal = TAIL_WIDTH + 10.dp)
                    .offset(y = (-4).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, crayon.outline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 7.dp, vertical = 1.dp),
            ) {
                message.reactions.forEach { (emoji, users) ->
                    Text(if (users.size > 1) "$emoji ${users.size}" else emoji, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Time, "edited", "saved", and (for your messages) the traffic light; its words only on your latest one. */
@Composable
private fun MetaLine(message: ChatMessage, peerName: String, showStatus: Boolean, color: Color, modifier: Modifier = Modifier) {
    val failed = message.status == ChatRepository.STATUS_FAILED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.padding(top = 2.dp),
    ) {
        Text(metaText(message), style = MaterialTheme.typography.labelSmall, color = color)
        if (message.fromMe) {
            val (light, label) = deliveryOf(message, peerName)
            DeliveryLight(light, if (showStatus || failed) label else "", color = color, error = failed)
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
private fun DeliveryLight(light: Light, label: String, color: Color = MeyouwwTheme.crayon.textSoft, error: Boolean = false) {
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
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (error) MaterialTheme.colorScheme.error else color)
        }
    }
}

/** "Friend", "Best Friend 💛" or "Girlfriend 💗". */
@Composable
private fun levelName(friend: Friend): String = when (friend.levelValue) {
    RelationshipLevel.Friend -> stringResource(R.string.chat_level_friend)
    RelationshipLevel.BestFriend -> stringResource(R.string.chat_level_bestie)
    RelationshipLevel.Partner -> stringResource(R.string.chat_level_partner, friend.label?.ifBlank { null } ?: stringResource(R.string.chat_level_partner_default))
}

@Composable
private fun previewText(message: ChatMessage): String = when {
    message.deleted -> stringResource(R.string.chat_deleted)
    message.sticker != null -> stringResource(R.string.chat_sticker)
    else -> message.text.orEmpty()
}

@Composable
private fun metaText(message: ChatMessage): String {
    val time = remember(message.sentAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)) }
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

/**
 * A wallpaper in the chat, like a polaroid taped into a scrapbook: the picture itself
 * (kept on this phone), which screen it went on, and its traffic light.
 */
@Composable
private fun WallpaperRow(item: TimelineItem.Wallpaper, peerName: String, mood: ChatMood) {
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
        Box(Modifier.padding(horizontal = TAIL_WIDTH + 4.dp).graphicsLayer { rotationZ = if (item.fromMe) 2f else -2f }) {
            CrayonCard(
                modifier = Modifier.width(212.dp).padding(top = 10.dp),
                fill = if (crayon.isDark) mood.theirs else Color.White,
                seed = item.id.hashCode(),
                cornerRadius = 10.dp,
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                onClick = if (thumb != null) ({ full = true }) else null,
                onClickLabel = stringResource(R.string.chat_wp_view),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (thumb != null) (thumb.width.toFloat() / thumb.height.coerceAtLeast(1)).coerceIn(0.45f, 1.4f) else 0.75f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(mood.accent.copy(alpha = 0.55f))
                        .border(2.dp, crayon.outline, RoundedCornerShape(6.dp)),
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("🎨", style = MaterialTheme.typography.displaySmall)
                    }
                }
                Text(title, style = MeyouwwTextStyles.handwritten.copy(fontSize = 19.sp, lineHeight = 22.sp), color = if (crayon.isDark) mood.onTheirs else Color(Palette.Ink))
                DeliveryLight(light, label, color = if (crayon.isDark) mood.onTheirs else Color(Palette.InkSoft))
            }
            // A strip of tape holding the photo in.
            TapeLabel(
                "🖼",
                tape = mood.accent,
                tilt = if (item.fromMe) -6f else 6f,
                style = MaterialTheme.typography.labelLarge,
                isHeading = false,
                modifier = Modifier.align(Alignment.TopCenter).clearAndSetSemantics { },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Composer(
    peerName: String,
    mood: ChatMood,
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

    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val context = when {
            editing != null -> stringResource(R.string.chat_editing)
            replyTo != null -> stringResource(R.string.chat_replying_to, if (replyTo.fromMe) stringResource(R.string.chat_you) else peerName) +
                ": " + previewText(replyTo)
            else -> null
        }
        AnimatedVisibility(visible = context != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, crayon.outline, RoundedCornerShape(14.dp))
                    .height(IntrinsicSize.Min),
            ) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(mood.accent))
                Text(
                    context.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                IconButton(onClick = {
                    onCancelContext()
                    text = ""
                }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel)) }
            }
        }
        AnimatedVisibility(visible = tray, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CrayonChip(stringResource(R.string.chat_attach_doodle), fill = crayon.lilac, onClick = {
                        tray = false
                        onDraw()
                    })
                    CrayonChip(stringResource(R.string.chat_attach_photo), fill = crayon.blue, onClick = {
                        tray = false
                        onPhoto()
                    })
                    if (onAlarm != null) {
                        CrayonChip(stringResource(R.string.chat_attach_alarm), fill = crayon.tangerine, onClick = {
                            tray = false
                            onAlarm()
                        })
                    }
                    CrayonChip(stringResource(R.string.chat_attach_sticker), fill = crayon.bubblegum, onClick = {
                        tray = false
                        stickers = !stickers
                    })
                }
                Text(stringResource(R.string.chat_attach_hint, peerName), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
            }
        }
        AnimatedVisibility(visible = stickers, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .crayonSurface(
                        fill = MaterialTheme.colorScheme.surface,
                        outline = crayon.outline,
                        seed = 77,
                        cornerRadius = 20.dp,
                        shadow = crayon.hardShadow,
                        shadowOffsetX = 2.dp,
                        shadowOffsetY = 3.dp,
                    )
                    .padding(10.dp),
            ) {
                WhitySticker.entries.forEach { sticker ->
                    Sticker(
                        sticker,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = stringResource(sticker.label)) {
                                stickers = false
                                onSticker("whity:" + sticker.name)
                            },
                        size = 64.dp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val turn by animateFloatAsState(if (tray) 45f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "plus")
            RoundButton(
                fill = crayon.yellow,
                label = stringResource(R.string.chat_attach),
                onClick = { tray = !tray },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = crayon.onFill, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .crayonSurface(
                        fill = MaterialTheme.colorScheme.surface,
                        outline = crayon.outline,
                        seed = 31,
                        cornerRadius = 26.dp,
                        strokeWidth = 2.5.dp,
                        shadow = crayon.hardShadow,
                        shadowOffsetX = 2.dp,
                        shadowOffsetY = 3.dp,
                    )
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                val hint = stringResource(R.string.chat_message_hint)
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 4000) text = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft)
                            inner()
                        }
                    },
                )
            }
            val ready = text.isNotBlank()
            val pop by animateFloatAsState(if (ready) 1f else 0.86f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "send")
            RoundButton(
                fill = if (ready) crayon.red else crayon.red.copy(alpha = 0.45f),
                label = stringResource(R.string.chat_send),
                enabled = ready,
                modifier = Modifier.graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
                onClick = {
                    val toSend = text.trim()
                    if (toSend.isNotEmpty()) {
                        onSend(toSend)
                        text = ""
                    }
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** A round crayon button with a comic shadow (attach, send). */
@Composable
private fun RoundButton(
    fill: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .crayonSurface(
                fill = fill,
                outline = crayon.outline,
                seed = label.hashCode(),
                cornerRadius = 26.dp,
                strokeWidth = 2.5.dp,
                shadow = if (enabled) crayon.hardShadow else null,
                shadowOffsetX = 2.dp,
                shadowOffsetY = 3.dp,
            )
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) { content() }
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
