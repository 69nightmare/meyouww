package com.meyouww.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.component.PersonAvatar
import com.meyouww.core.designsystem.component.SkyCard
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyField
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyPill
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhityPacks
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.skyGlass
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Friend
import com.meyouww.core.model.RelationshipLevel

/*
 * The chat in the Living Sky look, as on its design boards: a glass header, glass day chips,
 * round bubbles (yours pink, theirs the card) with one tight corner, a glass composer, and the
 * "Send Riya something" sheet with its tiles and stickers.
 */

/** The board's chat header (.hd): back, their coin with the online dot, name and relationship, "tap for more", search. */
@Composable
internal fun SkyChatHeader(
    name: String,
    peer: Friend?,
    peerId: String?,
    muted: Boolean,
    online: Boolean,
    status: String?,
    groupSubtitle: String?,
    onBack: () -> Unit,
    onSheet: () -> Unit,
    onSearch: () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 6.dp)
            .skyGlass(36.dp, crayon.card, crayon.hardShadow, crayon.shadow, crayon.sunX)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        SkyIconButton(SkyIcons.Back, stringResource(R.string.chat_back), onBack, size = SkyIconSize.Small)
        // Their coin, name and relationship: tap for the relationship, profile and chat options.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .clickable(enabled = peerId != null, onClickLabel = stringResource(R.string.chat_open_menu), role = Role.Button, onClick = onSheet),
        ) {
            Box {
                if (groupSubtitle != null) {
                    IconCoin(SkyIcons.Group, SkyTone.Butter.fill(), 44.dp)
                } else {
                    PersonAvatar(name.take(1).uppercase(), (peerId ?: name).hashCode(), size = 44.dp, photo = peer?.avatar)
                }
                if (online) OnlineDot(Modifier.align(Alignment.BottomEnd))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        name,
                        style = SkyType.tt,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).semantics { heading() },
                    )
                    if (peer != null && groupSubtitle == null) SkyPill(skyLevelLabel(peer), tone = skyLevelTone(peer.levelValue))
                }
                val tap = stringResource(if (muted) R.string.chat_sky_muted_tap else R.string.chat_sky_tap)
                Text(
                    listOfNotNull(groupSubtitle ?: status, tap).joinToString(" · "),
                    style = SkyType.sb,
                    color = crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        SkyIconButton(SkyIcons.Search, stringResource(R.string.chat_search), onSearch, size = SkyIconSize.Small)
    }
}

/** The green "online" dot on a coin's corner, ringed in the card colour. */
@Composable
internal fun OnlineDot(modifier: Modifier = Modifier) {
    val crayon = MeyouwwTheme.crayon
    Box(
        modifier
            .size(14.dp)
            .background(crayon.card, CircleShape)
            .padding(2.5.dp)
            .background(Color(0xFF2FA866), CircleShape)
            .clearAndSetSemantics { },
    )
}

/** The board's glass day chip (.dchip): "Today", "Only you and Riya can read this chat", group notes. */
@Composable
internal fun SkyDayChip(text: String, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    val crayon = MeyouwwTheme.crayon
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .padding(bottom = 2.dp)
                .skySurface(crayon.card.copy(alpha = 0.86f), crayon.hardShadow, Color.Transparent, 0f, cornerRadius = 999.dp, depth = 2.dp, belly = false)
                .clip(RoundedCornerShape(999.dp))
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.size(14.dp))
            Text(text, style = SkyType.pill.copy(lineHeight = 18.sp), color = crayon.textSoft, textAlign = TextAlign.Center)
        }
    }
}

/** A bubble's face (.bb): yours pink, theirs the card, round with one tight corner at the bottom on your side. */
@Composable
internal fun Modifier.skyBubble(mine: Boolean): Modifier {
    val crayon = MeyouwwTheme.crayon
    val fill = if (mine) SkyTone.Pink.fill() else crayon.card
    val edge = if (mine) crayon.pressShadowFor(fill) else crayon.hardShadow
    return this.skySurface(
        fill = fill,
        edge = edge,
        shadow = crayon.shadow,
        sunX = crayon.sunX * 0.5f,
        cornerRadius = 22.dp,
        depth = 3.dp,
        lift = 8.dp,
        blur = 14.dp,
        belly = false,
        bottomStart = if (mine) null else 8.dp,
        bottomEnd = if (mine) 8.dp else null,
    )
}

/** Text colours in a bubble: on your pink, the pastel ink; on theirs, the card's. */
@Composable
internal fun skyBubbleInk(mine: Boolean): Color = if (mine) SkyInk.onPastel else MaterialTheme.colorScheme.onSurface

/** The meta line's colour (.mt): the board's soft cocoa on your pink, the card's soft text on theirs. */
@Composable
internal fun skyMetaInk(mine: Boolean): Color = if (mine) Color(0xFF6B5560) else MeyouwwTheme.crayon.textSoft

/** A quoted message inside a bubble (.qt): a berry bar on the left, who wrote it in bold, and what it said. */
@Composable
internal fun SkyQuote(who: String, text: String, ink: Color) {
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.5f))
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(SkyInk.berryOnPink))
        Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(who, style = SkyType.pill.copy(lineHeight = 16.sp), color = SkyInk.berryOnPink, maxLines = 1)
            Text(text, style = SkyType.body.copy(fontSize = 14.sp, lineHeight = 19.sp), color = if (ink == SkyInk.onPastel) ink else Color(0xFF33263A), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** They're typing (.ty): three dots bobbing in one of their bubbles. */
@Composable
internal fun SkyTypingBubble(label: String) {
    val reducedMotion = LocalReducedMotion.current
    val soft = MeyouwwTheme.crayon.textSoft
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .skyBubble(mine = false)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) { contentDescription = label },
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            listOf(0, 350, 700).forEach { delay ->
                val y = if (reducedMotion) {
                    0f
                } else {
                    transition.animateFloat(
                        0f,
                        -4f,
                        infiniteRepeatable(tween(550, delayMillis = 0), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(delay)),
                        label = "dot",
                    ).value
                }
                Box(Modifier.size(8.dp).graphicsLayer { translationY = y.dp.toPx() }.background(soft, CircleShape))
            }
        }
    }
}

/** A wallpaper in the chat, as on the board: the picture in a bubble, then when and how it went. */
@Composable
internal fun SkyWallpaperBubble(mine: Boolean, picture: ImageBitmap?, title: String, meta: String, onOpen: (() -> Unit)?) {
    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .skyBubble(mine)
                .clip(RoundedCornerShape(22.dp))
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = stringResource(R.string.chat_wp_view), role = Role.Button, onClick = onOpen) else Modifier)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 196.dp, height = 112.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SkyTone.Blue.fill()),
            ) {
                if (picture != null) {
                    Image(picture, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text("🎨", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = title })
                }
            }
            Text(
                meta,
                style = SkyType.pill.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, lineHeight = 16.sp),
                color = skyMetaInk(mine),
                modifier = Modifier.align(Alignment.End).padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

/** The board's composer (.cmp): a glass capsule with the pink +, the message well (stickers inside it), and the round mic or send. */
@Composable
internal fun SkyComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onFocused: () -> Unit,
    trayOpen: Boolean,
    onPlus: () -> Unit,
    onStickers: () -> Unit,
    action: @Composable () -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val hint = stringResource(R.string.chat_message_hint)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .skyGlass(36.dp, crayon.card, crayon.hardShadow, crayon.shadow, crayon.sunX)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 3.dp),
    ) {
        SkyIconButton(
            SkyIcons.Plus,
            stringResource(R.string.chat_attach),
            onPlus,
            size = SkyIconSize.Small,
            tone = com.meyouww.core.designsystem.component.SkyIconTone.Pink,
            modifier = Modifier.graphicsLayer { rotationZ = if (trayOpen) 45f else 0f },
        )
        SkyField(
            value = text,
            onValueChange = onTextChange,
            placeholder = hint,
            singleLine = false,
            maxLines = 5,
            minHeight = 48.dp,
            corner = 24.dp,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            trailing = {
                Icon(
                    SkyIcons.Smile,
                    contentDescription = stringResource(R.string.chat_sky_stickers),
                    tint = crayon.textSoft,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onStickers)
                        .padding(4.dp),
                )
            },
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 5.dp)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .semantics { contentDescription = hint },
        )
        action()
    }
}

/** One tile in the chat-actions sheet (.tl): an icon over a short name, on a pastel. */
internal class SkyTrayTile(val label: String, val icon: ImageVector, val tone: SkyTone, val onClick: () -> Unit)

/**
 * The board's chat-actions sheet (.sh3): "Send Riya something", a grid of tiles (photo, file,
 * doodle, wallpaper, alarm, reminder, or a poll in a group), the sticker packs and their
 * stickers, and a line about where things go. [extra] holds anything more (her phone's sound).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkyChatTray(
    title: String,
    tiles: List<SkyTrayTile>,
    hint: String,
    onSticker: (WhitySticker) -> Unit,
    onDismiss: () -> Unit,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val crayon = MeyouwwTheme.crayon
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = crayon.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 10.dp).size(width = 46.dp, height = 5.dp).background(crayon.line, RoundedCornerShape(3.dp)))
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp)
                .navigationBarsPadding(),
        ) {
            Text(title, style = SkyType.h3, modifier = Modifier.padding(bottom = 10.dp).semantics { heading() })
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).height(IntrinsicSize.Max)) {
                    row.forEach { tile ->
                        SkyCard(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            tone = tile.tone,
                            padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp),
                            corner = 22.dp,
                            onClick = tile.onClick,
                            onClickLabel = tile.label,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Icon(tile.icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(24.dp))
                                Text(tile.label, style = SkyType.cap.copy(fontSize = 13.sp, lineHeight = 16.sp), color = SkyInk.onPastel, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // Stickers: a chip per pack, then that pack's stickers four to a row.
            val packs = WhityPacks.all
            var selected by rememberSaveable { mutableStateOf(packs.first().first) }
            val current = packs.firstOrNull { it.first == selected }?.second ?: packs.first().second
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            ) {
                Text(stringResource(R.string.chat_sky_stickers), style = SkyType.t, modifier = Modifier.padding(end = 2.dp).semantics { heading() })
                packs.forEach { (pack, _) ->
                    SkyChip(stringResource(WhityPacks.title(pack)), selected = pack == selected, height = 34.dp, onClick = { selected = pack })
                }
            }
            current.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { sticker ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Sticker(
                                sticker,
                                size = 72.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClickLabel = stringResource(sticker.label)) { onSticker(sticker) },
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(hint, style = SkyType.hp, color = crayon.textSoft, modifier = Modifier.padding(top = 8.dp))
            extra()
        }
    }
}
