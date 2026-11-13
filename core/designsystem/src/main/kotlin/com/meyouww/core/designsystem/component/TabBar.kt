package com.meyouww.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme

/** One tab of [FloatingTabBar]. [badgeDescription] is what TalkBack says for the badge. */
data class TabBarItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    val badge: Int = 0,
    val badgeDescription: String? = null,
)

/**
 * The bottom bar: one white pill floating over the page. Every tab keeps its word under
 * the icon; the current one sits on a strawberry blob with a berry icon. At least 60 dp tall.
 */
@Composable
fun FloatingTabBar(items: List<TabBarItem>, modifier: Modifier = Modifier) {
    val crayon = MeyouwwTheme.crayon
    val reducedMotion = LocalReducedMotion.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .crayonSurface(
                fill = crayon.card,
                outline = crayon.outline,
                seed = 918,
                cornerRadius = if (crayon.classic) 24.dp else PILL,
                strokeWidth = if (crayon.classic) 2.dp else 1.dp,
                wobble = if (crayon.classic) 0.8.dp else 0.dp,
                shadow = crayon.shadow,
                shadowOffsetX = if (crayon.classic) 3.dp else 0.dp,
                shadowOffsetY = if (crayon.classic) 3.dp else 5.dp,
                grain = LocalCrayonGrain.current,
            )
            .padding(6.dp)
            .selectableGroup(),
    ) {
        items.forEach { item ->
            val blob by animateColorAsState(
                targetValue = if (!item.selected) Color.Transparent else if (crayon.classic) crayon.yellow else crayon.bubblegum,
                animationSpec = if (reducedMotion) snap() else androidx.compose.animation.core.tween(220),
                label = "tab-blob",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(blob)
                    .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
                    .semantics { item.badgeDescription?.takeIf { item.badge > 0 }?.let { stateDescription = it } }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Box {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (!item.selected) crayon.textSoft else if (crayon.classic) crayon.onFill else crayon.berryDeep,
                        modifier = Modifier.size(26.dp),
                    )
                    if (item.badge > 0) {
                        CountBadge(item.badge, modifier = Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-6).dp))
                    }
                }
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.selected) crayon.onFill else crayon.textSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
