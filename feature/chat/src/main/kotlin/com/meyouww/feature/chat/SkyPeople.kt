package com.meyouww.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.designsystem.component.CardLevel
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.SkyBadge
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyField
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconTone
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.skyBob
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/**
 * Your people in the Living Sky look, laid out as its design board: the title on the sky with
 * the friends button; a search well; your people as bobbing coins (and "New group"); your chats
 * as cards (pinned in pink, unread raised, read ones flat); the lock line; and the pencil button
 * to find or add someone.
 */
@Composable
internal fun SkyPeopleScreen(
    conversations: List<ChatConversation>?,
    friends: List<Friend>,
    query: String,
    onQuery: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onStartChat: (String) -> Unit,
    onNewGroup: () -> Unit,
    onManageFriends: () -> Unit,
    onAddFriend: () -> Unit,
    onPin: (ChatConversation) -> Unit,
) {
    val byId = remember(friends) { friends.associateBy { it.id } }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MAX_CONTENT_WIDTH)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
        ) {
            // The bar: "Your people" and the friends button.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
            ) {
                Text(
                    stringResource(R.string.people_title),
                    style = SkyType.h1.copy(shadow = skyHalo()),
                    color = SkyInk.onSky,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                SkyIconButton(SkyIcons.AddPerson, stringResource(R.string.people_sky_friends), onManageFriends)
            }
            SkyField(
                value = query,
                onValueChange = onQuery,
                placeholder = stringResource(R.string.people_sky_search),
                leading = SkyIcons.Search,
                minHeight = 50.dp,
                corner = 26.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            val list = conversations
            val term = query.trim()
            when {
                list == null -> WhityLoading(stringResource(R.string.chat_loading), Modifier.fillMaxWidth().padding(top = 32.dp))
                else -> {
                    if (term.isEmpty()) PeopleCoinRow(list, byId, onOpenChat, onNewGroup)
                    val matches = peopleMatching(list, friends, term = term)
                    if (matches.isEmpty && term.isEmpty()) {
                        StickerMessage(
                            WhitySticker.DayWaitingAtTheDoor,
                            title = stringResource(R.string.people_empty_title),
                            body = stringResource(R.string.people_empty_body),
                            modifier = Modifier.padding(16.dp),
                        )
                    } else if (matches.isEmpty) {
                        Text(
                            stringResource(R.string.people_no_match),
                            style = SkyType.sm.copy(shadow = skyHalo()),
                            color = SkyInk.onSkySoft,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        )
                    }
                    Column(Modifier.padding(top = 4.dp)) {
                        matches.chats.forEach { (conversation, friend) ->
                            ChatCard(conversation, friend, onClick = { onOpenChat(conversation.id) }, onPin = { onPin(conversation) })
                        }
                        matches.newFriends.forEach { friend -> NewFriendCard(friend) { onStartChat(friend.id) } }
                    }
                }
            }
            // The lock line, under the chats.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp),
            ) {
                Icon(SkyIcons.Lock, contentDescription = null, tint = SkyInk.onSkySoft, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.people_sky_e2ee),
                    style = SkyType.sm.copy(shadow = skyHalo()),
                    color = SkyInk.onSkySoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // The pencil: find or add a person.
        SkyIconButton(
            SkyIcons.Pencil,
            stringResource(R.string.people_sky_find),
            onAddFriend,
            size = SkyIconSize.Large,
            tone = SkyIconTone.Accent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
        )
    }
}

/** Your first few people as 60 dp coins on the sky, bobbing; unread ones wear the accent ring. "New group" ends the row. */
@Composable
private fun PeopleCoinRow(
    conversations: List<ChatConversation>,
    byId: Map<String, Friend>,
    onOpenChat: (String) -> Unit,
    onNewGroup: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Row(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp),
    ) {
        conversations.take(6).forEachIndexed { i, conversation ->
            val friend = byId[conversation.peerId]
            val name = conversationName(conversation, friend)
            CoinWithName(name, i, onClickLabel = stringResource(R.string.chat_list_open, name), onClick = { onOpenChat(conversation.id) }) {
                Box(
                    Modifier.drawBehind {
                        if (conversation.unread > 0) {
                            val w = 3.dp.toPx()
                            drawCircle(crayon.red, radius = size.minDimension / 2 + 3.dp.toPx() + w / 2, style = Stroke(w))
                        }
                    },
                ) {
                    if (conversation.isGroup) {
                        IconCoin(SkyIcons.Group, SkyTone.Butter.fill(), 60.dp)
                    } else {
                        PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 60.dp, photo = friend?.card?.avatar)
                    }
                }
            }
        }
        CoinWithName(stringResource(R.string.people_sky_new_group), conversations.size.coerceAtMost(6), onClickLabel = null, onClick = onNewGroup) {
            IconCoin(SkyIcons.Plus, crayon.card, 60.dp)
        }
    }
}

@Composable
private fun CoinWithName(name: String, index: Int, onClickLabel: String?, onClick: () -> Unit, coin: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(64.dp)
            .skyBob(index)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
            .padding(top = 6.dp),
    ) {
        coin()
        Text(
            name,
            style = SkyType.cap.copy(shadow = skyHalo()),
            color = SkyInk.onSky,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round 3D coin with an icon on it (.a3 with an icon). */
@Composable
internal fun IconCoin(icon: ImageVector, face: Color, size: Dp) {
    val crayon = MeyouwwTheme.crayon
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .skySurface(face, crayon.pressShadowFor(face), crayon.shadow, crayon.sunX * 0.5f, cornerRadius = size / 2, depth = 4.dp, lift = 6.dp, blur = 10.dp)
            .clearAndSetSemantics { },
    ) {
        Icon(icon, contentDescription = null, tint = if (face == crayon.card) MaterialTheme.colorScheme.onSurface else SkyInk.onPastel, modifier = Modifier.size(size * 0.42f))
    }
}

/** The name shown for a chat: my nickname for them, their name, or the group's. */
@Composable
private fun conversationName(conversation: ChatConversation, friend: Friend?): String =
    conversation.nickname?.ifBlank { null }
        ?: friend?.card?.name?.ifBlank { null }
        ?: conversation.peerName.ifBlank { stringResource(if (conversation.isGroup) R.string.group_default_name else R.string.chat_friend) }

/**
 * One chat as a card (.cv): coin, name with the relationship pill, the last message, and on the
 * right the time (with a pin) over the unread count. Pinned chats are pink, unread ones float
 * higher, read ones lie flat. Press and hold to pin or unpin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatCard(conversation: ChatConversation, friend: Friend?, onClick: () -> Unit, onPin: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val name = conversationName(conversation, friend)
    var menu by remember { mutableStateOf(false) }
    val pinLabel = stringResource(if (conversation.pinned) R.string.chat_unpin else R.string.chat_pin)
    val unread = conversation.unread > 0
    val pink = conversation.pinned && unread
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)) {
        SkyCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.chat_list_open, name),
                    onLongClickLabel = pinLabel,
                    onLongClick = { menu = true },
                    onClick = onClick,
                ),
            tone = if (pink) SkyTone.Pink else null,
            level = when {
                unread && !conversation.muted -> CardLevel.Raised
                unread || conversation.pinned -> CardLevel.Normal
                else -> CardLevel.Flat
            },
            padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            corner = 26.dp,
        ) {
            val ink = if (pink) SkyInk.onPastel else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (conversation.isGroup) {
                    IconCoin(SkyIcons.Group, SkyTone.Butter.fill(), 52.dp)
                } else {
                    PersonAvatar(name.take(1).uppercase(), conversation.peerId.hashCode(), size = 52.dp, photo = friend?.card?.avatar)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = SkyType.tt, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (friend != null && friend.levelValue != RelationshipLevel.Friend) {
                            SkyPill(skyLevelLabel(friend), tone = if (pink) null else skyLevelTone(friend.levelValue))
                        }
                        if (conversation.muted) {
                            Icon(SkyIcons.Mute, contentDescription = stringResource(R.string.people_sky_muted), tint = crayon.textSoft, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(
                        conversationPreview(conversation.lastText, conversation.lastKind, conversation.lastFromMe),
                        style = SkyType.sb,
                        color = if (pink) SkyInk.berryOnPink else crayon.textSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (conversation.pinned) {
                            Icon(SkyIcons.Pin, contentDescription = stringResource(R.string.people_sky_pinned), tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
                        }
                        if (conversation.lastKind != null) {
                            Text(whenText(conversation.lastAt), style = SkyType.cap, color = ink.copy(alpha = 0.75f), maxLines = 1)
                        }
                    }
                    if (unread) {
                        if (conversation.muted) MutedBadge(conversation.unread) else SkyBadge(conversation.unread)
                    }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(pinLabel) }, onClick = {
                menu = false
                onPin()
            })
        }
    }
}

/** A muted chat's count: grey and flat, so it doesn't call for attention. */
@Composable
private fun MutedBadge(count: Int) {
    val crayon = MeyouwwTheme.crayon
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(min = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .drawBehind { drawRect(crayon.edge) }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = SkyType.pill, color = Color.White)
    }
}

/** A friend you haven't chatted with yet: a flat card that says hi. */
@Composable
private fun NewFriendCard(friend: Friend, onClick: () -> Unit) {
    val card = friend.card
    SkyCard(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp).fillMaxWidth(),
        level = CardLevel.Flat,
        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        corner = 26.dp,
        onClick = onClick,
        onClickLabel = stringResource(R.string.chat_list_open, card.name),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonAvatar(card.initial, friend.id.hashCode(), size = 52.dp, photo = card.avatar)
            Column(Modifier.weight(1f)) {
                Text(card.name, style = SkyType.tt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.chat_list_empty_preview), style = SkyType.sb, color = MeyouwwTheme.crayon.textSoft)
            }
        }
    }
}

/** "Girlfriend", "Best Friend": the relationship pill's words (without the emoji the other looks add). */
@Composable
internal fun skyLevelLabel(friend: Friend): String = when (friend.levelValue) {
    RelationshipLevel.Friend -> stringResource(R.string.chat_level_friend)
    RelationshipLevel.BestFriend -> stringResource(R.string.people_sky_best_friend)
    RelationshipLevel.Partner -> friend.label?.ifBlank { null } ?: stringResource(R.string.chat_level_partner_default)
}

internal fun skyLevelTone(level: RelationshipLevel): SkyTone = when (level) {
    RelationshipLevel.Partner -> SkyTone.Pink
    RelationshipLevel.BestFriend -> SkyTone.Lilac
    RelationshipLevel.Friend -> SkyTone.Mint
}

/** The board's time on a chat card: the time today, the weekday this week, else the date. */
@Composable
private fun whenText(at: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val today = LocalDate.now(zone)
    val day = time.toLocalDate()
    return when {
        day == today -> time.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        day.isAfter(today.minusDays(7)) -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        else -> day.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
}
