package com.meyouww.feature.friends

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyGlass
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import com.meyouww.core.designsystem.component.CardLevel
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.skyHeartOrbit
import com.meyouww.core.designsystem.component.skyBob
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.FullPhotoDialog
import com.meyouww.core.designsystem.component.LinkRow
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Capability
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.parseInstant
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class FriendDetailUiState(
    val loading: Boolean = true,
    val friend: Friend? = null,
    val busy: String? = null,
    val message: Int? = null,
    val closed: Boolean = false,
)

private data class DetailLocal(
    val id: String? = null,
    val loading: Boolean = true,
    val busy: String? = null,
    val message: Int? = null,
    val closed: Boolean = false,
)

@HiltViewModel
class FriendDetailViewModel @Inject constructor(
    private val repo: FriendsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(DetailLocal())

    val state: StateFlow<FriendDetailUiState> = combine(local, repo.friends) { l, friends ->
        FriendDetailUiState(l.loading && friends == null, friends?.firstOrNull { it.id == l.id }, l.busy, l.message, l.closed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendDetailUiState())

    fun load(id: String) {
        local.update { it.copy(id = id) }
        viewModelScope.launch {
            repo.refreshFriends()
            local.update { it.copy(loading = false) }
        }
    }

    fun invite(level: RelationshipLevel, label: String?) = act("invite") { repo.invite(it, level, label) }
    fun respondInvite(accept: Boolean) = act("answer") { id -> state.value.friend?.invite?.let { repo.respondInvite(it.id, accept) } ?: Outcome.Success(ChangeResult.Gone) }
    fun cancelInvite() = act("answer") { _ -> state.value.friend?.invite?.let { repo.cancelInvite(it.id) } ?: Outcome.Success(ChangeResult.Gone) }
    fun stepDown(level: RelationshipLevel) = act("level") { repo.setLevel(it, level) }
    fun rename(label: String) = act("level") { repo.setLevel(it, RelationshipLevel.Partner, label) }
    fun remove() = act("remove", closeOnSuccess = true) { repo.removeFriend(it) }
    fun block() = act("block", closeOnSuccess = true) { repo.block(it) }
    fun messageShown() = local.update { it.copy(message = null) }

    private fun act(key: String, closeOnSuccess: Boolean = false, block: suspend (String) -> Outcome<ChangeResult>) {
        val id = local.value.id ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = key, message = null) }
            val outcome = block(id)
            val ok = outcome is Outcome.Success && outcome.value in setOf(ChangeResult.Ok, ChangeResult.Pending, ChangeResult.Accepted, ChangeResult.Declined)
            val message = when {
                ok -> null
                outcome is Outcome.Failure -> outcome.error.messageRes()
                outcome is Outcome.Success && outcome.value == ChangeResult.AnswerTheirsFirst -> R.string.detail_answer_theirs
                outcome is Outcome.Success -> resultMessage(outcome.value)
                else -> null
            }
            repo.refreshFriends()
            local.update { it.copy(busy = null, message = message, closed = ok && closeOnSuccess) }
        }
    }
}

@Composable
fun FriendDetailScreen(
    friendId: String,
    onBack: () -> Unit,
    onOpenPermissions: (String) -> Unit,
    onDraw: (String) -> Unit = {},
    onSendPhoto: (String) -> Unit = {},
    onChat: ((String) -> Unit)? = null,
    onAlarm: ((String) -> Unit)? = null,
    viewModel: FriendDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(friendId) { viewModel.load(friendId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    val friend = state.friend
    val crayon = MeyouwwTheme.crayon

    var labelDialogFor by rememberSaveable { mutableStateOf<String?>(null) } // "invite" or "rename"
    var confirmStepDown by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }

    var menu by remember { mutableStateOf(false) }
    CrayonScreen(
        title = if (crayon.living) "" else friend?.card?.name ?: "",
        onBack = onBack,
        trailing = if (crayon.living && friend != null) {
            {
                Box {
                    SkyIconButton(SkyIcons.More, stringResource(R.string.detail_sky_more), { menu = true }, size = SkyIconSize.Small)
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val level = friend.levelValue
                        if (friend.invite == null && RelationshipLevel.BestFriend in level.higher) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_ask_bestie)) }, onClick = {
                                menu = false
                                viewModel.invite(RelationshipLevel.BestFriend, null)
                            })
                        }
                        if (friend.invite == null && RelationshipLevel.Partner in level.higher) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_ask_partner)) }, onClick = {
                                menu = false
                                labelDialogFor = "invite"
                            })
                        }
                        level.lower.forEach { lower ->
                            val name = levelName(lower, null)
                            DropdownMenuItem(text = { Text(stringResource(R.string.detail_step_down, name)) }, onClick = {
                                menu = false
                                confirmStepDown = lower.wire
                            })
                        }
                    }
                }
            }
        } else {
            null
        },
    ) {
        if (friend == null) {
            if (state.loading) {
                WhityLoading(stringResource(R.string.friends_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            } else {
                StickerMessage(WhitySticker.OopsFacepalm, title = stringResource(R.string.detail_not_found), body = "")
            }
            return@CrayonScreen
        }
        val level = friend.levelValue
        var fullPhoto by rememberSaveable { mutableStateOf(false) }
        if (fullPhoto) FullPhotoDialog(photo = friend.card.avatar, caption = friend.card.name, onDismiss = { fullPhoto = false })
        if (crayon.living) {
            SkyFriendPage(
                friend = friend,
                state = state,
                onPhoto = { fullPhoto = true },
                onChat = onChat?.let { chat -> { chat(friend.id) } },
                onDraw = { onDraw(friend.id) },
                onSendPhoto = { onSendPhoto(friend.id) },
                onAlarm = onAlarm?.let { alarm -> { alarm(friend.id) } },
                onRename = { labelDialogFor = "rename" },
                onMenu = { menu = true },
                onPermissions = { onOpenPermissions(friend.id) },
                onAnswer = viewModel::respondInvite,
                onCancelInvite = viewModel::cancelInvite,
                onRemove = { confirmRemove = true },
                onBlock = { confirmBlock = true },
                onMessageShown = viewModel::messageShown,
            )
        }
        if (crayon.living) {
            // (the Living Sky page is drawn above)
        } else if (false) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(156.dp).skyHeartOrbit().skyBob()) {
                    PersonAvatar(
                        friend.card.initial,
                        friend.id.hashCode(),
                        size = 108.dp,
                        photo = friend.card.avatar,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = friend.card.avatar != null, onClickLabel = stringResource(R.string.detail_view_photo), role = Role.Button) {
                                fullPhoto = true
                            },
                    )
                }
                Text(friend.card.handle, style = MaterialTheme.typography.titleLarge.copy(shadow = skyHalo()))
                sinceText(friend.since)?.let {
                    Text(stringResource(R.string.detail_since, it), style = MaterialTheme.typography.bodyMedium.copy(shadow = skyHalo()), color = crayon.textSoft)
                }
            }
        } else CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.peach, seed = 204) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Tap the photo to see it full size.
                PersonAvatar(
                    friend.card.initial,
                    friend.id.hashCode(),
                    size = 64.dp,
                    photo = friend.card.avatar,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = friend.card.avatar != null, onClickLabel = stringResource(R.string.detail_view_photo), role = Role.Button) {
                            fullPhoto = true
                        },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(friend.card.handle, style = MaterialTheme.typography.titleLarge)
                    sinceText(friend.since)?.let { Text(stringResource(R.string.detail_since, it), style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft) }
                }
            }
        }

        if (!crayon.living) state.message?.let {
            CrayonChip(
                text = stringResource(it),
                fill = crayon.peach,
                onClick = viewModel::messageShown,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (!crayon.living) CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.blue, seed = 3, level = CardLevel.Raised) {
            Text(
                stringResource(R.string.detail_send_title, friend.card.name),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            onChat?.let { chat ->
                CrayonButton(stringResource(R.string.detail_send_chat), onClick = { chat(friend.id) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(
                    stringResource(R.string.detail_send_doodle),
                    onClick = { onDraw(friend.id) },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
                CrayonButton(
                    stringResource(R.string.detail_send_photo),
                    onClick = { onSendPhoto(friend.id) },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
            onAlarm?.let { alarm ->
                CrayonButton(
                    stringResource(R.string.detail_send_alarm),
                    onClick = { alarm(friend.id) },
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(
                    when (friend.capability) {
                        Capability.Auto -> R.string.detail_send_hint_auto
                        Capability.Ask -> R.string.detail_send_hint_ask
                        Capability.Unavailable -> R.string.detail_send_hint_unavailable
                    },
                    friend.card.name,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (!crayon.living) CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 5) {
            Text(stringResource(R.string.detail_relationship), style = MaterialTheme.typography.labelLarge)
            Text(levelText(level, friend.label), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.detail_relationship_private), style = MaterialTheme.typography.bodySmall)

            val invite = friend.invite
            when {
                invite != null && invite.incoming -> {
                    Text(
                        if (invite.levelValue == RelationshipLevel.Partner) {
                            stringResource(R.string.detail_invite_incoming_partner, friend.card.name, invite.label ?: stringResource(R.string.level_partner_default))
                        } else {
                            stringResource(R.string.detail_invite_incoming_bestie, friend.card.name)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CrayonButton(stringResource(R.string.friends_accept), onClick = { viewModel.respondInvite(true) }, loading = state.busy == "answer", modifier = Modifier.weight(1f))
                        CrayonButton(stringResource(R.string.friends_not_now), onClick = { viewModel.respondInvite(false) }, kind = ButtonKind.Quiet, enabled = state.busy == null, modifier = Modifier.weight(1f))
                    }
                }
                invite != null -> {
                    Text(stringResource(R.string.detail_invite_sent, friend.card.name, levelName(invite.levelValue, invite.label)), style = MaterialTheme.typography.bodyLarge)
                    CrayonButton(stringResource(R.string.detail_invite_cancel), onClick = viewModel::cancelInvite, kind = ButtonKind.Quiet, loading = state.busy == "answer")
                }
                else -> {
                    if (RelationshipLevel.BestFriend in level.higher) {
                        CrayonButton(stringResource(R.string.detail_ask_bestie), onClick = { viewModel.invite(RelationshipLevel.BestFriend, null) }, kind = ButtonKind.Secondary, loading = state.busy == "invite", modifier = Modifier.fillMaxWidth())
                    }
                    if (RelationshipLevel.Partner in level.higher) {
                        CrayonButton(stringResource(R.string.detail_ask_partner), onClick = { labelDialogFor = "invite" }, kind = ButtonKind.Secondary, enabled = state.busy == null, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (level == RelationshipLevel.Partner) {
                CrayonButton(stringResource(R.string.detail_rename_label), onClick = { labelDialogFor = "rename" }, kind = ButtonKind.Quiet)
            }
            level.lower.forEach { lower ->
                val name = levelName(lower, null)
                CrayonButton(stringResource(R.string.detail_step_down, name), onClick = { confirmStepDown = lower.wire }, kind = ButtonKind.Quiet, enabled = state.busy == null)
            }
        }

        if (!crayon.living) CrayonCard(modifier = Modifier.fillMaxWidth(), seed = 9) {
            LinkRow(
                title = stringResource(R.string.detail_decorate),
                detail = stringResource(R.string.detail_decorate_body, friend.card.name),
                onClick = { onOpenPermissions(friend.id) },
            )
            Text(
                stringResource(R.string.detail_they_allow, stringResource(capabilityText(friend.capability))),
                style = MaterialTheme.typography.bodyMedium,
                color = crayon.textSoft,
            )
        }

        if (!crayon.living) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CrayonButton(stringResource(R.string.detail_remove), onClick = { confirmRemove = true }, kind = ButtonKind.Quiet, loading = state.busy == "remove", modifier = Modifier.weight(1f))
            CrayonButton(stringResource(R.string.detail_block), onClick = { confirmBlock = true }, kind = ButtonKind.Quiet, loading = state.busy == "block", modifier = Modifier.weight(1f))
        }

        labelDialogFor?.let { purpose ->
            LabelDialog(
                initial = if (purpose == "rename") friend.label.orEmpty() else "",
                onDismiss = { labelDialogFor = null },
                onConfirm = { label ->
                    labelDialogFor = null
                    if (purpose == "rename") viewModel.rename(label) else viewModel.invite(RelationshipLevel.Partner, label)
                },
            )
        }
        confirmStepDown?.let { wire ->
            val lower = RelationshipLevel.fromWire(wire)
            val name = levelName(lower, null)
            ConfirmDialog(
                title = stringResource(R.string.detail_step_down_title, name),
                body = stringResource(R.string.detail_step_down_body),
                onDismiss = { confirmStepDown = null },
                onConfirm = {
                    confirmStepDown = null
                    viewModel.stepDown(lower)
                },
            )
        }
        if (confirmRemove) {
            ConfirmDialog(
                title = stringResource(R.string.detail_remove_title, friend.card.name),
                body = stringResource(R.string.detail_remove_body),
                onDismiss = { confirmRemove = false },
                onConfirm = {
                    confirmRemove = false
                    viewModel.remove()
                },
            )
        }
        if (confirmBlock) {
            ConfirmDialog(
                title = stringResource(R.string.detail_block_title, friend.card.name),
                body = stringResource(R.string.detail_block_body),
                onDismiss = { confirmBlock = false },
                onConfirm = {
                    confirmBlock = false
                    viewModel.block()
                },
            )
        }
    }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit, confirmLabel: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel ?: stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val presets = listOf(R.string.label_boyfriend, R.string.label_girlfriend, R.string.label_partner, R.string.label_love).map { stringResource(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_label_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.detail_label_hint))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        CrayonChip(
                            text = preset,
                            fill = if (text == preset) MeyouwwTheme.crayon.bubblegum else MeyouwwTheme.crayon.yellow,
                            onClick = { text = preset },
                        )
                    }
                }
                CrayonTextField(value = text, onValueChange = { text = it.take(24) }, label = stringResource(R.string.detail_label_custom))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.detail_send_invite)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun sinceText(since: String?): String? =
    parseInstant(since)?.atZone(ZoneId.systemDefault())?.toLocalDate()?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/**
 * A friend's page in the Living Sky look, as on the board: their coin big on the sky with hearts
 * circling it, their name, relationship and ID; four tiles (chat, doodle, photo, alarm); a mint
 * strip saying how your wallpapers reach them; rows for your label and what they may do on your
 * phone; and Remove friend / Block on a glass strip. Asking to be closer is in the ⋯ menu.
 */
@Composable
private fun SkyFriendPage(
    friend: Friend,
    state: FriendDetailUiState,
    onPhoto: () -> Unit,
    onChat: (() -> Unit)?,
    onDraw: () -> Unit,
    onSendPhoto: () -> Unit,
    onAlarm: (() -> Unit)?,
    onRename: () -> Unit,
    onMenu: () -> Unit,
    onPermissions: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onCancelInvite: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val level = friend.levelValue
    Column(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = 150.dp, height = 124.dp).skyHeartOrbit()) {
            Box(
                Modifier.drawBehind {
                    val w = 3.dp.toPx()
                    drawCircle(crayon.red, radius = size.minDimension / 2 + 3.dp.toPx() + w / 2, style = Stroke(w))
                },
            ) {
                PersonAvatar(
                    friend.card.initial,
                    friend.id.hashCode(),
                    size = 104.dp,
                    photo = friend.card.avatar,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = friend.card.avatar != null, onClickLabel = stringResource(R.string.detail_view_photo), role = Role.Button, onClick = onPhoto),
                )
            }
        }
        Text(friend.card.name, style = SkyType.h1.copy(shadow = skyHalo()), color = SkyInk.onSky, modifier = Modifier.semantics { heading() })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkyPill(levelName(level, friend.label), tone = when (level) {
                RelationshipLevel.Partner -> SkyTone.Pink
                RelationshipLevel.BestFriend -> SkyTone.Lilac
                RelationshipLevel.Friend -> SkyTone.Mint
            })
            Text(friend.card.handle, style = SkyType.sm.copy(fontWeight = FontWeight.Bold, shadow = skyHalo()), color = SkyInk.onSkySoft)
        }
        sinceText(friend.since)?.let {
            Text(stringResource(R.string.detail_since, it), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
        }
    }
    state.message?.let {
        SkyChip(stringResource(it), tone = SkyTone.Butter, onClick = onMessageShown, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    // Four things to send them.
    data class Tile(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: SkyTone?, val onClick: () -> Unit)
    val tiles = listOfNotNull(
        onChat?.let { Tile(stringResource(R.string.detail_sky_chat), SkyIcons.Chat, null, it) },
        Tile(stringResource(R.string.detail_sky_doodle), SkyIcons.Pencil, SkyTone.Pink, onDraw),
        Tile(stringResource(R.string.detail_sky_photo), SkyIcons.Image, SkyTone.Blue, onSendPhoto),
        onAlarm?.let { Tile(stringResource(R.string.detail_sky_alarm), SkyIcons.Alarm, SkyTone.Butter, it) },
    )
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            row.forEach { tile ->
                if (tile.tone == null) {
                    // The main one: the accent.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .skySurface(crayon.red, crayon.berryDeep, crayon.shadow, crayon.sunX, cornerRadius = 24.dp, depth = 4.dp, lift = 12.dp, blur = 20.dp, belly = false)
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Button, onClick = tile.onClick)
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Icon(tile.icon, contentDescription = null, tint = crayon.onRed, modifier = Modifier.size(22.dp))
                        Text(tile.label, style = SkyType.t, color = crayon.onRed)
                    }
                } else {
                    SkyCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        tone = tile.tone,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                        corner = 24.dp,
                        onClick = tile.onClick,
                        onClickLabel = tile.label,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(tile.icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(22.dp))
                            Text(tile.label, style = SkyType.t, color = SkyInk.onPastel)
                        }
                    }
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
    // How your wallpapers reach them.
    SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Mint, level = CardLevel.Flat, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), corner = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(SkyIcons.Check, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(18.dp))
            Text(
                stringResource(
                    when (friend.capability) {
                        Capability.Auto -> R.string.detail_send_hint_auto
                        Capability.Ask -> R.string.detail_send_hint_ask
                        Capability.Unavailable -> R.string.detail_send_hint_unavailable
                    },
                    friend.card.name,
                ),
                style = SkyType.sm,
                color = SkyInk.onPastel,
            )
        }
    }
    // An invitation to be closer, either way.
    val invite = friend.invite
    if (invite != null && invite.incoming) {
        SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Pink, level = CardLevel.Raised, spacing = 10.dp) {
            Text(
                if (invite.levelValue == RelationshipLevel.Partner) {
                    stringResource(R.string.detail_invite_incoming_partner, friend.card.name, invite.label ?: stringResource(R.string.level_partner_default))
                } else {
                    stringResource(R.string.detail_invite_incoming_bestie, friend.card.name)
                },
                style = SkyType.tt,
                color = SkyInk.onPastel,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SkyButton(stringResource(R.string.friends_accept), { onAnswer(true) }, modifier = Modifier.weight(1f), small = true, loading = state.busy == "answer")
                SkyButton(stringResource(R.string.friends_not_now), { onAnswer(false) }, small = true, tone = SkyButtonTone.Quiet, enabled = state.busy == null)
            }
        }
    }
    SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        SkyLinkRow(
            title = stringResource(R.string.detail_sky_label, levelName(level, friend.label)),
            detail = stringResource(if (level == RelationshipLevel.Partner) R.string.detail_sky_label_body else R.string.detail_relationship_private),
            first = true,
            onClick = if (level == RelationshipLevel.Partner) onRename else onMenu,
        )
        if (invite != null && !invite.incoming) {
            SkyLinkRow(
                title = stringResource(R.string.detail_invite_sent, friend.card.name, levelName(invite.levelValue, invite.label)),
                detail = stringResource(R.string.detail_invite_cancel),
                onClick = onCancelInvite,
            )
        }
        SkyLinkRow(
            title = stringResource(R.string.detail_sky_may_do, friend.card.name),
            detail = stringResource(R.string.detail_they_allow, stringResource(capabilityText(friend.capability))),
            onClick = onPermissions,
        )
    }
    Row(
        horizontalArrangement = Arrangement.SpaceAround,
        modifier = Modifier
            .fillMaxWidth()
            .skyGlass(999.dp, crayon.card, crayon.hardShadow, Color.Transparent, 0f)
            .padding(horizontal = 6.dp),
    ) {
        val danger = Color(0xFF8E1B1B)
        TextButton(onClick = onRemove, enabled = state.busy == null) { Text(stringResource(R.string.detail_remove), style = SkyType.button, color = danger) }
        TextButton(onClick = onBlock, enabled = state.busy == null) { Text(stringResource(R.string.detail_block), style = SkyType.button, color = danger) }
    }
}

/** A row on a card (.r3) that opens something: title, a soft line under it, and an arrow. */
@Composable
private fun SkyLinkRow(title: String, detail: String, onClick: () -> Unit, first: Boolean = false) {
    val crayon = MeyouwwTheme.crayon
    val line = crayon.line
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind { if (!first) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 66.dp)
            .padding(vertical = 9.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SkyType.tt)
            Text(detail, style = SkyType.sb, color = crayon.textSoft)
        }
        Icon(SkyIcons.Next, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.size(18.dp))
    }
}
