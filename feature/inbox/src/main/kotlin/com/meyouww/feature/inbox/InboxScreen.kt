package com.meyouww.feature.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CountBadge
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.ActivityItem
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.RelativeTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class InboxUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val items: List<ActivityItem>? = null,
    val busy: Set<String> = emptySet(),
)

private data class InboxLocal(val loading: Boolean = true, val error: AppError? = null, val busy: Set<String> = emptySet())

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val activities: ActivityRepository,
    private val friends: FriendsRepository,
) : ViewModel() {
    private val local = MutableStateFlow(InboxLocal(loading = activities.items.value == null))

    val state: StateFlow<InboxUiState> = combine(local, activities.items) { l, items ->
        InboxUiState(l.loading, l.error, items, l.busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    fun refresh() = viewModelScope.launch {
        val outcome = activities.refresh()
        local.update {
            it.copy(loading = false, error = (outcome as? Outcome.Failure)?.error?.takeIf { activities.items.value == null })
        }
        if (outcome is Outcome.Success && outcome.value.any { !it.read }) {
            // Let the "New" dots show for a moment before clearing the badge.
            delay(1_500)
            activities.markAllRead()
        }
    }

    fun answer(item: ActivityItem, accept: Boolean) {
        val subject = item.subjectId ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = it.busy + item.id) }
            val outcome = when (item.kindValue) {
                ActivityKind.FriendRequest -> friends.respondRequest(subject, accept)
                ActivityKind.RelationshipInvite -> friends.respondInvite(subject, accept)
                else -> null
            }
            if (outcome is Outcome.Success) {
                activities.markResolved(
                    subject,
                    when (outcome.value) {
                        ChangeResult.Accepted -> "accepted"
                        ChangeResult.Declined -> "declined"
                        else -> "expired"
                    },
                )
                friends.refreshFriends()
                friends.refreshRequests()
            }
            local.update { it.copy(busy = it.busy - item.id) }
        }
    }

    fun dismiss(item: ActivityItem) = viewModelScope.launch { activities.dismiss(item.id) }
}

/** The heart: everything your people did for you, with answers right here. */
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenFriend: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val items = state.items

    CrayonScreen(title = stringResource(R.string.inbox_title), onBack = onBack) {
        when {
            items == null && state.loading -> WhityLoading(stringResource(R.string.inbox_loading), Modifier.fillMaxWidth().padding(top = 48.dp), sticker = WhitySticker.Love)
            items == null -> StickerMessage(
                sticker = WhitySticker.Confused,
                title = stringResource(R.string.inbox_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.inbox_retry),
                onAction = { viewModel.refresh() },
            )
            items.isEmpty() -> StickerMessage(
                sticker = WhitySticker.Sleepy,
                title = stringResource(R.string.inbox_empty_title),
                body = stringResource(R.string.inbox_empty_body),
            )
            else -> items.forEach { item ->
                InboxRow(
                    item = item,
                    busy = item.id in state.busy,
                    onAccept = { viewModel.answer(item, true) },
                    onDecline = { viewModel.answer(item, false) },
                    onDismiss = { viewModel.dismiss(item) },
                    onOpen = item.actor?.id?.takeIf { item.kindValue != ActivityKind.FriendRequest }?.let { id -> { onOpenFriend(id) } },
                )
            }
        }
    }
}

@Composable
private fun InboxRow(
    item: ActivityItem,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val crayon = MeyouwwTheme.crayon
    val actor = item.actor
    val name = actor?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_someone)
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        fill = if (item.actionable) crayon.yellow else MaterialTheme.colorScheme.surface,
        seed = item.id.hashCode(),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(actor?.initial ?: "♥", (actor?.id ?: item.id).hashCode())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline(item, name), style = MaterialTheme.typography.titleMedium)
                detail(item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!item.read) {
                        Text(stringResource(R.string.inbox_new), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(timeText(item.createdInstant), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
                }
                resolutionText(item)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = crayon.textSoft) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.inbox_dismiss))
            }
        }
        if (item.actionable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(stringResource(R.string.inbox_accept), onClick = onAccept, loading = busy, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.inbox_not_now), onClick = onDecline, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun headline(item: ActivityItem, name: String): String {
    val level = RelationshipLevel.fromWire(item.dataString("level"))
    val label = item.dataString("label") ?: stringResource(R.string.inbox_partner_default)
    return when (item.kindValue) {
        ActivityKind.FriendRequest -> stringResource(R.string.inbox_friend_request, name)
        ActivityKind.FriendAccepted -> stringResource(R.string.inbox_friend_accepted, name)
        ActivityKind.RelationshipInvite -> if (level == RelationshipLevel.Partner) {
            stringResource(R.string.inbox_partner_invite, name)
        } else {
            stringResource(R.string.inbox_bestie_invite, name)
        }
        ActivityKind.RelationshipAccepted -> if (level == RelationshipLevel.Partner) {
            stringResource(R.string.inbox_partner_accepted, name, label)
        } else {
            stringResource(R.string.inbox_bestie_accepted, name)
        }
        ActivityKind.WallpaperRequest -> stringResource(R.string.inbox_wallpaper_request, name)
        ActivityKind.WallpaperReceived -> stringResource(R.string.inbox_wallpaper_received, name)
        ActivityKind.WallpaperApplied -> stringResource(R.string.inbox_wallpaper_applied, name)
        ActivityKind.WallpaperDeclined -> stringResource(R.string.inbox_wallpaper_declined, name)
        ActivityKind.DoodleReceived -> stringResource(R.string.inbox_doodle_received, name)
        ActivityKind.EventInvite -> stringResource(R.string.inbox_event_invite, name)
        ActivityKind.AlarmRequest -> stringResource(R.string.inbox_alarm_request, name)
        ActivityKind.Unknown -> stringResource(R.string.inbox_unknown, name)
    }
}

@Composable
private fun detail(item: ActivityItem): String? = when {
    item.kindValue == ActivityKind.RelationshipInvite && RelationshipLevel.fromWire(item.dataString("level")) == RelationshipLevel.Partner ->
        stringResource(R.string.inbox_partner_invite_label, item.dataString("label") ?: stringResource(R.string.inbox_partner_default))
    else -> null
}

@Composable
private fun resolutionText(item: ActivityItem): String? = when (item.resolution) {
    "accepted" -> stringResource(R.string.inbox_resolved_accepted)
    "declined" -> stringResource(R.string.inbox_resolved_declined)
    "expired" -> stringResource(R.string.inbox_resolved_expired)
    "cancelled" -> stringResource(R.string.inbox_resolved_cancelled)
    "applied" -> stringResource(R.string.inbox_resolved_applied)
    else -> null
}

@Composable
private fun timeText(instant: Instant?): String {
    if (instant == null) return ""
    return when (val t = RelativeTime.between(instant, Instant.now())) {
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes, t.value.toInt(), t.value.toInt())
        is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours, t.value.toInt(), t.value.toInt())
        is RelativeTime.Days -> pluralStringResource(R.plurals.time_days, t.value.toInt(), t.value.toInt())
        is RelativeTime.OnDate -> t.instant.atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

/** The heart button for the top bar, with the unread count. */
@Composable
fun InboxHeartButton(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (unread > 0) stringResource(R.string.inbox_open_unread, unread) else stringResource(R.string.inbox_open)
    Box(modifier = modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp).semantics { contentDescription = label }) {
            Icon(
                if (unread > 0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MeyouwwTheme.crayon.bubblegum.takeIf { unread > 0 } ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        CountBadge(unread, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@HiltViewModel
class InboxBadgeViewModel @Inject constructor(private val activities: ActivityRepository) : ViewModel() {
    val unread: StateFlow<Int> = activities.unread

    fun refresh() = viewModelScope.launch { activities.refreshUnread() }
}

/** The heart for the Home top bar. Refreshes its count whenever the screen resumes. */
@Composable
fun InboxHeart(onClick: () -> Unit, modifier: Modifier = Modifier, viewModel: InboxBadgeViewModel = hiltViewModel()) {
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    InboxHeartButton(unread, onClick, modifier)
}
