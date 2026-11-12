package com.meyouww.feature.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meyouww.core.chat.ChatAttachment
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.MediaKind
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.presence.Presence
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.FullImageDialog
import com.meyouww.core.designsystem.component.FullPhotoDialog
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhityPacks
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.rememberFileImage
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.pet.WhityBuddy
import com.meyouww.core.designsystem.pet.WhityCue
import com.meyouww.core.designsystem.pet.WhityEarn
import com.meyouww.core.designsystem.pet.WhityPetStore
import com.meyouww.core.designsystem.pet.WhityReaction
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTextStyles
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.WallpaperTarget
import java.io.File
import java.text.DateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    onReminder: ((String) -> Unit)? = null,
    /** Their profile: relationship (Best Friend, girlfriend / boyfriend) and settings. */
    onOpenFriend: (String) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(conversationId, peerId) { viewModel.open(conversationId, peerId) }
    val peer by viewModel.peer.collectAsStateWithLifecycle()
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val disappearing by viewModel.disappearing.collectAsStateWithLifecycle()
    val peerPresence by viewModel.peerPresence.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadedTimeline by viewModel.timeline.collectAsStateWithLifecycle()
    val timeline = loadedTimeline.orEmpty()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val myId by viewModel.myId.collectAsStateWithLifecycle()
    val friendList by viewModel.friendList.collectAsStateWithLifecycle()
    var showPoll by rememberSaveable { mutableStateOf(false) }
    var showGroupReminder by rememberSaveable { mutableStateOf(false) }
    var showRename by rememberSaveable { mutableStateOf(false) }
    var showAddPeople by rememberSaveable { mutableStateOf(false) }
    var showNickname by rememberSaveable { mutableStateOf(false) }
    // A voice message stops playing when the chat closes.
    DisposableEffect(Unit) { onDispose { VoicePlayer.stop() } }
    // Search inside this chat: the words, and which match is shown (newest first).
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hit by rememberSaveable { mutableIntStateOf(0) }
    val matches = remember(timeline, query) {
        val term = query.trim()
        if (term.isEmpty()) {
            emptyList()
        } else {
            timeline.filterIsInstance<TimelineItem.Message>()
                .filter { !it.message.deleted && it.message.text?.contains(term, ignoreCase = true) == true }
                .map { it.key }
        }
    }
    LaunchedEffect(matches.size, query) { hit = (matches.size - 1).coerceAtLeast(0) }
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
    val isGroup = conversation?.isGroup == true
    val name = conversation?.peerName?.ifBlank { null } ?: stringResource(if (isGroup) R.string.group_default_name else R.string.chat_friend)
    val amAdmin = members.any { it.userId == myId && it.admin }
    if (showPhoto) FullPhotoDialog(photo = peer?.avatar, caption = name, onDismiss = { showPhoto = false })
    if (showSheet && conversation != null && isGroup) {
        GroupSheet(
            name = name,
            groupId = conversation.id,
            members = members,
            myId = myId,
            muted = conversation.muted,
            onDismiss = { showSheet = false },
            onRename = { showRename = true },
            onAddPeople = if (amAdmin) ({ showAddPeople = true }) else null,
            onRemove = { viewModel.removeMember(it.userId) },
            onLeave = {
                showSheet = false
                viewModel.leaveGroup(onLeft = onBack)
            },
            onToggleMute = { viewModel.setMuted(!conversation.muted) },
            onOpenGallery = {
                showSheet = false
                showGallery = true
            },
        )
    } else if (showSheet && conversation != null) {
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
            onToggleMute = { viewModel.setMuted(!conversation.muted) },
            onOpenProfile = {
                showSheet = false
                onOpenFriend(conversation.peerId)
            },
            onOpenSafety = {
                showSheet = false
                onOpenSafety(conversation.peerId)
            },
            onOpenGallery = {
                showSheet = false
                showGallery = true
            },
            nickname = conversation.nickname,
            onNickname = { showNickname = true },
        )
    }
    if (showNickname && conversation != null && !isGroup) {
        NicknameDialog(
            current = conversation.nickname.orEmpty(),
            theirName = peer?.card?.name ?: name,
            onDismiss = { showNickname = false },
            onSave = {
                showNickname = false
                viewModel.setNickname(it)
            },
        )
    }
    if (showGallery) ChatGalleryDialog(timeline, name, onDismiss = { showGallery = false })
    if (showRename && isGroup) {
        RenameGroupDialog(name, onDismiss = { showRename = false }, onSave = {
            showRename = false
            viewModel.renameGroup(it)
        })
    }
    if (showAddPeople && isGroup) {
        val inGroup = remember(members) { members.mapTo(HashSet()) { it.userId } }
        PickFriendsDialog(
            title = stringResource(R.string.group_add_people),
            friends = friendList.orEmpty().filter { it.id !in inGroup },
            confirm = stringResource(R.string.group_add_confirm),
            askName = false,
            busy = state.busy,
            error = null,
            maxPick = (GROUP_MAX - members.size).coerceAtLeast(0),
            onDismiss = { showAddPeople = false },
            onConfirm = { _, ids ->
                showAddPeople = false
                viewModel.addMembers(ids)
            },
        )
    }
    if (showPoll) {
        PollDialog(onDismiss = { showPoll = false }, onSend = { question, options, multi ->
            showPoll = false
            viewModel.sendPoll(question, options, multi)
        })
    }
    if (showGroupReminder) {
        GroupReminderDialog(onDismiss = { showGroupReminder = false }, onSend = { what, start ->
            showGroupReminder = false
            viewModel.sendGroupReminder(what, start)
        })
    }

    val mood = chatMood(peer?.levelValue)
    // Photos and videos come from the system picker (no storage permission needed); PDFs from Files.
    val pickerContext = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.sendMedia(pickerContext, uri)
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.sendMedia(pickerContext, uri)
    }
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
            onPhoto = { if (isGroup) showSheet = true else showPhoto = true },
            onSheet = {
                viewModel.refreshPeer()
                showSheet = true
            },
            onSafety = { conversation?.let { onOpenSafety(it.peerId) } },
            onToggleMute = { conversation?.let { viewModel.setMuted(!it.muted) } },
            onOpenProfile = { conversation?.let { onOpenFriend(it.peerId) } },
            onClearChat = { confirmClear = true },
            status = if (isGroup) null else presenceText(peerPresence),
            groupSubtitle = if (isGroup) pluralStringResource(R.plurals.group_people, members.size, members.size) else null,
            pinned = conversation?.pinned == true,
            onTogglePin = viewModel::togglePin,
            onSearch = { searching = true },
        )
        if (searching) {
            ChatSearchBar(
                query = query,
                onQuery = { query = it },
                position = if (matches.isEmpty()) 0 else hit + 1,
                total = matches.size,
                onOlder = { if (hit > 0) hit-- },
                onNewer = { if (hit < matches.size - 1) hit++ },
                onClose = {
                    searching = false
                    query = ""
                },
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.chat_clear_title)) },
                text = { Text(stringResource(R.string.chat_clear_body, name)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        viewModel.clearChat()
                    }) { Text(stringResource(R.string.chat_clear_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.chat_cancel)) }
                },
            )
        }

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
            // Messages still being read from the phone: wait, so the chat opens at the newest one
            // instead of showing the oldest first and then scrolling down.
            loadedTimeline == null -> Box(Modifier.weight(1f).fillMaxWidth())
            else -> {
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                        disappearing = disappearing,
                        peerTyping = peerPresence?.typing == true,
                        group = isGroup,
                        senderNames = remember(members) { members.associate { it.userId to it.name } },
                        myId = myId,
                        onVote = viewModel::vote,
                        onStar = viewModel::toggleStar,
                        highlightKey = if (searching) matches.getOrNull(hit) else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // A tiny Whity peeks in while they have this chat open (and aren't typing). She
                    // watches the chat, hops when you send something and gets heart eyes when they do.
                    val here = peerPresence?.let { it.inChat && !it.typing } == true
                    val peekLabel = stringResource(R.string.chat_peer_here, name)
                    val newest = remember(timeline) { timeline.maxByOrNull { it.at } }
                    var whityCue by remember { mutableStateOf<WhityCue?>(null) }
                    var lastNewest by remember { mutableStateOf<String?>(null) }
                    val whityContext = LocalContext.current
                    LaunchedEffect(newest?.key) {
                        val item = newest ?: return@LaunchedEffect
                        val before = lastNewest
                        lastNewest = item.key
                        if (before == null) return@LaunchedEffect // the chat just opened
                        val mine = when (item) {
                            is TimelineItem.Message -> item.message.fromMe
                            is TimelineItem.Wallpaper -> item.fromMe
                        }
                        whityCue = WhityCue(if (mine) WhityReaction.Tap else WhityReaction.Love)
                        // Chatting earns her a treat (one a day); she says thanks later.
                        if (mine) WhityPetStore.earn(whityContext, WhityEarn.Message)
                    }
                    // The plain version: the Column around this Box would otherwise claim it.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = here,
                        enter = if (LocalReducedMotion.current) fadeIn() else fadeIn() + slideInVertically { it },
                        exit = if (LocalReducedMotion.current) fadeOut() else fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp),
                    ) {
                        WhityBuddy(width = 72.dp, cue = whityCue, description = peekLabel)
                    }
                }
                Composer(
                    onMedia = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onPdf = { pickPdf.launch(arrayOf("application/pdf", MediaKind.DOCX, MediaKind.DOC)) },
                    onTyping = viewModel::onTyping,
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
                    // Doodles, wallpapers, alarms and phone sounds are for one person; a group gets polls and reminders.
                    onDraw = if (isGroup) null else ({ onDraw(conversation.peerId) }),
                    onPhoto = if (isGroup) null else ({ onSendPhoto(conversation.peerId) }),
                    onAlarm = if (isGroup) null else onAlarm?.let { alarm -> { alarm(conversation.peerId) } },
                    onReminder = if (isGroup) null else onReminder?.let { reminder -> { reminder(conversation.peerId) } },
                    onRinger = if (!isGroup && peer?.levelValue == RelationshipLevel.Partner) viewModel::setRinger else null,
                    onVoice = viewModel::sendVoice,
                    onPoll = if (isGroup) ({ showPoll = true }) else null,
                    onGroupReminder = if (isGroup) ({ showGroupReminder = true }) else null,
                    showSound = !isGroup,
                )
            }
        }
    }
}

/** Search inside a chat: the words, "2 of 5", older / newer, close. */
@Composable
private fun ChatSearchBar(
    query: String,
    onQuery: (String) -> Unit,
    position: Int,
    total: Int,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onClose: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 2.dp),
    ) {
        CrayonTextField(
            value = query,
            onValueChange = onQuery,
            label = stringResource(R.string.chat_search_hint),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (query.isBlank()) "" else if (total == 0) stringResource(R.string.chat_search_none) else stringResource(R.string.chat_search_count, position, total),
            style = MaterialTheme.typography.labelMedium,
            color = crayon.textSoft,
            modifier = Modifier.padding(start = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onOlder, enabled = position > 1) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.chat_search_older))
        }
        IconButton(onClick = onNewer, enabled = position in 1 until total) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.chat_search_newer))
        }
        IconButton(onClick = onClose) {
            Icon(PastelIcons.Close, contentDescription = stringResource(R.string.chat_search_close))
        }
    }
}

/** My own name for a friend, only on this phone. */
@Composable
private fun NicknameDialog(current: String, theirName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_nickname_title, theirName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.chat_nickname_body), style = MaterialTheme.typography.bodyMedium)
                CrayonTextField(
                    value = text,
                    onValueChange = { text = it.take(ChatRepository.MAX_NICKNAME) },
                    label = stringResource(R.string.chat_nickname),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.chat_nickname_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
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
    disappearing: Boolean = false,
    /** The other person is typing: a little Whity at the bottom says so. */
    peerTyping: Boolean = false,
    /** A group: each member's name over their bubbles, and polls to vote in. */
    group: Boolean = false,
    senderNames: Map<String, String> = emptyMap(),
    myId: String? = null,
    onVote: (ChatMessage, List<Int>) -> Unit = { _, _ -> },
    onStar: (ChatMessage) -> Unit = {},
    /** The search match to bring into view and light up. */
    highlightKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val crayon = MeyouwwTheme.crayon
    val rows = remember(timeline, group) { chatRows(timeline, group) }
    val someone = stringResource(R.string.chat_friend)
    val lastMine = remember(timeline) {
        timeline.lastOrNull { it is TimelineItem.Message && it.message.fromMe && it.message.kind != ChatRepository.KIND_SYSTEM }?.key
    }
    // Opens at the newest message straight away (item 0 is the encryption notice, so the last
    // row is item rows.size). Only messages that arrive while the chat is open scroll into view.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rows.size)
    var shownRows by remember { mutableIntStateOf(rows.size) }
    LaunchedEffect(rows.size) {
        if (rows.size > shownRows) listState.animateScrollToItem(rows.size)
        shownRows = rows.size
    }
    // Show the typing Whity if the newest message was in view.
    LaunchedEffect(peerTyping) {
        val total = listState.layoutInfo.totalItemsCount
        if (peerTyping && total > 0) {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (lastVisible >= total - 2) listState.animateScrollToItem(total - 1)
        }
    }
    // When the keyboard or the plus tray opens, the list gets shorter. If the newest message was
    // on screen, follow the bottom edge up so it stays visible without scrolling by hand.
    LaunchedEffect(listState) {
        var lastHeight = 0
        var pinned = true
        snapshotFlow { listState.layoutInfo.viewportSize.height to !listState.canScrollForward }
            .collect { (height, atEnd) ->
                val total = listState.layoutInfo.totalItemsCount
                if (height != lastHeight && lastHeight != 0 && pinned && total > 0) {
                    listState.scrollToItem(total - 1)
                } else {
                    pinned = atEnd
                }
                lastHeight = height
            }
    }
    // Search: scroll to the match (the first list item is the encryption notice).
    LaunchedEffect(highlightKey) {
        val index = if (highlightKey == null) -1 else rows.indexOfFirst { it.key == highlightKey }
        if (index >= 0) listState.animateScrollToItem(index + 1)
    }
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
                    text = stringResource(if (group) R.string.group_e2ee_notice else R.string.chat_e2ee_notice, name),
                    fill = crayon.yellow,
                    onClick = onOpenSafety,
                )
                Text(
                    stringResource(if (disappearing) R.string.chat_disappearing_notice else R.string.chat_kept_notice),
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
            val lit = if (highlightKey != null && row.key == highlightKey) {
                Modifier.background(crayon.yellow.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            } else {
                Modifier
            }
            Box(motion.padding(top = row.gapAbove).then(lit)) {
                when (row) {
                    is ChatRow.Day -> DayChip(row.at)
                    is ChatRow.Item -> when (val item = row.item) {
                        is TimelineItem.Wallpaper -> WallpaperRow(item, name, mood)
                        is TimelineItem.Message -> if (item.message.kind == ChatRepository.KIND_SYSTEM) {
                            SystemRow(item.message, name, onOpenSafety = onOpenSafety)
                        } else {
                            SwipeToReply(enabled = !item.message.deleted, onReply = { onReply(item.message) }) {
                                val quoted = item.message.replyTo?.let { byId[it]?.message }
                                MessageRow(
                                    message = item.message,
                                    peerName = name,
                                    quoted = quoted,
                                    senderLabel = if (group && !item.message.fromMe && row.first) {
                                        senderNames[item.message.senderId]?.ifBlank { null } ?: someone
                                    } else {
                                        null
                                    },
                                    quotedName = if (group) quoted?.let { senderNames[it.senderId] } else null,
                                    myId = myId,
                                    onVote = { choices -> onVote(item.message, choices) },
                                    onStar = { onStar(item.message) },
                                    mood = mood,
                                    tail = row.tail,
                                    showStatus = item.key == lastMine,
                                    maxBubble = maxBubble,
                                    disappearing = disappearing,
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
        if (peerTyping) {
            item(key = "typing", contentType = "typing") { TypingRow(name) }
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

/** Who a line is from, for grouping: "me", "them" (each member in a group), or null for the app. */
private fun TimelineItem.sender(group: Boolean): String? = when (this) {
    is TimelineItem.Wallpaper -> if (fromMe) "me" else "them"
    is TimelineItem.Message -> when {
        message.kind == ChatRepository.KIND_SYSTEM -> null
        message.fromMe -> "me"
        group -> "them:" + message.senderId
        else -> "them"
    }
}

private fun chatRows(timeline: List<TimelineItem>, group: Boolean = false): List<ChatRow> {
    val rows = ArrayList<ChatRow>(timeline.size + 4)
    var lastDay = -1
    timeline.forEachIndexed { i, item ->
        val day = dayOf(item.at)
        val newDay = day != lastDay
        if (newDay) rows += ChatRow.Day(item.at, day)
        lastDay = day
        val previous = timeline.getOrNull(i - 1)
        val next = timeline.getOrNull(i + 1)
        val who = item.sender(group)
        val first = newDay || previous == null || previous.sender(group) != who || who == null
        val tail = next == null || next.sender(group) != who || dayOf(next.at) != day || who == null
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
            tape = MeyouwwTheme.crayon.blue,
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
    onToggleMute: () -> Unit,
    onOpenProfile: () -> Unit,
    onClearChat: () -> Unit = {},
    /** "typing…", "online" or "last seen …"; the usual hint when unknown. */
    status: String? = null,
    /** A group: "4 people" in place of the relationship, and no safety number or profile. */
    groupSubtitle: String? = null,
    pinned: Boolean = false,
    onTogglePin: () -> Unit = {},
    onSearch: () -> Unit = {},
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
                cornerRadius = 30.dp,
                strokeWidth = 1.dp,
                shadow = crayon.shadow,
                shadowOffsetX = 0.dp,
                shadowOffsetY = 4.dp,
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(PastelIcons.Back, contentDescription = stringResource(R.string.chat_back))
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
                    groupSubtitle ?: peer?.let { levelName(it) } ?: stringResource(R.string.chat_friend),
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
                    status ?: stringResource(if (muted) R.string.chat_header_muted else R.string.chat_header_tap),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (status != null) crayon.onFill else crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        if (peerId != null) {
            // Safety numbers are per person; in a group each member's is in your chat with them.
            if (groupSubtitle == null) {
                IconButton(onClick = onSafety) {
                    Icon(PastelIcons.Lock, contentDescription = stringResource(R.string.chat_safety_open))
                }
            }
            var menu by rememberSaveable { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_search)) },
                        onClick = {
                            menu = false
                            onSearch()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (pinned) R.string.chat_unpin else R.string.chat_pin)) },
                        onClick = {
                            menu = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (muted) R.string.chat_unmute else R.string.chat_mute)) },
                        onClick = {
                            menu = false
                            onToggleMute()
                        },
                    )
                    if (groupSubtitle == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_open_profile)) },
                            onClick = {
                                menu = false
                                onOpenProfile()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_clear)) },
                        onClick = {
                            menu = false
                            onClearChat()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Drag a message to the right to reply, as in WhatsApp. A reply arrow fades in behind the
 * bubble; past the threshold the phone ticks, and letting go starts the reply. The bubble
 * always springs back. Long-press keeps working for every other action.
 */
@Composable
private fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val trigger = with(density) { 64.dp.toPx() }
    val limit = with(density) { 96.dp.toPx() }
    // Plain state, updated in step with the finger (a coroutine per move could land after the
    // spring-back started and leave the bubble stuck halfway).
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    val haptics = LocalHapticFeedback.current
    val armed = offset >= trigger
    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val replyLabel = stringResource(R.string.chat_reply)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = enabled,
                state = rememberDraggableState { delta -> offset = (offset + delta).coerceIn(0f, limit) },
                onDragStarted = { settle?.cancel() },
                onDragStopped = {
                    val reply = offset >= trigger
                    // Slide back smoothly, no bounce, in the screen's own scope so nothing cancels it.
                    settle = scope.launch {
                        animate(offset, 0f, animationSpec = tween(180, easing = FastOutSlowInEasing)) { value, _ -> offset = value }
                    }
                    if (reply) onReply()
                },
            )
            // Screen readers keep the long-press menu; this adds a direct Reply action too.
            .semantics { if (enabled) customActions = listOf(CustomAccessibilityAction(replyLabel) { onReply(); true }) },
    ) {
        val progress = (offset / trigger).coerceIn(0f, 1f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(32.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.6f + 0.4f * progress
                    scaleY = 0.6f + 0.4f * progress
                }
                .clip(CircleShape)
                .background(MeyouwwTheme.crayon.yellow)
                .border(1.5.dp, MeyouwwTheme.crayon.outline, CircleShape),
        ) {
            Text("↩", style = MaterialTheme.typography.titleMedium, color = MeyouwwTheme.crayon.onFill)
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
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
    disappearing: Boolean,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    /** In a group: the sender's name over the first bubble of their run. */
    senderLabel: String? = null,
    /** In a group: who wrote the message this one replies to. */
    quotedName: String? = null,
    myId: String? = null,
    onVote: (List<Int>) -> Unit = {},
    onStar: () -> Unit = {},
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
        if (senderLabel != null) SenderName(senderLabel, message.senderId, Modifier.padding(start = TAIL_WIDTH + 12.dp, bottom = 2.dp))
        Box {
            val sticker = stickerFor(message.sticker)
            val attachment = message.attachment.takeIf { !message.deleted }
            val files = rememberFileActions()
            var fullImage by rememberSaveable(message.id) { mutableStateOf(false) }
            val local = attachment?.file
            if (fullImage && attachment != null && local != null) {
                val big = rememberFileImage(local, maxPx = 2048)
                FullImageDialog(big, attachment.name, onDismiss = { fullImage = false }, onSave = { files.save(local) })
            }
            val click = Modifier.combinedClickable(
                interactionSource = press,
                indication = null,
                onClick = {
                    when {
                        message.status == ChatRepository.STATUS_FAILED -> onRetry()
                        attachment == null || local == null -> Unit
                        attachment.kind == MediaKind.Image -> fullImage = true
                        attachment.kind == MediaKind.Audio -> VoicePlayer.toggle(message.id, local)
                        else -> files.open(local, attachment.mime)
                    }
                },
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
                    if (quoted != null) {
                        // A sticker sent as a reply: what it answers, on a little paper slip.
                        Text(
                            (if (quoted.fromMe) stringResource(R.string.chat_you) else quotedName ?: peerName) + ": " + previewText(quoted),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 220.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.5.dp, crayon.outline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Sticker(sticker, size = 120.dp)
                    MetaLine(message, peerName, showStatus, crayon.textSoft, showSaved = disappearing)
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
                            shadow = crayon.shadow,
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
                                (if (quoted.fromMe) stringResource(R.string.chat_you) else quotedName ?: peerName) + ": " + previewText(quoted),
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
                    } else if (attachment != null) {
                        FileContent(attachment, ink, maxBubble, message.id)
                    } else if (message.poll != null) {
                        PollContent(message.text.orEmpty(), requireNotNull(message.poll), myId, ink, onVote)
                    } else if (message.reminder != null) {
                        ReminderContent(message.text.orEmpty(), requireNotNull(message.reminder), ink)
                    } else {
                        LinkedText(message.text.orEmpty(), ink)
                        message.linkPreview?.let { LinkPreviewCard(it, ink) }
                    }
                    MetaLine(message, peerName, showStatus, ink.copy(alpha = 0.78f), Modifier.align(Alignment.End), showSaved = disappearing)
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
                // Only matters when messages disappear; otherwise everything stays anyway.
                if (disappearing) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (message.saved) R.string.chat_unsave else R.string.chat_save)) },
                        onClick = {
                            menu = false
                            onSave()
                        },
                    )
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.chat_reply)) }, onClick = {
                    menu = false
                    onReply()
                })
                DropdownMenuItem(text = { Text(stringResource(if (message.starred) R.string.chat_unstar else R.string.chat_star)) }, onClick = {
                    menu = false
                    onStar()
                })
                val savable = message.attachment?.file.takeIf { !message.deleted }
                if (savable != null) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.chat_file_save)) }, onClick = {
                        menu = false
                        files.save(savable)
                    })
                }
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
private fun MetaLine(
    message: ChatMessage,
    peerName: String,
    showStatus: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    showSaved: Boolean = false,
) {
    val failed = message.status == ChatRepository.STATUS_FAILED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.padding(top = 2.dp),
    ) {
        Text(metaText(message, showSaved), style = MaterialTheme.typography.labelSmall, color = color)
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
internal fun previewText(message: ChatMessage): String = when {
    message.deleted -> stringResource(R.string.chat_deleted)
    message.sticker != null -> stringResource(R.string.chat_sticker)
    message.attachment != null -> stringResource(mediaLabel(message.attachment?.kind))
    else -> message.text.orEmpty()
}

@Composable
private fun metaText(message: ChatMessage, showSaved: Boolean): String {
    val time = remember(message.sentAt) { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(message.sentAt)) }
    return (if (message.starred) "★ " else "") + time + (if (message.edited) " · " + stringResource(R.string.chat_edited) else "") +
        (if (showSaved && message.saved) " · " + stringResource(R.string.chat_saved) else "")
}

@Composable
private fun SystemRow(message: ChatMessage, peerName: String, onOpenSafety: () -> Unit) {
    val text = when (message.status) {
        ChatRepository.SYSTEM_SAFETY_CHANGED -> stringResource(R.string.chat_system_safety_changed, peerName)
        ChatRepository.SYSTEM_UNREADABLE -> stringResource(R.string.chat_system_unreadable, peerName)
        ChatRepository.SYSTEM_GROUP_CREATED -> stringResource(R.string.group_system_created)
        ChatRepository.SYSTEM_GROUP_ADDED -> stringResource(R.string.group_system_added)
        ChatRepository.SYSTEM_GROUP_LEFT -> stringResource(R.string.group_system_left)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.chat_saved_to_phone)
    val notSavedText = stringResource(R.string.chat_not_saved_to_phone)
    if (full && thumb != null) {
        FullImageDialog(
            thumb,
            title,
            onDismiss = { full = false },
            // The full-size file kept on this phone, not the preview.
            onSave = item.file?.let { file ->
                {
                    scope.launch {
                        val ok = MediaSaver.save(context, file)
                        Toast.makeText(context, if (ok) savedText else notSavedText, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
    Column(horizontalAlignment = if (item.fromMe) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.padding(horizontal = TAIL_WIDTH + 4.dp).graphicsLayer { rotationZ = if (item.fromMe) 2f else -2f }) {
            CrayonCard(
                modifier = Modifier.width(176.dp).padding(top = 10.dp),
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
                        // Phone wallpapers are tall: show a polaroid-sized crop (tap for all of it).
                        .aspectRatio(if (thumb != null) (thumb.width.toFloat() / thumb.height.coerceAtLeast(1)).coerceIn(0.72f, 1.4f) else 0.75f)
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
    /** Null in a group (doodles and wallpapers go to one person). */
    onDraw: (() -> Unit)?,
    onPhoto: (() -> Unit)?,
    onAlarm: (() -> Unit)?,
    onReminder: (() -> Unit)? = null,
    onRinger: ((String) -> Unit)? = null,
    /** Send a photo or video into the chat (end-to-end encrypted). */
    onMedia: (() -> Unit)? = null,
    /** Send a PDF into the chat (end-to-end encrypted). */
    onPdf: (() -> Unit)? = null,
    /** Called as the person types, so the other side can show "typing…". */
    onTyping: () -> Unit = {},
    /** Groups: a poll, and a reminder that goes into everyone's calendar. */
    onPoll: (() -> Unit)? = null,
    onGroupReminder: (() -> Unit)? = null,
    /** The partner-only phone sound buttons (not in groups). */
    showSound: Boolean = true,
    /** A voice message was recorded: the file and how long it is. Null: no microphone button. */
    onVoice: ((File, Long) -> Unit)? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val reducedMotion = LocalReducedMotion.current
    val panelEnter = if (reducedMotion) EnterTransition.None else fadeIn(tween(160)) + expandVertically(tween(220))
    val panelExit = if (reducedMotion) ExitTransition.None else fadeOut(tween(120)) + shrinkVertically(tween(180))
    var text by rememberSaveable { mutableStateOf("") }
    var tray by rememberSaveable { mutableStateOf(false) }
    var stickers by rememberSaveable { mutableStateOf(false) }
    // Voice messages: tap the microphone to record, then send or throw it away.
    val appContext = LocalContext.current.applicationContext
    val recorder = remember { VoiceRecorder(appContext) }
    var recording by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }
    val micFailed = stringResource(R.string.chat_voice_failed)
    val micNeeded = stringResource(R.string.chat_voice_permission)
    val tooShort = stringResource(R.string.chat_voice_too_short)
    fun startRecording() {
        VoicePlayer.stop()
        if (recorder.start()) recording = true else Toast.makeText(appContext, micFailed, Toast.LENGTH_SHORT).show()
    }
    fun finishRecording(send: Boolean) {
        recording = false
        if (!send) {
            recorder.cancel()
            return
        }
        val done = recorder.finish()
        if (done == null) Toast.makeText(appContext, tooShort, Toast.LENGTH_SHORT).show() else onVoice?.invoke(done.first, done.second)
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else Toast.makeText(appContext, micNeeded, Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(editing?.id) { if (editing != null) text = editing.text.orEmpty() }

    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val context = when {
            editing != null -> stringResource(R.string.chat_editing)
            replyTo != null -> stringResource(R.string.chat_replying_to, if (replyTo.fromMe) stringResource(R.string.chat_you) else peerName) +
                ": " + previewText(replyTo)
            else -> null
        }
        AnimatedVisibility(visible = context != null, enter = panelEnter, exit = panelExit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, crayon.outline, RoundedCornerShape(14.dp))
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
                }) { Icon(PastelIcons.Close, contentDescription = stringResource(R.string.chat_cancel)) }
            }
        }
        // The tray takes the height its contents need, so nothing hides behind a scroll. Only on
        // very short screens (landscape, huge text) does the cap below kick in and let it scroll.
        val trayMax = (LocalConfiguration.current.screenHeightDp * 0.6f).dp.coerceAtLeast(260.dp)
        AnimatedVisibility(visible = tray, enter = panelEnter, exit = panelExit) {
            CrayonCard(
                modifier = Modifier.fillMaxWidth().heightIn(max = trayMax).verticalScroll(rememberScrollState()),
                contentPadding = PaddingValues(14.dp),
                seed = 87,
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onMedia != null) {
                        CrayonChip(stringResource(R.string.chat_attach_media), fill = crayon.peach, onClick = {
                            tray = false
                            onMedia()
                        })
                    }
                    if (onPdf != null) {
                        CrayonChip(stringResource(R.string.chat_attach_pdf), fill = crayon.yellow, onClick = {
                            tray = false
                            onPdf()
                        })
                    }
                    if (onPoll != null) {
                        CrayonChip(stringResource(R.string.group_attach_poll), fill = crayon.blue, onClick = {
                            tray = false
                            onPoll()
                        })
                    }
                    if (onGroupReminder != null) {
                        CrayonChip(stringResource(R.string.group_attach_reminder), fill = crayon.green, onClick = {
                            tray = false
                            onGroupReminder()
                        })
                    }
                    if (onDraw != null) {
                        CrayonChip(stringResource(R.string.chat_attach_doodle), fill = crayon.lilac, onClick = {
                            tray = false
                            onDraw()
                        })
                    }
                    if (onPhoto != null) {
                        CrayonChip(stringResource(R.string.chat_attach_photo), fill = crayon.blue, onClick = {
                            tray = false
                            onPhoto()
                        })
                    }
                    if (onAlarm != null) {
                        CrayonChip(stringResource(R.string.chat_attach_alarm), fill = crayon.tangerine, onClick = {
                            tray = false
                            onAlarm()
                        })
                    }
                    if (onReminder != null) {
                        CrayonChip(stringResource(R.string.chat_attach_reminder), fill = crayon.green, onClick = {
                            tray = false
                            onReminder()
                        })
                    }
                    CrayonChip(stringResource(R.string.chat_attach_sticker), fill = crayon.bubblegum, onClick = {
                        tray = false
                        stickers = !stickers
                    })
                }
                if (!showSound) {
                    Text(stringResource(R.string.group_attach_hint), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                } else {
                Text(stringResource(R.string.chat_attach_hint, peerName), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                Text(stringResource(R.string.sheet_sound_title, peerName), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                if (onRinger != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ring" to R.string.sheet_sound_ring, "vibrate" to R.string.sheet_sound_vibrate, "mute" to R.string.sheet_sound_mute).forEach { (mode, label) ->
                            CrayonChip(stringResource(label), fill = crayon.yellow, onClick = {
                                tray = false
                                onRinger(mode)
                            })
                        }
                    }
                } else {
                    Text(stringResource(R.string.sheet_sound_locked), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                }
                }
            }
        }
        AnimatedVisibility(visible = stickers, enter = panelEnter, exit = panelExit) {
            StickerPicker(
                onPick = { sticker ->
                    stickers = false
                    onSticker("whity:" + sticker.name)
                },
            )
        }
        if (recording) {
            RecordingBar(recorder, onCancel = { finishRecording(send = false) }, onSend = { finishRecording(send = true) })
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val turn by animateFloatAsState(if (tray) 45f else 0f, if (reducedMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow), label = "plus")
            val focusManager = LocalFocusManager.current
            RoundButton(
                fill = crayon.bubblegum,
                label = stringResource(R.string.chat_attach),
                onClick = {
                    // Close the keyboard so the whole tray fits above the message box.
                    if (!tray) focusManager.clearFocus()
                    tray = !tray
                },
            ) {
                Icon(PastelIcons.Plus, contentDescription = null, tint = crayon.onFill, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .crayonSurface(
                        fill = MaterialTheme.colorScheme.surface,
                        outline = crayon.edge,
                        seed = 31,
                        cornerRadius = 26.dp,
                        strokeWidth = 2.dp,
                    )
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                val hint = stringResource(R.string.chat_message_hint)
                BasicTextField(
                    value = text,
                    onValueChange = {
                        if (it.length <= 4000) {
                            text = it
                            if (it.isNotBlank()) onTyping()
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    // Typing closes the tray, so the keyboard and the messages share the screen.
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { if (it.isFocused) tray = false }
                        .semantics { contentDescription = hint },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = crayon.textSoft)
                            inner()
                        }
                    },
                )
            }
            val ready = text.isNotBlank()
            val pop by animateFloatAsState(if (ready || reducedMotion) 1f else 0.94f, if (reducedMotion) snap() else spring(dampingRatio = 0.85f), label = "send")
            // Nothing typed: the round button records a voice message instead.
            if (!ready && onVoice != null) {
                RoundButton(
                    fill = crayon.red,
                    label = stringResource(R.string.chat_voice_record),
                    onClick = {
                        focusManager.clearFocus()
                        tray = false
                        val allowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (allowed) startRecording() else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Icon(PastelIcons.Mic, contentDescription = null, tint = crayon.onRed)
                }
                return@Row
            }
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
                Icon(PastelIcons.Send, contentDescription = null, tint = crayon.onRed)
            }
        }
    }
}

/**
 * While recording: a pulsing red dot, the time, a live wave of your voice, and buttons to
 * throw it away or send it.
 */
@Composable
private fun RecordingBar(recorder: VoiceRecorder, onCancel: () -> Unit, onSend: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = recorder.elapsedMs()
            level = recorder.level()
            // Quiet talking is still easy to see: the square root lifts the soft parts.
            levels.add(sqrt(level))
            if (levels.size > LIVE_WAVE_KEEP) levels.removeAt(0)
            // The longest a voice message can be: it goes as it is.
            if (elapsed >= VoiceRecorder.MAX_MS) {
                onSend()
                break
            }
            delay(LIVE_WAVE_MS)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RoundButton(fill = crayon.errorFill, label = stringResource(R.string.chat_voice_cancel), onClick = onCancel) {
            Icon(PastelIcons.Trash, contentDescription = null, tint = crayon.onError)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .crayonSurface(
                    fill = MaterialTheme.colorScheme.surface,
                    outline = crayon.red,
                    seed = 32,
                    cornerRadius = 26.dp,
                    strokeWidth = 2.dp,
                )
                .padding(horizontal = 18.dp, vertical = 13.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            val recording = stringResource(R.string.chat_voice_recording)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(18.dp).semantics { contentDescription = recording },
            ) {
                Box(Modifier.size((10 + 8 * level).dp).clip(CircleShape).background(Color(0xFFE23D2D)))
            }
            Text(clock(elapsed), style = MaterialTheme.typography.titleMedium)
            LiveVoiceWave(
                levels = levels,
                color = crayon.red,
                modifier = Modifier.weight(1f).height(26.dp),
            )
        }
        RoundButton(fill = crayon.red, label = stringResource(R.string.chat_voice_send), onClick = onSend) {
            Icon(PastelIcons.Send, contentDescription = null, tint = crayon.onRed)
        }
    }
}

/** A round pastel button that sits on its own deeper-coloured shadow (attach, send). */
@Composable
internal fun RoundButton(
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
                outline = if (fill == MaterialTheme.colorScheme.surface) crayon.outline else Color.Transparent,
                seed = label.hashCode(),
                cornerRadius = 26.dp,
                strokeWidth = 1.dp,
                shadow = if (enabled) crayon.pressShadowFor(fill) else null,
                shadowOffsetX = 0.dp,
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
        "file" -> stringResource(mediaLabel(MediaKind.fromWire(lastText)))
        "poll" -> "📊 " + lastText.orEmpty()
        "reminder" -> "⏰ " + lastText.orEmpty()
        ChatRepository.KIND_OPENED -> return stringResource(R.string.chat_list_opened)
        else -> lastText.orEmpty()
    }
    return if (lastFromMe && lastKind != null) stringResource(R.string.chat_you) + ": " + body else body
}

/** "📷 Photo", "🎬 Video" or "📄 PDF". */
internal fun mediaLabel(kind: MediaKind?): Int = when (kind) {
    MediaKind.Video -> R.string.chat_video_label
    MediaKind.Pdf -> R.string.chat_pdf_label
    MediaKind.Doc -> R.string.chat_doc_label
    MediaKind.Audio -> R.string.chat_voice_label
    else -> R.string.chat_photo_label
}

/** Opening a chat file in another app (one-off read grant) and saving it to the phone. */
internal class FileActions(
    private val context: Context,
    private val scope: CoroutineScope,
    private val savedText: String,
    private val notSavedText: String,
    private val noAppText: String,
) {
    fun open(file: File, mime: String) {
        val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".files", file) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, noAppText, Toast.LENGTH_SHORT).show()
        }
    }

    fun save(file: File) {
        scope.launch {
            val ok = MediaSaver.save(context, file)
            Toast.makeText(context, if (ok) savedText else notSavedText, Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun rememberFileActions(): FileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.chat_file_saved)
    val notSaved = stringResource(R.string.chat_not_saved_to_phone)
    val noApp = stringResource(R.string.chat_file_no_app)
    return remember(context, scope, saved) { FileActions(context, scope, saved, notSaved, noApp) }
}

/** The tiny preview that came inside the encrypted message, until the file itself is here. */
@Composable
internal fun rememberThumb(base64: String?): ImageBitmap? = remember(base64) {
    base64?.let {
        runCatching {
            val bytes = Base64.getDecoder().decode(it)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}

/** A photo, video or PDF inside a speech bubble. Tapping the bubble opens it. */
@Composable
private fun FileContent(attachment: ChatAttachment, ink: Color, maxBubble: Dp, messageId: String) {
    if (attachment.kind == MediaKind.Audio) return VoiceNoteContent(messageId, attachment, ink, maxBubble)
    val thumb = rememberThumb(attachment.thumb)
    val status = when {
        attachment.unavailable -> stringResource(R.string.chat_file_unavailable)
        attachment.file == null -> stringResource(R.string.chat_file_downloading)
        else -> null
    }
    when (attachment.kind) {
        MediaKind.Image, MediaKind.Video -> {
            val full = if (attachment.kind == MediaKind.Image) rememberFileImage(attachment.file, maxPx = 720) else null
            val picture = full ?: thumb
            val w = attachment.width ?: picture?.width ?: 1
            val h = attachment.height ?: picture?.height ?: 1
            val ratio = (w.toFloat() / h.coerceAtLeast(1)).coerceIn(0.6f, 1.8f)
            val label = stringResource(mediaLabel(attachment.kind))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .width(minOf(maxBubble - 48.dp, 260.dp))
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ink.copy(alpha = 0.1f))
                    .semantics { contentDescription = label },
            ) {
                if (picture != null) {
                    Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (attachment.kind == MediaKind.Video) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Text("▶", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    attachment.durationMs?.let { ms ->
                        val seconds = (ms / 1000).toInt()
                        Text(
                            "%d:%02d".format(seconds / 60, seconds % 60),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (status != null) {
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        else -> {
            val context = LocalContext.current
            val size = remember(attachment.size) { Formatter.formatShortFileSize(context, attachment.size) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 4.dp).widthIn(max = maxBubble - 48.dp),
            ) {
                if (thumb != null) {
                    Image(
                        thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, ink.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                    )
                } else {
                    Text(if (attachment.kind == MediaKind.Doc) "📝" else "📄", style = MaterialTheme.typography.headlineMedium)
                }
                Column {
                    Text(attachment.name, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(if (attachment.kind == MediaKind.Doc) "Word" else "PDF", size, status).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = ink.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

/** "typing…", "online", "last seen today at 4:55 pm"…; null when nothing is known. */
@Composable
private fun presenceText(presence: Presence?): String? {
    presence ?: return null
    if (presence.typing) return stringResource(R.string.chat_status_typing)
    if (presence.online) return stringResource(R.string.chat_status_online)
    val seen = presence.lastSeen ?: return null
    val zone = java.time.ZoneId.systemDefault()
    val day = seen.atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(seen.toEpochMilli()))
    return when (day) {
        today -> stringResource(R.string.chat_status_seen_today, time)
        today.minusDays(1) -> stringResource(R.string.chat_status_seen_yesterday, time)
        else -> stringResource(R.string.chat_status_seen_date, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(seen.toEpochMilli())))
    }
}

/** The other person is typing: a small Whity at the laptop, bobbing gently. */
@Composable
private fun TypingRow(name: String) {
    val reducedMotion = LocalReducedMotion.current
    val bob = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "typing")
        transition.animateFloat(0f, -4f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "bob").value
    }
    val label = stringResource(R.string.chat_peer_typing, name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(start = TAIL_WIDTH, top = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Sticker(WhitySticker.Typing, size = 56.dp, decorative = true, modifier = Modifier.graphicsLayer { translationY = bob.dp.toPx() })
        Text(stringResource(R.string.chat_status_typing), style = MaterialTheme.typography.labelMedium, color = MeyouwwTheme.crayon.textSoft)
    }
}

/** Whity packs: a tab per pack (its first sticker and name) and a grid of that pack. */
@Composable
private fun StickerPicker(onPick: (WhitySticker) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val packs = WhityPacks.all
    var selected by rememberSaveable { mutableStateOf(packs.first().first) }
    val current = packs.firstOrNull { it.first == selected }?.second ?: packs.first().second
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .crayonSurface(
                fill = MaterialTheme.colorScheme.surface,
                outline = crayon.outline,
                seed = 77,
                cornerRadius = 24.dp,
                strokeWidth = 1.dp,
                shadow = crayon.shadow,
                shadowOffsetX = 0.dp,
                shadowOffsetY = 4.dp,
            )
            .padding(10.dp),
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(packs, key = { it.first }) { (pack, list) ->
                val on = pack == selected
                val title = stringResource(WhityPacks.title(pack))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) crayon.yellow else crayon.outline.copy(alpha = 0.06f))
                        .border(if (on) 2.dp else 1.dp, crayon.outline.copy(alpha = if (on) 1f else 0.3f), RoundedCornerShape(14.dp))
                        .clickable(role = Role.Tab, onClickLabel = title) { selected = pack }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Sticker(list.first(), size = 30.dp, decorative = true)
                    Text(title, style = MaterialTheme.typography.labelMedium, color = if (on) crayon.onFill else crayon.textSoft, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(232.dp),
        ) {
            items(current, key = { it.name }) { sticker ->
                Sticker(
                    sticker,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = stringResource(sticker.label)) { onPick(sticker) },
                    size = 72.dp,
                )
            }
        }
    }
}
