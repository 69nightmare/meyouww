package com.meyouww.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.LocalSky
import com.meyouww.core.designsystem.theme.MeyouwwFonts
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.designsystem.theme.Palette

/*
 * The Living Sky building blocks, one for each part on the design boards (the canvas "Living Sky
 * 3D", sky.css): the same sizes, fonts, edges and shadows, so a screen built from them looks like
 * its board. Colours come from the hour's palette.
 */

/** Text styles from the boards. */
object SkyType {
    private val fredoka = TextStyle(fontFamily = MeyouwwFonts.Fredoka, fontWeight = FontWeight.SemiBold)
    private val nunito = TextStyle(fontFamily = MeyouwwFonts.Nunito, fontWeight = FontWeight.Medium)

    val h1 = fredoka.copy(fontSize = 30.sp, lineHeight = 36.sp)
    val h2 = fredoka.copy(fontSize = 24.sp, lineHeight = 30.sp)
    val h3 = fredoka.copy(fontSize = 19.sp, lineHeight = 24.sp)
    /** Card titles. */
    val t = fredoka.copy(fontSize = 16.sp, lineHeight = 22.sp)
    val button = fredoka.copy(fontSize = 17.sp, lineHeight = 22.sp)
    val buttonSmall = fredoka.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val big = fredoka.copy(fontSize = 46.sp, lineHeight = 50.sp)
    val body = nunito.copy(fontSize = 16.sp, lineHeight = 23.sp)
    val sm = nunito.copy(fontSize = 14.sp, lineHeight = 20.sp)
    /** Row titles. */
    val tt = nunito.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
    /** Row subtitles (soft). */
    val sb = nunito.copy(fontSize = 14.sp, lineHeight = 20.sp)
    /** Captions (soft, bold). */
    val cap = nunito.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp)
    /** Help under a field (soft). */
    val hp = nunito.copy(fontSize = 13.sp, lineHeight = 18.sp)
    /** Field labels. */
    val lb = nunito.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
    val chip = nunito.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
    val pill = nunito.copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 16.sp)
    val dock = nunito.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp)
    /** Whity's voice and tape labels. */
    val hand = TextStyle(fontFamily = MeyouwwFonts.Handwritten, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 21.sp)
    val tape = TextStyle(fontFamily = MeyouwwFonts.Handwritten, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp)
    val wordmark = TextStyle(fontFamily = MeyouwwFonts.Pop, fontSize = 24.sp, lineHeight = 27.sp)
}

/** The six pastels the boards use for cards, chips, pills and coins (cocoa text on all of them). */
enum class SkyTone { Pink, Butter, Mint, Blue, Lilac, Peach }

/** A pastel's face colour. */
@Composable
fun SkyTone.fill(): Color {
    val c = MeyouwwTheme.crayon
    return when (this) {
        SkyTone.Pink -> c.bubblegum
        SkyTone.Butter -> c.yellow
        SkyTone.Mint -> c.green
        SkyTone.Blue -> c.blue
        SkyTone.Lilac -> c.lilac
        SkyTone.Peach -> c.peach
    }
}

/** Text on the sky (white after dark) and its soft version. */
object SkyInk {
    val onSky: Color @Composable get() = if (LocalOnCard.current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onBackground
    val onSkySoft: Color @Composable get() =
        if (LocalOnCard.current) MeyouwwTheme.crayon.textSoft else LocalSky.current?.colors?.soft?.let { Color(it) } ?: MeyouwwTheme.crayon.textSoft
    /** Links and text buttons: the sky's on the sky, the card's on a card. */
    val link: Color @Composable get() =
        if (LocalOnCard.current) MeyouwwTheme.crayon.link else LocalSky.current?.colors?.link?.let { Color(it) } ?: MeyouwwTheme.crayon.link
    val ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val soft: Color @Composable get() = MeyouwwTheme.crayon.textSoft
    val onPastel: Color get() = Color(Palette.Cocoa)
    /** The berry used for icons and text on a pink pastel (.berry-on-pink). */
    val berryOnPink: Color get() = Color(Palette.BerryDeep)
}

/** The board's puffy card (.c3): 28 dp corners, 16 dp padding, an edge and a shadow; [level] sets how high it floats. */
@Composable
fun SkyCard(
    modifier: Modifier = Modifier,
    tone: SkyTone? = null,
    level: CardLevel = CardLevel.Normal,
    padding: PaddingValues = PaddingValues(16.dp),
    corner: Dp = 28.dp,
    spacing: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CrayonCard(
        modifier = modifier,
        fill = tone?.fill() ?: MaterialTheme.colorScheme.surface,
        cornerRadius = corner - 4.dp,
        contentPadding = padding,
        onClick = onClick,
        onClickLabel = onClickLabel,
        level = level,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** How a [SkyButton] looks: the main accent, a pastel, quiet (card), danger, or just text. */
enum class SkyButtonTone { Accent, Pink, Mint, Blue, Butter, Lilac, Quiet, Danger, Text }

/**
 * The board's squishy 3D button (.b3): a pill face on a deeper base (6 dp) that squashes into it
 * when pressed. 54 dp tall ([small]: 46 dp); [block] fills the width.
 */
@Composable
fun SkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: SkyButtonTone = SkyButtonTone.Accent,
    small: Boolean = false,
    block: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val (face, base, ink) = when (tone) {
        SkyButtonTone.Accent -> Triple(crayon.red, crayon.berryDeep, crayon.onRed)
        SkyButtonTone.Pink -> Triple(crayon.bubblegum, crayon.pressShadowFor(crayon.bubblegum), SkyInk.onPastel)
        SkyButtonTone.Mint -> Triple(crayon.green, crayon.pressShadowFor(crayon.green), SkyInk.onPastel)
        SkyButtonTone.Blue -> Triple(crayon.blue, crayon.pressShadowFor(crayon.blue), SkyInk.onPastel)
        SkyButtonTone.Butter -> Triple(crayon.yellow, crayon.pressShadowFor(crayon.yellow), SkyInk.onPastel)
        SkyButtonTone.Lilac -> Triple(crayon.lilac, crayon.pressShadowFor(crayon.lilac), SkyInk.onPastel)
        SkyButtonTone.Quiet -> Triple(crayon.card, crayon.hardShadow, MaterialTheme.colorScheme.onSurface)
        SkyButtonTone.Danger -> Triple(Color(0xFFFFE1DC), Color(0xFFE9A79C), Color(0xFF8E1B1B))
        SkyButtonTone.Text -> Triple(Color.Transparent, Color.Transparent, SkyInk.link)
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val textOnly = tone == SkyButtonTone.Text
    val push by animateDpAsState(
        if (pressed && !reduced && !textOnly) 5.dp else 0.dp,
        if (reduced) snap() else spring(dampingRatio = 0.55f, stiffness = 1200f),
        label = "sky-button",
    )
    val haptics = LocalHapticFeedback.current
    val active = enabled && !loading
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(bottom = if (textOnly) 0.dp else 6.dp)
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (textOnly) 48.dp else if (small) 46.dp else 54.dp)
            .graphicsLayer {
                translationY = push.toPx()
                val k = push / 5.dp
                scaleX = 1f + 0.03f * k
                scaleY = 1f - 0.05f * k
                transformOrigin = TransformOrigin(0.5f, 1f)
                alpha = if (enabled) 1f else 0.5f
            }
            .then(
                if (textOnly) {
                    Modifier
                } else {
                    Modifier.skySurface(
                        fill = face,
                        edge = base,
                        shadow = crayon.shadow,
                        sunX = crayon.sunX,
                        cornerRadius = PILL,
                        depth = 6.dp - push,
                        lift = 16.dp,
                        blur = 22.dp,
                    )
                },
            )
            .clip(RoundedCornerShape(PILL))
            .clickable(interactionSource = interaction, indication = null, enabled = active, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { if (loading && loadingDescription != null) stateDescription = loadingDescription }
            .padding(horizontal = if (textOnly) 12.dp else if (small) 18.dp else 24.dp, vertical = 4.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = ink, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                Text(
                    text,
                    style = if (small) SkyType.buttonSmall else SkyType.button,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Round icon button sizes on the boards: 44, 52 and 62 dp. */
enum class SkyIconSize(val size: Dp) { Small(44.dp), Normal(52.dp), Large(62.dp) }

/** The face of a round icon button: the card, the accent, or a pastel. */
enum class SkyIconTone { Card, Accent, Pink, Mint, Glass }

/** The board's round 3D icon button (.i3), with an optional count badge in its corner. */
@Composable
fun SkyIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: SkyIconSize = SkyIconSize.Normal,
    tone: SkyIconTone = SkyIconTone.Card,
    badge: Int = 0,
    tint: Color? = null,
    iconSize: Dp = if (size == SkyIconSize.Large) 28.dp else if (size == SkyIconSize.Small) 20.dp else 24.dp,
) {
    val crayon = MeyouwwTheme.crayon
    val (face, base, ink) = when (tone) {
        SkyIconTone.Card -> Triple(crayon.card, crayon.hardShadow, MaterialTheme.colorScheme.onSurface)
        SkyIconTone.Glass -> Triple(crayon.card.copy(alpha = 0.86f), crayon.hardShadow, MaterialTheme.colorScheme.onSurface)
        SkyIconTone.Accent -> Triple(crayon.red, crayon.berryDeep, crayon.onRed)
        SkyIconTone.Pink -> Triple(crayon.bubblegum, crayon.pressShadowFor(crayon.bubblegum), SkyInk.onPastel)
        SkyIconTone.Mint -> Triple(crayon.green, crayon.pressShadowFor(crayon.green), SkyInk.onPastel)
    }
    Box(modifier.padding(bottom = 5.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size.size)
                .skySurface(face, base, crayon.shadow, crayon.sunX, cornerRadius = size.size / 2, depth = 5.dp, lift = 12.dp, blur = 18.dp, belly = false)
                .clip(CircleShape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        ) {
            Icon(icon, contentDescription = null, tint = tint ?: ink, modifier = Modifier.size(iconSize))
        }
        if (badge > 0) SkyBadge(badge, Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp))
    }
}

/** The board's count badge (.bdg): the accent, its deeper edge, bold small numbers. */
@Composable
fun SkyBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val crayon = MeyouwwTheme.crayon
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 22.dp)
            .widthIn(min = 22.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(crayon.berryDeep, Offset(0f, 2.dp.toPx()), size, r)
                drawRoundRect(crayon.red, Offset.Zero, size, r)
            }
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { },
    ) {
        Text(if (count > 99) "99+" else count.toString(), style = SkyType.pill, color = crayon.onRed)
    }
}

/**
 * The board's chip (.ch): 42 dp tall, bold small text, on its own edge. [selected] turns it ink
 * with a tick (it reads without colour); a [tone] makes it pastel.
 */
@Composable
fun SkyChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    tone: SkyTone? = null,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = 42.dp,
) {
    val crayon = MeyouwwTheme.crayon
    val on = selected == true
    val face = when {
        on -> MaterialTheme.colorScheme.onSurface
        tone != null -> tone.fill()
        else -> crayon.card
    }
    val base = when {
        on -> Color.Black.copy(alpha = 0.4f)
        tone != null -> crayon.pressShadowFor(face)
        else -> crayon.hardShadow
    }
    val ink = when {
        on -> crayon.card
        tone != null -> SkyInk.onPastel
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .padding(bottom = 3.dp)
            .height(height)
            .skySurface(face, base, Color.Transparent, 0f, cornerRadius = PILL, depth = 3.dp, belly = false)
            .clip(RoundedCornerShape(PILL))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (selected != null) Modifier.semantics { this.selected = on } else Modifier)
            .padding(horizontal = if (height < 42.dp) 12.dp else 16.dp),
    ) {
        if (on) Icon(SkyIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        if (icon != null && !on) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        Text(text, style = SkyType.chip, color = ink, maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

/** The board's little pill label (.pl): 24 dp tall, extra-bold 12 sp on a pastel. */
@Composable
fun SkyPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: SkyTone? = SkyTone.Pink,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val face = tone?.fill() ?: Color.White
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            // A tappable pill keeps the board's size but gets a finger-sized touch area around it.
            .then(if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier)
            .height(24.dp)
            .clip(RoundedCornerShape(PILL))
            .background(face)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = SkyInk.onPastel, modifier = Modifier.size(14.dp))
        Text(text, style = SkyType.pill, color = SkyInk.onPastel, maxLines = 1)
    }
}

/**
 * The board's switch (.s3), drawn: 58 × 34, carved in when off, the accent with a white thumb on
 * its edge when on; the thumb stretches as it crosses. Visual only: the row around it toggles.
 */
@Composable
fun SkySwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val crayon = MeyouwwTheme.crayon
    val field = LocalSky.current?.colors?.field?.let { Color(it) } ?: crayon.card
    val reduced = LocalReducedMotion.current
    val x by animateFloatAsState(if (checked) 1f else 0f, if (reduced) snap() else spring(dampingRatio = 0.6f, stiffness = 600f), label = "sky-switch")
    val trail by animateFloatAsState(if (checked) 1f else 0f, if (reduced) snap() else spring(dampingRatio = 0.9f, stiffness = 250f), label = "sky-switch-trail")
    Box(
        modifier
            .size(width = 58.dp, height = 34.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                val on = x > 0.5f
                drawRoundRect(if (on) crayon.red else field, cornerRadius = r)
                // Carved in: a soft inner shadow along the top.
                drawRoundRect(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.18f), 0.35f to Color.Transparent),
                    cornerRadius = r,
                )
                if (!on) drawRoundRect(crayon.edge, cornerRadius = r, style = Stroke(2.dp.toPx()))
                val thumb = 26.dp.toPx()
                val pad = 4.dp.toPx()
                val travel = size.width - thumb - pad * 2
                val lead = pad + travel * maxOf(x, trail)
                val back = pad + travel * minOf(x, trail)
                val w = thumb + (lead - back)
                val left = back
                val top = pad
                val face = if (on) Color.White else crayon.edge
                drawRoundRect(if (on) crayon.berryDeep else Color.Black.copy(alpha = 0.25f), Offset(left, top + 2.dp.toPx()), Size(w, thumb), CornerRadius(thumb / 2))
                drawRoundRect(face, Offset(left, top), Size(w, thumb), CornerRadius(thumb / 2))
                drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(left + thumb * 0.3f, top + 1.dp.toPx()), Size((w - thumb * 0.6f).coerceAtLeast(0f), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
            .clearAndSetSemantics { },
    )
}

/**
 * The board's segmented choice (.sg): a carved track with the chosen option on a raised pink
 * blob. Each option is a radio button for TalkBack.
 */
@Composable
fun SkySegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val crayon = MeyouwwTheme.crayon
    val field = LocalSky.current?.colors?.field?.let { Color(it) } ?: crayon.card
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(field, cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.10f), 0.3f to Color.Transparent), cornerRadius = r)
                drawRoundRect(crayon.line, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
            }
            .padding(5.dp)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .then(
                        if (on) {
                            Modifier.skySurface(crayon.bubblegum, crayon.pressShadowFor(crayon.bubblegum), Color.Transparent, 0f, cornerRadius = PILL, depth = 3.dp, belly = false)
                        } else {
                            Modifier
                        },
                    )
                    .clip(RoundedCornerShape(PILL))
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(i) },
            ) {
                Text(label, style = SkyType.chip, color = if (on) SkyInk.onPastel else crayon.textSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A thin line between rows inside a card (.r3 + .r3). */
@Composable
fun SkyDivider(modifier: Modifier = Modifier) {
    val line = MeyouwwTheme.crayon.line
    Box(modifier.fillMaxWidth().height(1.dp).background(line))
}

/**
 * The board's top bar (.bar): padding 18/18/8, a small round back button, the title on the sky
 * (h2 by default) and anything [trailing]. [leading] replaces the title with your own content.
 */
@Composable
fun SkyTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleStyle: TextStyle = SkyType.h2,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
    ) {
        if (onBack != null) {
            SkyIconButton(SkyIcons.Back, stringResource(R.string.ds_back), onBack, size = SkyIconSize.Small)
        }
        Box(Modifier.weight(1f)) {
            if (leading != null) {
                leading()
            } else if (title != null) {
                Text(
                    title,
                    style = titleStyle.copy(shadow = skyHalo()),
                    color = SkyInk.onSky,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        trailing?.invoke()
    }
}

/** A section heading on the sky (h3, white after dark), with an optional caption on the right. */
@Composable
fun SkySectionHeading(text: String, modifier: Modifier = Modifier, caption: String? = null, badge: Int = 0, inset: Dp = 18.dp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = inset),
    ) {
        Text(text, style = SkyType.h3.copy(shadow = skyHalo()), color = SkyInk.onSky, modifier = Modifier.semantics { heading() })
        if (badge > 0) SkyBadge(badge)
        Box(Modifier.weight(1f))
        if (caption != null) Text(caption, style = SkyType.cap.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
    }
}

/** The board's washi tape label (.tp): handwriting on a striped pastel strip, a little crooked. */
@Composable
fun SkyTape(text: String, modifier: Modifier = Modifier, tone: SkyTone = SkyTone.Pink) {
    val face = tone.fill()
    Box(
        modifier
            .rotate(-2f)
            .drawWithCache {
                val stripe = 11.dp.toPx()
                onDrawBehind {
                    drawRect(Color.Black.copy(alpha = 0.08f), topLeft = Offset(0f, 3.dp.toPx()), size = size)
                    drawRect(face)
                    clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }) {
                        var x = -size.height
                        while (x < size.width + size.height) {
                            drawLine(Color.White.copy(alpha = 0.5f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 5.dp.toPx())
                            x += stripe
                        }
                    }
                }
            }
            .padding(start = 16.dp, end = 16.dp, top = 3.dp, bottom = 4.dp),
    ) {
        Text(text, style = SkyType.tape, color = SkyInk.onPastel)
    }
}

/** The board's date chip (.dt): a short label over a big number, on a pastel with its edge. */
@Composable
fun SkyDateChip(small: String, big: String, modifier: Modifier = Modifier, tone: SkyTone = SkyTone.Pink) {
    val crayon = MeyouwwTheme.crayon
    val face = tone.fill()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .size(width = 54.dp, height = 60.dp)
            .skySurface(face, crayon.pressShadowFor(face), Color.Transparent, 0f, cornerRadius = 18.dp, depth = 3.dp, belly = false)
            .clearAndSetSemantics { },
    ) {
        Text(small, style = SkyType.pill.copy(fontSize = 11.sp, letterSpacing = 0.5.sp), color = SkyInk.onPastel)
        Text(big, style = SkyType.h3.copy(fontSize = 22.sp), color = SkyInk.onPastel)
    }
}

/** The board's glass capsule (.hd, .cmp, .dock): the card colour, see-through, on an edge with a shadow. */
fun Modifier.skyGlass(radius: Dp, fill: Color, edge: Color, shadow: Color, sunX: Float): Modifier =
    this.skySurface(fill.copy(alpha = 0.86f), edge, shadow, sunX, cornerRadius = radius, depth = 4.dp, lift = 18.dp, blur = 30.dp, belly = false)

/** A Living Sky screen's floating main button (.b3.fab), placed at the bottom right of a box. */
@Composable
fun BoxScope.SkyFab(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SkyButton(
        text = text,
        onClick = onClick,
        icon = icon,
        modifier = modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp),
    )
}

/** The pastel a colour is, if it is one of the six (for components given a fill colour). */
@Composable
internal fun skyToneOf(fill: Color): SkyTone? = SkyTone.entries.firstOrNull { it.fill() == fill }

/**
 * The board's field (.f3): a pressed-in well on the field colour with a 2 dp edge, 56 dp tall at
 * least, radius 20 (or a pill for search). On focus the edge turns to the focus colour with a
 * soft halo; on an error it turns red.
 */
@Composable
fun SkyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    minHeight: Dp = 56.dp,
    corner: Dp = 20.dp,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: TextStyle = SkyType.body,
) {
    val crayon = MeyouwwTheme.crayon
    val ink = MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val field = LocalSky.current?.colors?.field?.let { Color(it) } ?: crayon.card
    val outline = when {
        isError -> crayon.onError
        focused -> crayon.focus
        else -> crayon.edge
    }
    val well = Color.Black.copy(alpha = if (field.luminance() > 0.5f) 0.07f else 0.22f)
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = textStyle.copy(color = ink),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(crayon.focus),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minHeight)
                    .drawWithCache {
                        val r = CornerRadius(corner.toPx().coerceAtMost(size.height / 2))
                        val inset = Brush.verticalGradient(0f to well, 6.dp.toPx() / size.height to Color.Transparent)
                        val shape = Path().apply {
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, r))
                        }
                        val grow = 3.dp.toPx()
                        onDrawBehind {
                            if (focused && !isError) {
                                drawRoundRect(
                                    crayon.focus.copy(alpha = 0.28f),
                                    topLeft = Offset(-grow, -grow),
                                    size = Size(size.width + grow * 2, size.height + grow * 2),
                                    cornerRadius = CornerRadius(r.x + grow),
                                    style = Stroke(4.dp.toPx()),
                                )
                            }
                            drawRoundRect(field, cornerRadius = r)
                            // The pressed-in well: a soft shade along the top inside edge.
                            clipPath(shape) { drawRect(inset) }
                            val w = (if (focused || isError) 2.5.dp else 2.dp).toPx()
                            drawRoundRect(
                                outline,
                                topLeft = Offset(w / 2, w / 2),
                                size = Size(size.width - w, size.height - w),
                                cornerRadius = CornerRadius(r.x - w / 2),
                                style = Stroke(w),
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (leading != null) Icon(leading, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.size(20.dp))
                if (prefix != null) Text(prefix, style = textStyle, color = crayon.textSoft, modifier = Modifier.padding(end = 0.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = crayon.textSoft, maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                trailing?.invoke()
            }
        },
    )
}
