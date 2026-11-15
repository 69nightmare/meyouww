package com.meyouww.core.designsystem.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.R
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/*
 * Placement helpers for the Living Sky look. In the other looks they show their content exactly
 * as it was, so a screen can use them without changing how Pastel Whity or Crayon look.
 */

/**
 * A settings-style section that, in the Living Sky look, rests as one summary row (an icon coin,
 * the [title] and a short [summary]) and opens in place when tapped; a round button on its top
 * edge folds it away again. Other looks show [content] as it is.
 */
@Composable
fun FoldingSection(
    title: String,
    summary: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!MeyouwwTheme.crayon.living) {
        content()
        return
    }
    val crayon = MeyouwwTheme.crayon
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val openLabel = stringResource(R.string.ds_section_open, title)
    val closeLabel = stringResource(R.string.ds_section_close, title)
    val shut = stringResource(R.string.ds_section_closed)
    Box(modifier.fillMaxWidth().animateContentSize()) {
        if (!open) {
            CrayonCard(
                modifier = Modifier.fillMaxWidth().semantics { stateDescription = shut },
                onClick = { open = true },
                onClickLabel = openLabel,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp).skySurface(tint, crayon.pressShadowFor(tint), Color.Transparent, 0f, cornerRadius = 21.dp, depth = 3.dp)) {
                        Icon(icon, contentDescription = null, tint = crayon.onFill, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(summary, style = MaterialTheme.typography.bodyMedium, color = crayon.textSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(PastelIcons.Forward, contentDescription = null, tint = crayon.textSoft, modifier = Modifier.rotate(90f))
                }
            }
        } else {
            content()
            // Fold it away: a little round button sitting on the card's top edge.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp, y = (-16).dp)
                    .size(40.dp)
                    .skySurface(MaterialTheme.colorScheme.surface, crayon.hardShadow, crayon.shadow, crayon.sunX, cornerRadius = 20.dp, depth = 3.dp, lift = 6.dp, blur = 10.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = closeLabel, role = Role.Button) { open = false },
            ) {
                Icon(PastelIcons.Forward, contentDescription = closeLabel, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp).rotate(-90f))
            }
        }
    }
}
