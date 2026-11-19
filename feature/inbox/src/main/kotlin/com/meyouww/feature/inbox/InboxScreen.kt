package com.meyouww.feature.inbox

import com.meyouww.core.designsystem.component.CardLevel
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkySectionHeading
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyButtonTone
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SectionTitle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.alarm.AlarmSync
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.messageRes
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.wallpaper.WallpaperRepository
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
import androidx.compose.foundation.layout.offset
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.model.ActivityItem
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.RelationshipLevel
import com.meyouww.core.model.RelativeTime
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.notifications.push.WallpaperWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val preview: WallpaperPreview? = null,
)

/** A wallpaper request opened for a closer look before answering. */
data class WallpaperPreview(
    val item: ActivityItem,
    val image: ImageBitmap? = null,
    val aspect: Float = 20f / 9f,
    val target: WallpaperTarget = WallpaperTarget.Lock,
    val failed: Boolean = false,
)

private data class InboxLocal(
    val loading: Boolean = true,
    val error: AppError? = null,
    val busy: Set<String> = emptySet(),
    val preview: WallpaperPreview? = null,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val activities: ActivityRepository,
    private val friends: FriendsRepository,
    private val wallpapers: WallpaperRepository,
    private val alarms: AlarmRepository,
    private val alarmSync: AlarmSync,
    private val calendar: CalendarRepository,
) : ViewModel() {
    private val local = MutableStateFlow(InboxLocal(loading = activities.items.value == null))

    val state: StateFlow<InboxUiState> = combine(local, activities.items) { l, items ->
        InboxUiState(l.loading, l.error, items, l.busy, l.preview)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InboxUiState())

    /** Fetches the picture (same checks as applying) so it can be looked at first. */
    fun preview(item: ActivityItem) {
        val id = item.subjectId ?: return
        local.update { it.copy(preview = WallpaperPreview(item)) }
        viewModelScope.launch {
            val reply = (wallpapers.fetch(id) as? Outcome.Success)?.value
            val cmd = reply?.command
            val bytes = cmd?.let { (wallpapers.download(it.storagePath) as? Outcome.Success)?.value }
            val image = if (cmd != null && bytes != null && WallpaperRepository.sha256(bytes) == cmd.sha256) {
                withContext(Dispatchers.Default) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = if (cmd.width > 1200) 2 else 1 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                }
            } else {
                null
            }
            local.update { l ->
                val current = l.preview ?: return@update l
                if (current.item.id != item.id) return@update l
                l.copy(
                    preview = current.copy(
                        image = image,
                        aspect = cmd?.let { it.height.toFloat() / it.width } ?: current.aspect,
                        target = cmd?.targetValue ?: current.target,
                        failed = image == null,
                    ),
                )
            }
        }
    }

    fun closePreview() = local.update { it.copy(preview = null) }

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
        local.update { it.copy(preview = null) }
        if (item.kindValue == ActivityKind.WallpaperRequest) {
            answerWallpaper(item, subject, accept)
            return
        }
        if (item.kindValue == ActivityKind.AlarmRequest || item.kindValue == ActivityKind.AlarmAdded) {
            answerAlarm(item, subject, accept)
            return
        }
        if (item.kindValue == ActivityKind.EventInvite || item.kindValue == ActivityKind.EventAdded) {
            answerEvent(item, subject, accept)
            return
        }
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

    /** Accept or decline an alarm request, or Undo an automatic one; then this phone re-arms its alarms. */
    private fun answerAlarm(item: ActivityItem, alarmId: String, accept: Boolean) = viewModelScope.launch {
        local.update { it.copy(busy = it.busy + item.id) }
        if (alarms.respond(alarmId, accept) is Outcome.Success) {
            activities.markResolved(alarmId, if (accept) "accepted" else "declined")
            alarmSync.syncNow()
        }
        local.update { it.copy(busy = it.busy - item.id) }
    }

    private fun answerEvent(item: ActivityItem, eventId: String, going: Boolean) = viewModelScope.launch {
        local.update { it.copy(busy = it.busy + item.id) }
        if (calendar.respond(eventId, going) is Outcome.Success) {
            activities.markResolved(eventId, if (going) "accepted" else "declined")
            alarmSync.syncCalendar()
        }
        local.update { it.copy(busy = it.busy - item.id) }
    }

    fun dismiss(item: ActivityItem) = viewModelScope.launch { activities.dismiss(item.id) }

    /**
     * Put it on runs in the background (it keeps going if the app closes), on the screen the
     * sender picked. Not now is reported right away; the picture leaves the server either way.
     */
    private fun answerWallpaper(item: ActivityItem, commandId: String, accept: Boolean) {
        if (accept) {
            local.update { it.copy(preview = null) }
            WallpaperWorker.enqueue(context, commandId, approved = true)
            activities.markResolved(commandId, "applied")
            return
        }
        viewModelScope.launch {
            local.update { it.copy(busy = it.busy + item.id) }
            (wallpapers.fetch(commandId) as? Outcome.Success)?.value?.command?.storagePath?.let { wallpapers.deleteMedia(it) }
            if (wallpapers.report(commandId, "declined") is Outcome.Success) activities.markResolved(commandId, "declined")
            local.update { it.copy(busy = it.busy - item.id) }
        }
    }
}

/** The heart: everything your people did for you, with answers right here. */
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val items = state.items
    state.preview?.let { p ->
        PreviewDialog(
            preview = p,
            onApply = { viewModel.answer(p.item, true) },
            onDecline = { viewModel.answer(p.item, false) },
            onClose = viewModel::closePreview,
        )
    }

    CrayonScreen(
        title = stringResource(R.string.inbox_title),
        onBack = onBack,
        trailing = {
            if (MeyouwwTheme.crayon.living) {
                SkyChip(stringResource(R.string.inbox_history), icon = SkyIcons.Clock, onClick = onOpenHistory)
            } else {
                TextButton(onClick = onOpenHistory) { Text(stringResource(R.string.inbox_history)) }
            }
        },
    ) {
        when {
            items == null && state.loading -> WhityLoading(stringResource(R.string.inbox_loading), Modifier.fillMaxWidth().padding(top = 48.dp), sticker = WhitySticker.Love)
            items == null -> StickerMessage(
                sticker = WhitySticker.OopsFacepalm,
                blob = MeyouwwTheme.crayon.peach,
                title = stringResource(R.string.inbox_error_title),
                body = stringResource((state.error ?: AppError.Unknown).messageRes()),
                actionLabel = stringResource(R.string.inbox_retry),
                onAction = { viewModel.refresh() },
            )
            items.isEmpty() -> StickerMessage(
                sticker = WhitySticker.LoveBlowingAKiss,
                title = stringResource(R.string.inbox_empty_title),
                body = stringResource(R.string.inbox_empty_body),
            )
            else -> {
                @Composable
                fun row(item: ActivityItem, level: CardLevel) = InboxRow(
                    item = item,
                    busy = item.id in state.busy,
                    onAccept = { viewModel.answer(item, true) },
                    onDecline = { viewModel.answer(item, false) },
                    onPreview = { viewModel.preview(item) },
                    onDismiss = { viewModel.dismiss(item) },
                    onOpen = item.actor?.id?.takeIf { item.kindValue != ActivityKind.FriendRequest }?.let { id -> { onOpenFriend(id) } },
                    level = level,
                )
                if (MeyouwwTheme.crayon.living) {
                    // Living Sky, as on the board: what waits for an answer is a deck of cards (tap
                    // one to bring it forward); what's done is a list of rows on one card below.
                    val waiting = items.filter { it.actionable }
                    val earlier = items.filterNot { it.actionable }
                    if (waiting.isNotEmpty()) {
                        SkySectionHeading(stringResource(R.string.inbox_sky_new), caption = stringResource(R.string.inbox_sky_tap_hint), badge = waiting.size, inset = 2.dp)
                        var front by rememberSaveable { mutableStateOf<String?>(null) }
                        val top = waiting.firstOrNull { it.id == front } ?: waiting.first()
                        val behind = waiting.filter { it.id != top.id }
                        Column(verticalArrangement = Arrangement.spacedBy((-14).dp)) {
                            Box(Modifier.zIndex(behind.size + 1f)) {
                                SkyInboxFront(
                                    item = top,
                                    busy = top.id in state.busy,
                                    onAccept = { viewModel.answer(top, true) },
                                    onDecline = { viewModel.answer(top, false) },
                                    onPreview = { viewModel.preview(top) },
                                    onDismiss = { viewModel.dismiss(top) },
                                    onOpen = top.actor?.id?.takeIf { top.kindValue != ActivityKind.FriendRequest }?.let { id -> { onOpenFriend(id) } },
                                )
                            }
                            behind.forEachIndexed { i, item ->
                                DeckCard(item, depth = i + 1, onClick = { front = item.id }, modifier = Modifier.zIndex((behind.size - i).toFloat()))
                            }
                        }
                    }
                    if (earlier.isNotEmpty()) {
                        if (waiting.isNotEmpty()) SkySectionHeading(stringResource(R.string.inbox_sky_earlier), inset = 2.dp, modifier = Modifier.padding(top = 4.dp))
                        SkyCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                            earlier.forEachIndexed { i, item ->
                                SkyInboxEarlierRow(
                                    item = item,
                                    first = i == 0,
                                    onDismiss = { viewModel.dismiss(item) },
                                    onOpen = item.actor?.id?.takeIf { item.kindValue != ActivityKind.FriendRequest }?.let { id -> { onOpenFriend(id) } },
                                )
                            }
                        }
                    }
                } else {
                    items.forEach { row(it, CardLevel.Normal) }
                }
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
    onPreview: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
    level: CardLevel = CardLevel.Normal,
) {
    val crayon = MeyouwwTheme.crayon
    val actor = item.actor
    val name = actor?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_someone)
    CrayonCard(
        modifier = Modifier.fillMaxWidth(),
        fill = if (item.actionable) crayon.bubblegum else MaterialTheme.colorScheme.surface,
        seed = item.id.hashCode(),
        onClick = onOpen,
        level = level,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(actor?.initial ?: "♥", (actor?.id ?: item.id).hashCode(), photo = actor?.avatar)
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
                Icon(PastelIcons.Close, contentDescription = stringResource(R.string.inbox_dismiss))
            }
        }
        if (item.actionable && item.kindValue == ActivityKind.WallpaperRequest) {
            CrayonButton(stringResource(R.string.inbox_preview), onClick = onPreview, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(stringResource(R.string.inbox_apply), onClick = onAccept, loading = busy, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.inbox_not_now), onClick = onDecline, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
            }
        } else if (item.actionable && item.kindValue == ActivityKind.AlarmAdded) {
            CrayonButton(stringResource(R.string.inbox_alarm_undo), onClick = onDecline, kind = ButtonKind.Quiet, loading = busy, modifier = Modifier.fillMaxWidth())
        } else if (item.actionable && item.kindValue == ActivityKind.EventAdded) {
            CrayonButton(stringResource(R.string.inbox_event_remove), onClick = onDecline, kind = ButtonKind.Quiet, loading = busy, modifier = Modifier.fillMaxWidth())
        } else if (item.actionable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonButton(stringResource(R.string.inbox_accept), onClick = onAccept, loading = busy, modifier = Modifier.weight(1f))
                CrayonButton(stringResource(R.string.inbox_not_now), onClick = onDecline, kind = ButtonKind.Quiet, enabled = !busy, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Living Sky: a card waiting behind the front one in the deck, a little narrower the further back it is. */
@Composable
private fun DeckCard(item: ActivityItem, depth: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val crayon = MeyouwwTheme.crayon
    val actor = item.actor
    val name = actor?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_someone)
    CrayonCard(
        modifier = modifier.fillMaxWidth().padding(horizontal = (10 * depth.coerceAtMost(3)).dp),
        fill = if (crayon.living) MaterialTheme.colorScheme.surface else crayon.bubblegum,
        seed = item.id.hashCode(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.inbox_sky_bring),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PersonAvatar(actor?.initial ?: "♥", (actor?.id ?: item.id).hashCode(), size = 38.dp, photo = actor?.avatar)
            Text(headline(item, name), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(timeText(item.createdInstant), style = MaterialTheme.typography.labelSmall, color = crayon.textSoft)
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
        ActivityKind.EventAdded -> stringResource(R.string.inbox_event_added, name)
        ActivityKind.EventAccepted -> stringResource(R.string.inbox_event_accepted, name)
        ActivityKind.EventDeclined -> stringResource(R.string.inbox_event_declined, name)
        ActivityKind.EventCancelled -> stringResource(R.string.inbox_event_cancelled, name)
        ActivityKind.AlarmRequest -> stringResource(R.string.inbox_alarm_request, name)
        ActivityKind.AlarmAdded -> stringResource(R.string.inbox_alarm_added, name)
        ActivityKind.AlarmAccepted -> stringResource(R.string.inbox_alarm_accepted, name)
        ActivityKind.AlarmDeclined -> stringResource(R.string.inbox_alarm_declined, name)
        ActivityKind.Unknown -> stringResource(R.string.inbox_unknown, name)
    }
}

@Composable
private fun detail(item: ActivityItem): String? = when {
    item.kindValue == ActivityKind.RelationshipInvite && RelationshipLevel.fromWire(item.dataString("level")) == RelationshipLevel.Partner ->
        stringResource(R.string.inbox_partner_invite_label, item.dataString("label") ?: stringResource(R.string.inbox_partner_default))
    item.kindValue in EVENT_KINDS ->
        listOfNotNull(
            listOfNotNull(item.dataString("emoji"), item.dataString("title")).joinToString(" ").ifEmpty { null },
            listOfNotNull(item.dataString("date"), item.dataString("time")).joinToString(" ").ifEmpty { null },
        ).joinToString(" · ").ifEmpty { null }
    item.kindValue in ALARM_KINDS ->
        listOfNotNull(item.dataString("title"), item.dataString("local_time")?.let { stringResource(R.string.inbox_alarm_at, it) })
            .joinToString(" · ").ifEmpty { null }
    else -> null
}

private val EVENT_KINDS = setOf(
    ActivityKind.EventInvite, ActivityKind.EventAdded, ActivityKind.EventAccepted, ActivityKind.EventDeclined, ActivityKind.EventCancelled,
)

private val ALARM_KINDS = setOf(ActivityKind.AlarmRequest, ActivityKind.AlarmAdded, ActivityKind.AlarmAccepted, ActivityKind.AlarmDeclined)

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

@Composable
private fun PreviewDialog(preview: WallpaperPreview, onApply: () -> Unit, onDecline: () -> Unit, onClose: () -> Unit) {
    val name = preview.item.actor?.name ?: stringResource(R.string.inbox_someone)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.inbox_preview_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.inbox_preview_target, targetText(preview.target)))
                val image = preview.image
                when {
                    image != null -> Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.inbox_preview_description, name),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .aspectRatio(1f / preview.aspect.coerceIn(0.5f, 3f))
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    preview.failed -> Text(stringResource(R.string.inbox_preview_failed))
                    else -> WhityLoading(stringResource(R.string.inbox_preview_loading))
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply, enabled = preview.image != null) { Text(stringResource(R.string.inbox_apply)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.inbox_not_now)) } },
    )
}

@Composable
internal fun targetText(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.Home -> stringResource(R.string.target_home)
    WallpaperTarget.Lock -> stringResource(R.string.target_lock)
    WallpaperTarget.Both -> stringResource(R.string.target_both)
}

/** The heart button for the top bar, with the unread count. */
@Composable
fun InboxHeartButton(unread: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (unread > 0) stringResource(R.string.inbox_open_unread, unread) else stringResource(R.string.inbox_open)
    val crayon = MeyouwwTheme.crayon
    if (crayon.living) {
        // Living Sky: the board's round 3D button with an accent heart and the count on its corner.
        SkyIconButton(SkyIcons.Heart, label, onClick, modifier, badge = unread, tint = crayon.red)
        return
    }
    Box(modifier = modifier) {
        // A white sticker circle with a berry heart: filled when something new is waiting.
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .crayonSurface(
                    fill = MaterialTheme.colorScheme.surface,
                    outline = crayon.outline,
                    seed = 482,
                    cornerRadius = 24.dp,
                    strokeWidth = 1.dp,
                    shadow = crayon.shadow,
                    shadowOffsetX = 0.dp,
                    shadowOffsetY = 3.dp,
                )
                .semantics { contentDescription = label },
        ) {
            Icon(
                if (unread > 0) Icons.Filled.Favorite else PastelIcons.Heart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        CountBadge(unread, modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
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

/**
 * Living Sky: the front card of the deck, as on the board. Their coin, what they did and when,
 * and the answers: Put it on / Preview / Not now for a wallpaper, Add it / No thanks for the
 * rest. Press and hold to clear it away.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SkyInboxFront(
    item: ActivityItem,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPreview: () -> Unit,
    onDismiss: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val crayon = MeyouwwTheme.crayon
    val actor = item.actor
    val name = actor?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_someone)
    var menu by remember { mutableStateOf(false) }
    val dismissLabel = stringResource(R.string.inbox_dismiss)
    Box {
        SkyCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .combinedClickable(
                    onClick = { onOpen?.invoke() },
                    onLongClick = { menu = true },
                    onLongClickLabel = dismissLabel,
                )
                .semantics { customActions = listOf(CustomAccessibilityAction(dismissLabel) { onDismiss(); true }) },
            level = CardLevel.Raised,
            spacing = 12.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PersonAvatar(actor?.initial ?: "♥", (actor?.id ?: item.id).hashCode(), size = 52.dp, photo = actor?.avatar)
                Column(Modifier.weight(1f)) {
                    Text(headline(item, name), style = SkyType.tt)
                    Text(
                        listOfNotNull(detail(item), timeText(item.createdInstant).ifEmpty { null }).joinToString(" · "),
                        style = SkyType.sb,
                        color = crayon.textSoft,
                    )
                }
            }
            when {
                item.kindValue == ActivityKind.WallpaperRequest -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkyButton(stringResource(R.string.inbox_apply), onAccept, small = true, loading = busy)
                    SkyButton(stringResource(R.string.inbox_preview), onPreview, small = true, tone = SkyButtonTone.Pink, enabled = !busy)
                    SkyButton(stringResource(R.string.inbox_not_now), onDecline, small = true, tone = SkyButtonTone.Text, enabled = !busy)
                }
                item.kindValue == ActivityKind.AlarmAdded ->
                    SkyButton(stringResource(R.string.inbox_alarm_undo), onDecline, small = true, tone = SkyButtonTone.Quiet, icon = SkyIcons.Undo, loading = busy)
                item.kindValue == ActivityKind.EventAdded ->
                    SkyButton(stringResource(R.string.inbox_event_remove), onDecline, small = true, tone = SkyButtonTone.Quiet, loading = busy)
                else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkyButton(stringResource(R.string.inbox_accept), onAccept, small = true, loading = busy)
                    SkyButton(stringResource(R.string.inbox_not_now), onDecline, small = true, tone = SkyButtonTone.Text, enabled = !busy)
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(dismissLabel) }, onClick = {
                menu = false
                onDismiss()
            })
        }
    }
}

/** Living Sky: one thing that's done, as a row on the "Earlier" card: a coin for its kind, what happened, how it ended and when. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SkyInboxEarlierRow(item: ActivityItem, first: Boolean, onDismiss: () -> Unit, onOpen: (() -> Unit)?) {
    val crayon = MeyouwwTheme.crayon
    val name = item.actor?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.inbox_someone)
    var menu by remember { mutableStateOf(false) }
    val dismissLabel = stringResource(R.string.inbox_dismiss)
    val (icon, tone) = when (item.kindValue) {
        ActivityKind.WallpaperRequest, ActivityKind.WallpaperReceived, ActivityKind.WallpaperApplied, ActivityKind.WallpaperDeclined -> SkyIcons.Wallpaper to SkyTone.Lilac
        ActivityKind.DoodleReceived -> SkyIcons.Pencil to SkyTone.Pink
        ActivityKind.EventInvite, ActivityKind.EventAdded, ActivityKind.EventAccepted, ActivityKind.EventDeclined, ActivityKind.EventCancelled -> SkyIcons.Calendar to SkyTone.Butter
        ActivityKind.AlarmRequest, ActivityKind.AlarmAdded, ActivityKind.AlarmAccepted, ActivityKind.AlarmDeclined -> SkyIcons.Alarm to SkyTone.Peach
        ActivityKind.FriendRequest, ActivityKind.FriendAccepted -> SkyIcons.AddPerson to SkyTone.Mint
        else -> SkyIcons.Heart to SkyTone.Pink
    }
    val line = crayon.line
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { if (!first) drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .combinedClickable(
                    onClick = { onOpen?.invoke() },
                    onLongClick = { menu = true },
                    onLongClickLabel = dismissLabel,
                )
                .semantics { customActions = listOf(CustomAccessibilityAction(dismissLabel) { onDismiss(); true }) }
                .heightIn(min = 66.dp)
                .padding(vertical = 9.dp),
        ) {
            val face = tone.fill()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .skySurface(face, crayon.pressShadowFor(face), crayon.shadow, crayon.sunX * 0.5f, cornerRadius = 19.dp, depth = 3.dp, lift = 6.dp, blur = 10.dp),
            ) { Icon(icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(headline(item, name), style = SkyType.tt)
                Text(
                    listOfNotNull(resolutionText(item), timeText(item.createdInstant).ifEmpty { null }).joinToString(" · "),
                    style = SkyType.sb,
                    color = crayon.textSoft,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(dismissLabel) }, onClick = {
                menu = false
                onDismiss()
            })
        }
    }
}
