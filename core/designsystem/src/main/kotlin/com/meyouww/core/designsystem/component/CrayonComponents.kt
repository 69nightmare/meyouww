package com.meyouww.core.designsystem.component

import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme

enum class ButtonKind { Primary, Secondary, Quiet }

/**
 * The crayon button, as on the Kuchhu-chan site: a fat pill with a thick ink outline
 * and a hard comic shadow that it presses down into, plus a confirm haptic.
 * Primary is crayon red with white text, Secondary sunny yellow, Quiet a white ghost.
 * At least 52 dp tall.
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
) {
    val crayon = MeyouwwTheme.crayon
    val (fill, content) = when (kind) {
        ButtonKind.Primary -> crayon.red to Color.White
        ButtonKind.Secondary -> crayon.yellow to crayon.onFill
        ButtonKind.Quiet -> Color.White to crayon.onFill
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    // Pressing pushes the button into its shadow: 4 dp of shadow becomes 1.
    val push by animateDpAsState(
        targetValue = if (pressed && !reducedMotion) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 1200f),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    val active = enabled && !loading
    val seed = remember(text) { text.hashCode() }
    val shadowOffset = 4.dp - push
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp, minWidth = 120.dp)
            .graphicsLayer {
                translationX = push.toPx()
                translationY = push.toPx()
                alpha = if (enabled) 1f else 0.55f
            }
            .crayonSurface(
                fill = fill,
                outline = crayon.outline,
                seed = seed,
                cornerRadius = 30.dp,
                strokeWidth = 3.dp,
                shadow = if (kind == ButtonKind.Quiet) null else crayon.hardShadow,
                shadowOffsetX = shadowOffset,
                shadowOffsetY = shadowOffset,
                grain = LocalCrayonGrain.current,
                grainAlpha = 0.14f,
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
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(color = content, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, color = content, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

/** A paper card with a crayon outline. */
@Composable
fun CrayonCard(
    modifier: Modifier = Modifier,
    fill: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = if (fill == MaterialTheme.colorScheme.surface) MaterialTheme.colorScheme.onSurface else MeyouwwTheme.crayon.onFill,
    seed: Int = 0,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = modifier
                .crayonSurface(
                    fill = fill,
                    outline = crayon.outline,
                    seed = seed,
                    cornerRadius = cornerRadius,
                    strokeWidth = 3.dp,
                    wobble = 1.2.dp,
                    shadow = crayon.shadow,
                    shadowOffsetX = 6.dp,
                    shadowOffsetY = 7.dp,
                    grain = LocalCrayonGrain.current,
                )
                .then(clickModifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** A small rounded chip, used for statuses and quick actions. */
@Composable
fun CrayonChip(
    text: String,
    modifier: Modifier = Modifier,
    fill: Color = MeyouwwTheme.crayon.yellow,
    onClick: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val crayon = MeyouwwTheme.crayon
    val seed = remember(text) { text.hashCode() xor 0x5f3759df }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .crayonSurface(
                fill = fill,
                outline = crayon.outline,
                seed = seed,
                cornerRadius = 24.dp,
                strokeWidth = 2.5.dp,
                shadow = crayon.hardShadow,
                shadowOffsetX = 2.dp,
                shadowOffsetY = 2.dp,
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides crayon.onFill) {
            leading?.invoke(this)
            Text(text, style = MaterialTheme.typography.labelLarge, color = crayon.onFill)
        }
    }
}

/**
 * Text field with a crayon outline, a visible label above it and an error
 * message below it. The outline thickens and turns the focus colour on focus.
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
) {
    val crayon = MeyouwwTheme.crayon
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = when {
        errorText != null -> scheme.error
        focused -> crayon.focus
        else -> crayon.outline
    }
    val seed = remember(label) { label.hashCode() }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onBackground)
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
                        .crayonSurface(
                            fill = scheme.surface,
                            outline = outline,
                            seed = seed,
                            cornerRadius = 16.dp,
                            strokeWidth = if (focused || errorText != null) 3.dp else 2.dp,
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
            errorText != null -> Text("⚠ $errorText", style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            supportingText != null -> Text(supportingText, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft)
        }
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
                    .crayonSurface(fill = fill, outline = crayon.outline, seed = title.length, cornerRadius = 20.dp, strokeWidth = 2.dp)
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

/** A full-width message with a Whity sticker, for empty, error and offline states. */
@Composable
fun StickerMessage(
    sticker: WhitySticker,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Sticker(sticker, decorative = true, size = 140.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MeyouwwTheme.crayon.textSoft)
        if (actionLabel != null && onAction != null) {
            CrayonButton(text = actionLabel, onClick = onAction)
        }
    }
}
