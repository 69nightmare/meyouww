package com.meyouww.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/**
 * Primary: berry, the one main action on a screen. Secondary: a soft pastel (strawberry
 * unless a fill is given) for friendly second choices. Quiet: white with an outline, for
 * "not now". Danger: a pale red with a red outline, for things that delete or remove.
 */
enum class ButtonKind { Primary, Secondary, Quiet, Danger }

/**
 * The pastel button: a fat pill that presses down into its own deeper-coloured shadow,
 * with a confirm haptic. At least 54 dp tall; labels wrap to two lines and shrink a little
 * rather than cut off. Reduced motion turns the press off.
 */
@Composable
fun CrayonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String? = null,
    icon: ImageVector? = null,
    fill: Color? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val labelStyle = MaterialTheme.typography.labelLarge
    // Convert each line before multiplying: Android's accessibility font scaling
    // is nonlinear, so converting twice the sp value underestimates two lines.
    val buttonHeight = maxOf(BUTTON_HEIGHT, with(LocalDensity.current) { labelStyle.lineHeight.toDp() * 2f } + 12.dp)
    val background: Color
    val content: Color
    val press: Color?
    val ring: Color?
    when (kind) {
        ButtonKind.Primary -> {
            background = crayon.red
            content = crayon.onRed
            press = crayon.berryDeep
            ring = null
        }
        ButtonKind.Secondary -> {
            background = fill ?: crayon.bubblegum
            content = crayon.onFill
            press = crayon.pressShadowFor(background)
            ring = null
        }
        ButtonKind.Quiet -> {
            background = fill ?: crayon.card
            content = MaterialTheme.colorScheme.onSurface
            press = null
            ring = crayon.edge
        }
        ButtonKind.Danger -> {
            background = crayon.errorFill
            content = crayon.onError
            press = null
            ring = crayon.onError
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    // Pressing pushes the pill into its shadow: 4 dp of shadow becomes 1.
    val push by animateDpAsState(
        targetValue = if (pressed && !reducedMotion && press != null) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 1200f),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    val active = enabled && !loading
    val seed = remember(text) { text.hashCode() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // Consistent height with room for readable two-line labels on narrow phones.
            .heightIn(min = buttonHeight)
            .defaultMinSize(minWidth = 96.dp)
            .graphicsLayer {
                translationY = push.toPx()
                alpha = if (enabled) 1f else 0.5f
            }
            .crayonSurface(
                fill = background,
                outline = ring ?: Color.Transparent,
                seed = seed,
                cornerRadius = PILL,
                strokeWidth = if (ring != null) 2.dp else 0.dp,
                shadow = press,
                shadowOffsetX = 0.dp,
                shadowOffsetY = 4.dp - push,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = active,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .semantics { if (loading && loadingDescription != null) stateDescription = loadingDescription }
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                BasicText(
                    text = text,
                    style = labelStyle.copy(color = content, textAlign = TextAlign.Center),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = labelStyle.fontSize, stepSize = 0.5.sp),
                )
            }
        }
    }
}

/** The one button height used everywhere, so rows of buttons line up. */
val BUTTON_HEIGHT = 54.dp

/** Large enough to make any rounded shape a pill or a circle. */
internal val PILL = 999.dp

/**
 * A white sticker card with a soft lift (or a pastel card when [fill] is given). Tappable
 * when [onClick] is set; it shrinks a touch while pressed.
 */
@Composable
fun CrayonCard(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = if (fill == MaterialTheme.colorScheme.surface) MaterialTheme.colorScheme.onSurface else MeyouwwTheme.crayon.onFill,
    seed: Int = 0,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.985f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "paper-card-press",
    )
    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .crayonSurface(
                    fill = fill,
                    outline = crayon.outline,
                    seed = seed,
                    cornerRadius = cornerRadius,
                    strokeWidth = 1.dp,
                    shadow = crayon.shadow,
                    shadowOffsetX = 0.dp,
                    shadowOffsetY = 4.dp,
                )
                .then(clickModifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * A rounded chip for statuses, quick actions and choices. With a [fill] it is a pastel
 * pill; without one it is white with an outline. Pass [selected] for a choice: the chosen
 * chip turns cocoa and gains a tick, so it reads without colour.
 */
@Composable
fun CrayonChip(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color? = MeyouwwTheme.crayon.yellow,
    onClick: (() -> Unit)? = null,
    selected: Boolean? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val seed = remember(text) { text.hashCode() xor 0x5f3759df }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) 0.96f else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 800f),
        label = "crayon-chip-press",
    )
    val chosen = selected == true
    val background = when {
        chosen -> MaterialTheme.colorScheme.onBackground
        fill != null -> fill
        else -> crayon.card
    }
    val ink = when {
        chosen -> MaterialTheme.colorScheme.background
        fill != null -> crayon.onFill
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .crayonSurface(
                fill = background,
                outline = if (fill == null && !chosen) crayon.edge else Color.Transparent,
                seed = seed,
                cornerRadius = PILL,
                strokeWidth = if (fill == null && !chosen) 1.5.dp else 0.dp,
            )
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, role = Role.Button, onClick = onClick) else Modifier)
            .then(if (selected != null) Modifier.semantics { this.selected = chosen } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
            if (chosen) Icon(PastelIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
            leading?.invoke(this)
            Text(text, style = MaterialTheme.typography.titleSmall, color = ink)
        }
    }
}

/**
 * Text field with a visible label above it and an error (or success) message below it.
 * The outline turns berry with a soft pink halo on focus, and red on an error.
 */
@Composable
fun CrayonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    successText: String? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = when {
        errorText != null -> crayon.onError
        focused -> crayon.focus
        else -> crayon.edge
    }
    val halo = if (focused && errorText == null) crayon.bubblegum else Color.Transparent
    val seed = remember(label) { label.hashCode() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onBackground)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(crayon.focus),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (errorText != null) error(errorText) },
            decorationBox = { inner ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .drawBehind {
                            if (halo.alpha > 0f) {
                                val grow = 3.dp.toPx()
                                drawRoundRect(
                                    color = halo,
                                    topLeft = Offset(-grow, -grow),
                                    size = Size(size.width + grow * 2, size.height + grow * 2),
                                    cornerRadius = CornerRadius(21.dp.toPx()),
                                    style = Stroke(width = 4.dp.toPx()),
                                )
                            }
                        }
                        .crayonSurface(
                            fill = scheme.surface,
                            outline = outline,
                            seed = seed,
                            cornerRadius = 18.dp,
                            strokeWidth = if (focused || errorText != null) 2.5.dp else 2.dp,
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = textStyle, color = crayon.textSoft)
                    }
                    inner()
                }
            },
        )
        when {
            errorText != null -> FieldNote(errorText, PastelIcons.Alert, crayon.onError)
            successText != null -> FieldNote(successText, PastelIcons.Check, crayon.success)
            supportingText != null -> Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }
    }
}

@Composable
private fun FieldNote(text: String, icon: ImageVector, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = color)
    }
}

/** Overall state of a checklist row. Never shown by colour alone: each has its own symbol and words. */
enum class CheckState { Ok, NeedsFix, Unknown }

/** One row of a setup checklist: symbol, title, detail and an optional fix button. */
@Composable
fun ChecklistRow(
    state: CheckState,
    title: String,
    detail: String,
    stateLabel: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val (symbol, fill) = when (state) {
        CheckState.Ok -> "✓" to crayon.green
        CheckState.NeedsFix -> "✗" to crayon.bubblegum
        CheckState.Unknown -> "?" to crayon.yellow
    }
    CrayonCard(modifier = modifier.fillMaxWidth(), seed = title.hashCode(), contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(fill, CircleShape)
                    .semantics { stateDescription = stateLabel },
            ) {
                Text(symbol, style = MaterialTheme.typography.titleLarge, color = crayon.onFill)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("$stateLabel · $detail", style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
            }
        }
        if (actionLabel != null && onAction != null) {
            CrayonButton(
                text = actionLabel,
                onClick = onAction,
                kind = ButtonKind.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * A full-width message with a Whity sticker on a pastel circle, for empty, error and
 * offline states. Whity's pose says which: waiting (empty), oops (error) and so on.
 */
@Composable
fun StickerMessage(
    sticker: WhitySticker,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    blob: Color = MeyouwwTheme.crayon.bubblegum,
) {
    CrayonCard(modifier = modifier.fillMaxWidth(), seed = title.hashCode()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp).background(blob, CircleShape)) {
                Sticker(sticker, decorative = true, size = 128.dp)
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MeyouwwTheme.crayon.textSoft)
            if (actionLabel != null && onAction != null) {
                CrayonButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
