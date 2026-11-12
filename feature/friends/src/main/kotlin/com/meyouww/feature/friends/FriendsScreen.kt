package com.meyouww.feature.friends

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

    CrayonScreen(title = stringResource(R.string.friends_title)) {
        CrayonButton(text = stringResource(R.string.friends_add), onClick = onAddFriend, modifier = Modifier.fillMaxWidth())

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
    CrayonCard(modifier = Modifier.fillMaxWidth(), fill = if (incoming) crayon.peach else MaterialTheme.colorScheme.surface, seed = request.id.hashCode()) {
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
