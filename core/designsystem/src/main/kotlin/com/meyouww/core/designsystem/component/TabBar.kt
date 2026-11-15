package com.meyouww.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.meyouww.core.designsystem.theme.Palette

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
    if (crayon.living) {
        SkyDock(items, modifier)
        return
    }
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

/**
 * The Living Sky dock: a glass pill floating over the sky, with a raised pink blob under the open
 * tab. Switching tabs, the blob's front edge runs ahead and its back edge catches up, so it
 * stretches like a drop and pulls itself together.
 */
@Composable
private fun SkyDock(items: List<TabBarItem>, modifier: Modifier) {
    val crayon = MeyouwwTheme.crayon
    val reducedMotion = LocalReducedMotion.current
    val index = items.indexOfFirst { it.selected }.coerceAtLeast(0).toFloat()
    val lead by animateFloatAsState(index, if (reducedMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 420f), label = "dock-lead")
    val trail by animateFloatAsState(index, if (reducedMotion) snap() else spring(dampingRatio = 0.85f, stiffness = 170f), label = "dock-trail")
    val blob = crayon.bubblegum
    val blobEdge = crayon.pressShadowFor(blob)
    val count = items.size.coerceAtLeast(1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .skySurface(
                fill = crayon.card.copy(alpha = 0.9f),
                edge = crayon.hardShadow,
                shadow = crayon.shadow,
                sunX = crayon.sunX,
                cornerRadius = PILL,
                depth = 4.dp,
                lift = 18.dp,
                blur = 28.dp,
                belly = false,
            )
            .drawBehind {
                val pad = 6.dp.toPx()
                val gap = 6.dp.toPx()
                val cell = (size.width - pad * 2 - gap * (count - 1)) / count
                val from = minOf(lead, trail)
                val to = maxOf(lead, trail)
                val left = pad + from * (cell + gap)
                val right = pad + to * (cell + gap) + cell
                val raise = 7.dp.toPx()
                val top = pad - raise
                val bottom = size.height - pad - raise
                val r = CornerRadius(30.dp.toPx())
                drawRoundRect(blobEdge, Offset(left, top + 5.dp.toPx()), Size(right - left, bottom - top), r)
                drawRoundRect(blob, Offset(left, top), Size(right - left, bottom - top), r)
                drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(left + r.x * 0.5f, top + 1.dp.toPx()), Size((right - left - r.x).coerceAtLeast(0f), 2.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
            .padding(6.dp)
            .selectableGroup(),
    ) {
        items.forEach { item ->
            val raise by animateDpAsState(if (item.selected) (-7).dp else 0.dp, if (reducedMotion) snap() else spring(dampingRatio = 0.55f, stiffness = 500f), label = "dock-raise")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .offset(y = raise)
                    .clip(RoundedCornerShape(30.dp))
                    .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
                    .semantics { item.badgeDescription?.takeIf { item.badge > 0 }?.let { stateDescription = it } }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Box {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (item.selected) Color(Palette.BerryDeep) else crayon.textSoft,
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
