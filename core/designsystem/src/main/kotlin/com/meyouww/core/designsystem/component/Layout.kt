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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (narrow) 12.dp else 20.dp, vertical = if (narrow) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ds_back))
                    }
                }
                Text(
                    title,
                    style = if (narrow) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Screens never stretch wider than this, so tablets and unfolded phones stay readable. */
val MAX_CONTENT_WIDTH = 640.dp

/** A section label inside a screen. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(top = 6.dp).semantics { heading() },
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
                .border(if (size < 24.dp) 1.dp else 2.dp, crayon.outline, CircleShape)
                .clearAndSetSemantics { },
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .crayonSurface(fill = fill, outline = crayon.outline, seed = seed, cornerRadius = size / 2, strokeWidth = if (size < 24.dp) 1.dp else 2.dp)
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
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** A small number badge, for example unread inbox items. Hidden at zero. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, fill: Color = MeyouwwTheme.crayon.bubblegum) {
    if (count <= 0) return
    val crayon = MeyouwwTheme.crayon
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(22.dp)
            .crayonSurface(fill = fill, outline = crayon.outline, seed = count, cornerRadius = 11.dp, strokeWidth = 1.5.dp)
            .clearAndSetSemantics { },
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelSmall, color = crayon.onFill)
    }
}

/** A tappable row inside a card, with a trailing arrow-like hint. */
@Composable
fun LinkRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = listOfNotNull(title, detail).joinToString(". ") },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("$title  →", style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MeyouwwTheme.crayon.textSoft)
    }
}
