package com.meyouww.feature.friends

import com.meyouww.core.model.Capability
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySwitch
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import com.meyouww.core.designsystem.component.CardLevel
import androidx.compose.material3.Icon
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.designsystem.component.Sticker
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.LinkRow
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.Friend
import com.meyouww.core.model.FriendRequest
import com.meyouww.core.model.FriendRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val friends: List<Friend>? = null,
    val requests: FriendRequests = FriendRequests(),
    val remotePaused: Boolean = false,
    val busy: Set<String> = emptySet(),
    val message: Int? = null,
    val askNotifications: Boolean = false,
)

private data class LocalState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val busy: Set<String> = emptySet(),
    val message: Int? = null,
    val askNotifications: Boolean = false,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val repo: FriendsRepository,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val local = MutableStateFlow(LocalState(loading = repo.friends.value == null))

    val state: StateFlow<FriendsUiState> = combine(local, repo.friends, repo.requests, repo.remotePaused) { l, friends, requests, paused ->
        FriendsUiState(l.loading, l.error, friends, requests, paused, l.busy, l.message, l.askNotifications)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun refresh() = viewModelScope.launch {
        val friends = async { repo.refreshFriends() }
        val requests = async { repo.refreshRequests() }
        val pause = async { repo.refreshRemotePause() }
        val f = friends.await()
        requests.await()
        pause.await()
        local.update {
            it.copy(loading = false, error = (f as? Outcome.Failure)?.error?.takeIf { repo.friends.value == null })
        }
    }

    fun respond(request: FriendRequest, accept: Boolean) = act(request.id) {
        val outcome = repo.respondRequest(request.id, accept)
        if (outcome is Outcome.Success && outcome.value == ChangeResult.Accepted) afterFirstFriend()
        outcome
    }

    fun cancel(request: FriendRequest) = act(request.id) { repo.cancelRequest(request.id) }

    fun setPaused(paused: Boolean) = act("pause") { repo.setRemotePause(paused) }

    fun revokeEverything() = act("revoke") { repo.revokeEverything() }

    fun messageShown() = local.update { it.copy(message = null) }

    fun explainerDone() = viewModelScope.launch {
        prefs.setNotificationExplainerShown()
        local.update { it.copy(askNotifications = false) }
    }

    private suspend fun afterFirstFriend() {
        if (!prefs.notificationExplainerShown.first()) local.update { it.copy(askNotifications = true) }
    }

    private fun act(key: String, block: suspend () -> Outcome<ChangeResult>) = viewModelScope.launch {
        local.update { it.copy(busy = it.busy + key, message = null) }
        val outcome = block()
        val message = when (outcome) {
            is Outcome.Failure -> outcome.error.messageRes()
            is Outcome.Success -> when (outcome.value) {
                ChangeResult.Ok, ChangeResult.Accepted, ChangeResult.Declined, ChangeResult.Pending -> null
                else -> resultMessage(outcome.value)
            }
        }
        local.update { it.copy(busy = it.busy - key, message = message) }
        repo.refreshFriends()
        repo.refreshRequests()
    }
}

/** The Friends tab: requests, your people, and the Emergency Pause. */
@Composable
fun FriendsScreen(
    onAddFriend: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenBlocked: () -> Unit,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val context = LocalContext.current
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }

    val living = MeyouwwTheme.crayon.living
    if (living) {
        SkyFriends(state, viewModel, onAddFriend, onOpenFriend, onOpenBlocked, onRevokeAll = { confirmRevoke = true })
    } else
    CrayonScreen(
        title = stringResource(R.string.friends_title),
        // Living Sky: the one main button floats at the bottom, within reach of a thumb.
        floating = { CrayonButton(text = stringResource(R.string.friends_add), onClick = onAddFriend, icon = PastelIcons.AddPerson) },
    ) {
        if (!living) CrayonButton(text = stringResource(R.string.friends_add), onClick = onAddFriend, modifier = Modifier.fillMaxWidth())

        state.message?.let { message ->
            CrayonChip(
                text = stringResource(message),
                fill = MeyouwwTheme.crayon.peach,
                onClick = viewModel::messageShown,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        val requests = state.requests
        if (requests.incoming.isNotEmpty() || requests.outgoing.isNotEmpty()) {
            SectionTitle(stringResource(R.string.friends_requests))
            requests.incoming.forEach { request ->
                RequestCard(request, incoming = true, busy = request.id in state.busy, onAccept = { viewModel.respond(request, true) }, onDecline = { viewModel.respond(request, false) })
            }
            requests.outgoing.forEach { request ->
                RequestCard(request, incoming = false, busy = request.id in state.busy, onAccept = {}, onDecline = { viewModel.cancel(request) })
            }
        }

        val friends = state.friends
        when {
            friends == null && state.loading -> WhityLoading(stringResource(R.string.friends_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
            friends == null -> StickerMessage(
                sticker = WhitySticker.OopsFacepalm,
                blob = MeyouwwTheme.crayon.peach,
                title = stringResource(R.string.friends_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.friends_retry),
                onAction = { viewModel.refresh() },
            )
            friends.isEmpty() -> StickerMessage(
                sticker = WhitySticker.LoveTeddyHug,
                blob = MeyouwwTheme.crayon.peach,
                title = stringResource(R.string.friends_empty_title),
                body = stringResource(R.string.friends_empty_body),
            )
            else -> {
                SectionTitle(stringResource(R.string.friends_your_people))
                friends.forEach { friend -> FriendRow(friend, onClick = { onOpenFriend(friend.id) }) }
            }
        }

        // Whity on guard: who may decorate this phone, and the one switch that stops everyone.
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.green, seed = 31) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.pause_body), style = MaterialTheme.typography.bodyMedium)
                }
                Sticker(WhitySticker.GrrOnGuard, decorative = true, size = 84.dp)
            }
            SwitchRow(
                title = stringResource(R.string.pause_switch),
                detail = stringResource(if (state.remotePaused) R.string.pause_switch_on else R.string.pause_switch_off),
                checked = state.remotePaused,
                enabled = "pause" !in state.busy,
                onCheckedChange = viewModel::setPaused,
            )
            CrayonButton(
                text = stringResource(R.string.pause_revoke_all),
                onClick = { confirmRevoke = true },
                kind = ButtonKind.Secondary,
                loading = "revoke" in state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            LinkRow(title = stringResource(R.string.blocked_people), onClick = onOpenBlocked)
        }
    }

    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text(stringResource(R.string.pause_revoke_all_title)) },
            text = { Text(stringResource(R.string.pause_revoke_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    viewModel.revokeEverything()
                }) { Text(stringResource(R.string.pause_revoke_all_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (state.askNotifications) {
        if (notificationsAllowed(context)) {
            LaunchedEffect(Unit) { viewModel.explainerDone() }
        } else {
            NotificationExplainer(onDone = viewModel::explainerDone)
        }
    }
}

@Composable
private fun RequestCard(
    request: FriendRequest,
    incoming: Boolean,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        fill = if (incoming) crayon.peach else MaterialTheme.colorScheme.surface,
        seed = request.id.hashCode(),
        level = if (incoming) CardLevel.Raised else CardLevel.Flat,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(request.user.initial, request.user.id.hashCode(), photo = request.user.avatar)
            Column(Modifier.weight(1f)) {
                Text(request.user.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (incoming) "${request.user.handle} · ${stringResource(R.string.friends_incoming_body)}" else "${request.user.handle} · ${stringResource(R.string.friends_pending)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (incoming) {
                CrayonButton(stringResource(R.string.friends_accept), onClick = onAccept, loading = busy, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.friends_not_now), onClick = onDecline, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
            } else {
                Text(stringResource(R.string.friends_outgoing_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).align(Alignment.CenterVertically))
                CrayonButton(stringResource(R.string.friends_cancel_request), onClick = onDecline, kind = ButtonKind.Quiet, loading = busy)
            }
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onClick: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val card = friend.card
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        seed = friend.id.hashCode(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.friends_open, card.name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(card.initial, friend.id.hashCode(), photo = card.avatar)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium)
                Text("${card.handle} · ${levelText(friend.levelValue, friend.label)}", style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
                Text(stringResource(capabilityText(friend.capability)), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                friend.invite?.let { invite ->
                    Text(
                        stringResource(if (invite.incoming) R.string.invite_incoming else R.string.invite_waiting),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (invite.incoming) MaterialTheme.colorScheme.primary else crayon.textSoft,
                    )
                }
            }
            Icon(PastelIcons.Forward, contentDescription = null, tint = crayon.textSoft)
        }
    }
}

/**
 * Friends in the Living Sky look, as on the board: the Emergency Pause on a peach card at the
 * top; requests (theirs raised with Accept / Not now, yours flat with Take back); your people as
 * rows on one card with a pill for what they may do; Blocked people; and "Add a friend".
 */
@Composable
private fun SkyFriends(
    state: FriendsUiState,
    viewModel: FriendsViewModel,
    onAddFriend: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenBlocked: () -> Unit,
    onRevokeAll: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    CrayonScreen(
        title = stringResource(R.string.friends_sky_title),
        floating = { SkyButton(stringResource(R.string.friends_add), onAddFriend, icon = SkyIcons.AddPerson) },
    ) {
        // The Emergency Pause: the one switch that stops everyone.
        SkyCard(modifier = Modifier.fillMaxWidth(), tone = SkyTone.Peach, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.remotePaused, enabled = "pause" !in state.busy, role = Role.Switch, onValueChange = viewModel::setPaused),
            ) {
                val face = SkyTone.Peach.fill()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(38.dp).skySurface(face, crayon.pressShadowFor(face), crayon.shadow, crayon.sunX * 0.5f, cornerRadius = 19.dp, depth = 3.dp, lift = 6.dp, blur = 10.dp),
                ) { Icon(SkyIcons.Shield, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(20.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pause_switch), style = SkyType.tt, color = SkyInk.onPastel)
                    Text(stringResource(if (state.remotePaused) R.string.pause_switch_on else R.string.friends_sky_pause_body), style = SkyType.sm, color = SkyInk.onPastel)
                }
                SkySwitch(state.remotePaused, enabled = "pause" !in state.busy)
            }
        }
        state.message?.let { message ->
            SkyChip(stringResource(message), tone = SkyTone.Butter, onClick = viewModel::messageShown, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        val requests = state.requests
        if (requests.incoming.isNotEmpty() || requests.outgoing.isNotEmpty()) {
            SkySectionHeading(stringResource(R.string.friends_sky_requests), badge = requests.incoming.size, inset = 2.dp)
            requests.incoming.forEach { request ->
                val busy = request.id in state.busy
                SkyCard(modifier = Modifier.fillMaxWidth(), level = CardLevel.Raised, spacing = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PersonAvatar(request.user.initial, request.user.id.hashCode(), size = 52.dp, photo = request.user.avatar)
                        Column(Modifier.weight(1f)) {
                            Text(request.user.name, style = SkyType.tt)
                            Text(request.user.handle + " " + stringResource(R.string.friends_incoming_body), style = SkyType.sb, color = crayon.textSoft)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SkyButton(stringResource(R.string.friends_accept), { viewModel.respond(request, true) }, modifier = Modifier.weight(1f), small = true, loading = busy)
                        SkyButton(stringResource(R.string.friends_not_now), { viewModel.respond(request, false) }, small = true, tone = SkyButtonTone.Quiet, enabled = !busy)
                    }
                }
            }
            requests.outgoing.forEach { request ->
                SkyCard(modifier = Modifier.fillMaxWidth(), level = CardLevel.Flat, padding = PaddingValues(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(min = 66.dp)) {
                        PersonAvatar(request.user.initial, request.user.id.hashCode(), size = 38.dp, photo = request.user.avatar)
                        Column(Modifier.weight(1f)) {
                            Text(request.user.name, style = SkyType.tt)
                            Text(stringResource(R.string.friends_outgoing_body), style = SkyType.sb, color = crayon.textSoft)
                        }
                        SkyButton(stringResource(R.string.friends_cancel_request), { viewModel.cancel(request) }, small = true, tone = SkyButtonTone.Text, loading = request.id in state.busy)
                    }
                }
            }
        }
        val friends = state.friends
        when {
            friends == null && state.loading -> WhityLoading(stringResource(R.string.friends_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
            friends == null -> StickerMessage(
                sticker = WhitySticker.OopsFacepalm,
                blob = crayon.peach,
                title = stringResource(R.string.friends_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.friends_retry),
                onAction = { viewModel.refresh() },
            )
            friends.isEmpty() -> StickerMessage(
                sticker = WhitySticker.LoveTeddyHug,
                blob = crayon.peach,
                title = stringResource(R.string.friends_empty_title),
                body = stringResource(R.string.friends_empty_body),
            )
            else -> {
                SkySectionHeading(stringResource(R.string.friends_your_people), inset = 2.dp)
                SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    friends.forEachIndexed { i, friend ->
                        val line = crayon.line
                        val card = friend.card
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind { if (i > 0) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                                .clickable(onClickLabel = stringResource(R.string.friends_open, card.name), role = Role.Button) { onOpenFriend(friend.id) }
                                .heightIn(min = 66.dp)
                                .padding(vertical = 9.dp),
                        ) {
                            PersonAvatar(card.initial, friend.id.hashCode(), size = 38.dp, photo = card.avatar)
                            Column(Modifier.weight(1f)) {
                                Text(card.name + " · " + levelName(friend.levelValue, friend.label), style = SkyType.tt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    friend.invite?.let { stringResource(if (it.incoming) R.string.invite_incoming else R.string.invite_waiting) }
                                        ?: stringResource(capabilityText(friend.capability)),
                                    style = SkyType.sb,
                                    color = if (friend.invite?.incoming == true) crayon.link else crayon.textSoft,
                                )
                            }
                            val (word, tone) = when (friend.capability) {
                                Capability.Auto -> R.string.friends_sky_auto to SkyTone.Mint
                                Capability.Ask -> R.string.friends_sky_asks to SkyTone.Butter
                                Capability.Unavailable -> R.string.friends_sky_paused to SkyTone.Lilac
                            }
                            SkyPill(stringResource(word), tone = tone)
                            Icon(SkyIcons.Next, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            SkyChip(stringResource(R.string.blocked_people), trailingIcon = SkyIcons.Next, onClick = onOpenBlocked)
            SkyChip(stringResource(R.string.pause_revoke_all), onClick = onRevokeAll)
        }
    }
}
