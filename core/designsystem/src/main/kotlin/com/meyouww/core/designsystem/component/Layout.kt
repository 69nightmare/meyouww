package com.meyouww.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/**
 * A scrolling screen with an optional back arrow and a heading. Every
 * secondary screen uses it so spacing and insets stay consistent.
 */
@Composable
fun CrayonScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Adapts to the phone: tighter on small screens, a readable column on tablets and foldables.
    val width = LocalConfiguration.current.screenWidthDp
    val narrow = width < 360
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = if (narrow) 12.dp else 20.dp, vertical = if (narrow) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(end = 10.dp).size(48.dp).crayonSurface(
                            fill = MaterialTheme.colorScheme.surface,
                            outline = MeyouwwTheme.crayon.outline,
                            seed = 811,
                            cornerRadius = 24.dp,
                            strokeWidth = 1.dp,
                            shadow = MeyouwwTheme.crayon.shadow,
                            shadowOffsetX = 0.dp,
                            shadowOffsetY = 3.dp,
                        ),
                    ) {
                        Icon(PastelIcons.Back, contentDescription = stringResource(R.string.ds_back))
                    }
                }
                // The title on a strip of washi tape, as on the Kuchhu-chan site.
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    TapeLabel(
                        title,
                        style = if (narrow) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 6.dp),
                        maxLines = 2,
                    )
                }
                trailing?.invoke()
            }
            Column(
                modifier = (if (compactHeight) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState()))
                    .fillMaxWidth()
                    .padding(start = 2.dp, end = 5.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 16.dp),
                content = content,
            )
        }
    }
}

/** Screens never stretch wider than this, so tablets and unfolded phones stay readable. */
val MAX_CONTENT_WIDTH = 640.dp

/** A section label inside a screen. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    TapeLabel(
        text = text,
        tape = MeyouwwTheme.crayon.yellow,
        tilt = -1f,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/**
 * Loads profile pictures for [PersonAvatar]. The app provides one backed by the
 * private avatars bucket; without it, avatars show initials.
 */
fun interface AvatarLoader {
    suspend fun load(path: String): ImageBitmap?
}

val LocalAvatarLoader = staticCompositionLocalOf<AvatarLoader?> { null }

/**
 * A round avatar: the person's photo when there is one (and you're allowed to see
 * it), otherwise their initial on a crayon colour that stays the same per person.
 */
@Composable
fun PersonAvatar(
    initial: String,
    seed: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    photo: String? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val fills = listOf(crayon.yellow, crayon.blue, crayon.green, crayon.peach, crayon.bubblegum, crayon.lilac, crayon.tangerine)
    val fill = fills[Math.floorMod(seed, fills.size)]
    val loader = LocalAvatarLoader.current
    val image by produceState<ImageBitmap?>(null, photo, loader) {
        value = if (photo != null && loader != null) loader.load(photo) else null
    }
    val picture = image
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(if (size < 24.dp) 1.dp else 2.dp, crayon.card, CircleShape)
                .clearAndSetSemantics { },
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .crayonSurface(fill = fill, outline = Color.Transparent, seed = seed, cornerRadius = size / 2, strokeWidth = 0.dp)
            .clearAndSetSemantics { },
    ) {
        Text(
            initial,
            style = if (size < 24.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleLarge,
            fontSize = if (size < 24.dp) (size.value * 0.55f).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            color = crayon.onFill,
            textAlign = TextAlign.Center,
        )
    }
}

/** A labelled switch row. The whole row is one toggle for TalkBack. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
            }
        }
        // The row handles clicks; the switch is only a visual.
        PastelSwitch(checked = checked, enabled = enabled)
    }
}

/** A small number badge, for example unread inbox items. Hidden at zero. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, fill: Color = MeyouwwTheme.crayon.red) {
    if (count <= 0) return
    val crayon = MeyouwwTheme.crayon
    val ink = if (fill == crayon.red) crayon.onRed else crayon.onFill
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 22.dp)
            .crayonSurface(fill = fill, outline = crayon.card, seed = count, cornerRadius = 11.dp, strokeWidth = 2.dp)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { },
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

/** A tappable row inside a card, with a trailing arrow-like hint. */
@Composable
fun LinkRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = listOfNotNull(title, detail).joinToString(". ") },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
        }
        Icon(PastelIcons.Forward, contentDescription = null, tint = MeyouwwTheme.crayon.textSoft)
    }
}

/** The pastel switch: berry when on, a soft track with a visible outline when off. Visual only. */
@Composable
fun PastelSwitch(checked: Boolean, enabled: Boolean = true, onCheckedChange: ((Boolean) -> Unit)? = null) {
    val crayon = MeyouwwTheme.crayon
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = crayon.card,
            checkedTrackColor = crayon.red,
            checkedBorderColor = crayon.red,
            checkedIconColor = crayon.red,
            uncheckedThumbColor = crayon.edge,
            uncheckedTrackColor = crayon.card,
            uncheckedBorderColor = crayon.edge,
        ),
    )
}
