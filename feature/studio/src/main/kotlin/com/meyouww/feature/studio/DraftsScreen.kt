package com.meyouww.feature.studio

import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.skyGlass
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyFab
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import com.meyouww.core.designsystem.component.skyHalo
import com.meyouww.core.designsystem.component.PastelIcons
import com.meyouww.core.designsystem.component.Sticker
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.database.Draft
import com.meyouww.core.database.DraftStore
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.SectionTitle
import com.meyouww.core.designsystem.component.TapeLabel
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DraftsViewModel @Inject constructor(private val store: DraftStore) : ViewModel() {
    val drafts: StateFlow<List<Draft>?> = store.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun duplicate(id: String) = viewModelScope.launch { store.duplicate(id) }
    fun delete(id: String) = viewModelScope.launch { store.delete(id) }
}

/** The Create tab: start a doodle or pick up a draft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DraftsScreen(
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: DraftsViewModel = hiltViewModel(),
) {
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    val living = MeyouwwTheme.crayon.living
    if (living) {
        SkyCreate(drafts, onNew, onOpen, onDuplicate = viewModel::duplicate, onDelete = { confirmDelete = it })
    } else CrayonScreen(
        title = stringResource(R.string.create_title),
        // Living Sky: the one main button floats at the bottom, within reach of a thumb.
        floating = { CrayonButton(stringResource(R.string.create_new), onClick = onNew, icon = PastelIcons.Plus) },
    ) {
        val list = drafts
        if (living) {
            // Living Sky: Whity in her flower crown on the sky, saying what Create is for.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list != null && list.isEmpty()) {
                        Text(stringResource(R.string.create_empty_title), style = MaterialTheme.typography.headlineSmall.copy(shadow = skyHalo()))
                    }
                    Text(stringResource(R.string.create_empty_body), style = MaterialTheme.typography.bodyLarge.copy(shadow = skyHalo()))
                }
                Sticker(WhitySticker.ReactFlowerCrown, decorative = true, size = 116.dp)
            }
        } else CrayonCard(modifier = Modifier.fillMaxWidth(), fill = MeyouwwTheme.crayon.lilac, seed = 82) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list != null && list.isEmpty()) {
                        Text(stringResource(R.string.create_empty_title), style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(stringResource(R.string.create_empty_body), style = MaterialTheme.typography.bodyMedium)
                }
                Sticker(WhitySticker.ReactFlowerCrown, decorative = true, size = 104.dp)
            }
            CrayonButton(stringResource(R.string.create_new), onClick = onNew, icon = PastelIcons.Plus, modifier = Modifier.fillMaxWidth())
        }
        when {
            list == null -> Unit
            list.isEmpty() -> Unit
            else -> {
                TapeLabel(stringResource(R.string.create_drafts), tape = MeyouwwTheme.crayon.yellow)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 328.dp || LocalConfiguration.current.fontScale > 1.3f) 1 else 2
                    val tileWidth = (maxWidth - 14.dp * (columns - 1)) / columns
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        list.forEach { draft ->
                            DraftTile(
                                draft = draft,
                                modifier = Modifier.width(tileWidth),
                                onOpen = { onOpen(draft.id) },
                                onDuplicate = { viewModel.duplicate(draft.id) },
                                onDelete = { confirmDelete = draft.id },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.create_delete_title)) },
            text = { Text(stringResource(R.string.create_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.delete(id)
                }) { Text(stringResource(R.string.create_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.create_cancel)) } },
        )
    }
}

@Composable
private fun DraftTile(draft: Draft, onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val edited = DateUtils.getRelativeTimeSpanString(draft.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val thumb by produceState<ImageBitmap?>(null, draft.thumbnail, draft.updatedAt) {
        value = withContext(Dispatchers.IO) {
            draft.thumbnail?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    CrayonCard(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        seed = draft.id.hashCode(),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.create_open),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MeyouwwTheme.crayon.outline, RoundedCornerShape(16.dp))
                .clearAndSetSemantics { },
        ) {
            thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(stringResource(R.string.create_edited, edited), style = MaterialTheme.typography.bodySmall)
        CrayonButton(stringResource(R.string.create_duplicate), onClick = onDuplicate, kind = ButtonKind.Secondary, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.create_delete), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Create in the Living Sky look, as on the board: the title with "Drafts save themselves", how
 * many drafts there are, Whity saying "Draw something sweet", your drafts as a fan of tilted
 * cards to swipe through (tap one to open it), when it was edited with a ⋯ menu, and "New doodle".
 */
@Composable
private fun SkyCreate(
    drafts: List<Draft>?,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val crayon = MeyouwwTheme.crayon
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
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.create_title), style = SkyType.h1.copy(shadow = skyHalo()), color = SkyInk.onSky, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.create_sky_sub), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft)
                }
                if (!drafts.isNullOrEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.create_sky_count, drafts.size, drafts.size),
                        style = SkyType.pill,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .skyGlass(999.dp, crayon.card, crayon.hardShadow, Color.Transparent, 0f)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            // Whity with her line, on the right.
            Box(Modifier.fillMaxWidth().height(110.dp)) {
                Text(
                    stringResource(R.string.create_sky_bubble),
                    style = SkyType.hand,
                    color = Color(0xFF3A2A32),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 70.dp)
                        .skySurface(Color.White, crayon.hardShadow, crayon.shadow, crayon.sunX, cornerRadius = 18.dp, depth = 3.dp, lift = 8.dp, blur = 14.dp, belly = false)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Sticker(WhitySticker.ReactFlowerCrown, decorative = true, size = 84.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 26.dp))
            }
            val list = drafts
            when {
                list == null -> Unit
                list.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.create_empty_title), style = SkyType.h3.copy(shadow = skyHalo()), color = SkyInk.onSky, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.create_empty_body), style = SkyType.sm.copy(shadow = skyHalo()), color = SkyInk.onSkySoft, textAlign = TextAlign.Center)
                }
                else -> DraftFan(list, onOpen, onDuplicate, onDelete)
            }
        }
        SkyFab(stringResource(R.string.create_sky_new), SkyIcons.Pencil, onClick = onNew)
    }
}

/** The drafts as a fan of tilted cards: swipe through them, tap the front one to open it. */
@Composable
private fun DraftFan(list: List<Draft>, onOpen: (String) -> Unit, onDuplicate: (String) -> Unit, onDelete: (String) -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val pager = rememberPagerState { list.size }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val card = 156.dp
        val side = ((maxWidth - card) / 2).coerceAtLeast(0.dp)
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = side),
            pageSize = PageSize.Fixed(card),
            pageSpacing = 0.dp,
            modifier = Modifier.fillMaxWidth().height(290.dp),
        ) { page ->
            val draft = list[page]
            val edited = DateUtils.getRelativeTimeSpanString(draft.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            val thumb by produceState<ImageBitmap?>(null, draft.thumbnail, draft.updatedAt) {
                value = withContext(Dispatchers.IO) {
                    draft.thumbnail?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
                }
            }
            val description = stringResource(R.string.create_draft_description, draft.elementCount, edited)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        val d = offset.coerceIn(-1.5f, 1.5f)
                        rotationY = d * 28f
                        rotationZ = -d * 4f
                        scaleX = 1f - kotlin.math.abs(d) * 0.12f
                        scaleY = scaleX
                        translationX = d * 40.dp.toPx()
                        cameraDistance = 14f * density
                    }
                    .zIndex(-kotlin.math.abs((pager.currentPage - page).toFloat())),
            ) {
                Box(
                    Modifier
                        .size(width = 148.dp, height = 262.dp)
                        .skySurface(Color.White, crayon.hardShadow, crayon.shadow, crayon.sunX, cornerRadius = 24.dp, depth = 4.dp, lift = 18.dp, blur = 24.dp, belly = false)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(onClickLabel = stringResource(R.string.create_open), role = Role.Button) { onOpen(draft.id) }
                        .semantics { contentDescription = description }
                        .padding(6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SkyTone.Blue.fill()),
                ) {
                    thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
    val current = list.getOrNull(pager.currentPage) ?: return
    val edited = DateUtils.getRelativeTimeSpanString(current.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Text(stringResource(R.string.create_edited, edited), style = SkyType.tt.copy(shadow = skyHalo()), color = SkyInk.onSky)
        Box {
            SkyIconButton(SkyIcons.More, stringResource(R.string.create_sky_more), { menu = true }, size = SkyIconSize.Small)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.create_open)) }, onClick = {
                    menu = false
                    onOpen(current.id)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.create_duplicate)) }, onClick = {
                    menu = false
                    onDuplicate(current.id)
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.create_delete)) }, onClick = {
                    menu = false
                    onDelete(current.id)
                })
            }
        }
    }
    val others = list.filter { it.id != current.id }.take(3).map {
        DateUtils.getRelativeTimeSpanString(it.updatedAt, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    }.distinct()
    Text(
        listOfNotNull(
            pluralStringResource(R.plurals.create_sky_items, current.elementCount, current.elementCount),
            if (others.isNotEmpty()) stringResource(R.string.create_sky_swipe, others.joinToString(", ")) else null,
        ).joinToString(" · "),
        style = SkyType.sb.copy(shadow = skyHalo()),
        color = SkyInk.onSkySoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
    )
    if (list.size > 1) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { },
        ) {
            list.indices.take(8).forEach { i ->
                val on = i == pager.currentPage
                Box(Modifier.size(width = if (on) 20.dp else 7.dp, height = 7.dp).background(if (on) crayon.red else Color.White.copy(alpha = 0.85f), CircleShape))
            }
        }
    }
}
