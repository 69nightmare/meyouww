package com.meyouww.feature.studio

import com.meyouww.core.designsystem.component.skyGlass
import com.meyouww.core.designsystem.component.fill
import com.meyouww.core.designsystem.component.SkyType
import com.meyouww.core.designsystem.component.SkyTone
import com.meyouww.core.designsystem.component.SkyInk
import com.meyouww.core.designsystem.component.SkyIcons
import com.meyouww.core.designsystem.component.SkyIconSize
import com.meyouww.core.designsystem.component.SkyIconButton
import com.meyouww.core.designsystem.component.SkyChip
import com.meyouww.core.designsystem.component.SkyButton
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import com.meyouww.core.designsystem.component.skySurface
import com.meyouww.core.designsystem.component.PastelIcons
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.render.SceneRenderer
import com.meyouww.core.scene.Background
import com.meyouww.core.scene.FontChoice
import com.meyouww.core.scene.PhotoElement
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneTransform
import com.meyouww.core.scene.ShapeKind
import com.meyouww.core.scene.StickerElement

private enum class StudioSheet { None, Text, Stickers, Background, Clear }

/** The wallpaper studio: draw, decorate, then save or share. */
@Composable
fun StudioScreen(
    draftId: String?,
    onBack: () -> Unit,
    onPreview: (String) -> Unit,
    startWithPhoto: Boolean = false,
    viewModel: StudioViewModel = hiltViewModel(),
) {
    LaunchedEffect(draftId) { viewModel.open(draftId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.saveNow() }
    var sheet by rememberSaveable { mutableStateOf(StudioSheet.None) }
    val chooserTitle = stringResource(R.string.studio_share_chooser)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, chooserTitle))
                }
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }
    // "Send a photo" from a friend's page: pick it straight away and let it fill the wallpaper.
    var askedForPhoto by rememberSaveable { mutableStateOf(false) }
    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importPhoto(uri, fill = true)
    }
    LaunchedEffect(startWithPhoto, state.loading) {
        if (startWithPhoto && !state.loading && !askedForPhoto) {
            askedForPhoto = true
            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    // Android 8–9 only: saving to the shared Pictures folder needs storage access.
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveToGallery()
    }
    fun saveToGallery() {
        val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        if (legacy && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.saveToGallery()
        }
    }

    // The brush the "Draw" button returns to (pencil, crayon, marker…).
    var brush by rememberSaveable { mutableStateOf(StudioTool.Crayon) }
    LaunchedEffect(state.tool) {
        if (state.tool.drawTool != null && state.tool != StudioTool.Eraser) brush = state.tool
    }

    Column(
        Modifier
            .fillMaxSize()
            .kasukabeBackdrop()
            .safeDrawingPadding(),
    ) {
        TopBar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onBack = {
                viewModel.saveNow()
                onBack()
            },
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onBackground = { sheet = StudioSheet.Background },
            onClear = { sheet = StudioSheet.Clear },
            onSave = ::saveToGallery,
            onShare = viewModel::share,
            onNext = { viewModel.saveThen(onPreview) },
            busy = state.busy,
        )
        state.message?.let {
            CrayonChip(
                text = stringResource(it),
                fill = MeyouwwTheme.crayon.yellow,
                onClick = viewModel::messageShown,
                modifier = Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (MeyouwwTheme.crayon.living) 16.dp else 8.dp, vertical = 4.dp)
                .then(
                    if (MeyouwwTheme.crayon.living) {
                        Modifier
                            .skySurface(Color.White, MeyouwwTheme.crayon.hardShadow, MeyouwwTheme.crayon.shadow, MeyouwwTheme.crayon.sunX, cornerRadius = 28.dp, depth = 4.dp, lift = 16.dp, blur = 24.dp, belly = false)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(22.dp))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (state.loading) {
                WhityLoading(stringResource(R.string.studio_loading), Modifier.align(Alignment.Center))
            } else {
                DrawingSurface(
                    state = state,
                    renderer = viewModel.renderer,
                    onStroke = viewModel::addStroke,
                    onShape = viewModel::addShape,
                    onTextAt = { sheet = StudioSheet.Text },
                    onSelect = viewModel::select,
                    onTransform = viewModel::transformSelected,
                    newId = viewModel::newId,
                )
            }
            if (state.busy) {
                WhityLoading(stringResource(R.string.studio_exporting), Modifier.align(Alignment.Center), sticker = WhitySticker.Pat)
            }
        }
        val selected = state.selected
        if (selected != null) {
            SelectionBar(
                photo = selected as? PhotoElement,
                onDelete = viewModel::deleteSelected,
                onForward = { viewModel.reorderSelected(true) },
                onBackward = { viewModel.reorderSelected(false) },
                onCopy = viewModel::duplicateSelected,
                onDone = { viewModel.select(null) },
                onBrightness = { viewModel.adjustPhoto(brightness = it) },
                onOpacity = { viewModel.adjustPhoto(opacity = it) },
            )
        } else if (MeyouwwTheme.crayon.living) {
            SkyToolPanel(
                state = state,
                viewModel = viewModel,
                onText = { sheet = StudioSheet.Text },
                onStickers = { sheet = StudioSheet.Stickers },
                onPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
        } else {
            OptionsRow(state, viewModel)
        }
        if (!MeyouwwTheme.crayon.living && state.tool != StudioTool.Eraser) ColorRow(state.color, viewModel::setColor)
        if (!MeyouwwTheme.crayon.living) ToolBar(
            current = state.tool,
            brush = brush,
            onDraw = { viewModel.setTool(brush) },
            onTool = viewModel::setTool,
            onText = { sheet = StudioSheet.Text },
            onStickers = { sheet = StudioSheet.Stickers },
            onPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }

    when (sheet) {
        StudioSheet.Text -> TextDialog(
            color = state.color,
            onDismiss = { sheet = StudioSheet.None },
            onAdd = { text, font ->
                sheet = StudioSheet.None
                viewModel.addText(text, font)
            },
        )
        StudioSheet.Stickers -> StickerDialog(
            onDismiss = { sheet = StudioSheet.None },
            onPick = {
                sheet = StudioSheet.None
                viewModel.addSticker(it)
            },
        )
        StudioSheet.Background -> BackgroundDialog(
            renderer = viewModel.renderer,
            aspect = state.scene.aspect,
            onDismiss = { sheet = StudioSheet.None },
            onPick = {
                sheet = StudioSheet.None
                viewModel.setBackground(it)
            },
        )
        StudioSheet.Clear -> AlertDialog(
            onDismissRequest = { sheet = StudioSheet.None },
            title = { Text(stringResource(R.string.studio_clear_title)) },
            text = { Text(stringResource(R.string.studio_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    sheet = StudioSheet.None
                    viewModel.clearAll()
                }) { Text(stringResource(R.string.studio_clear)) }
            },
            dismissButton = { TextButton(onClick = { sheet = StudioSheet.None }) { Text(stringResource(R.string.create_cancel)) } },
        )
        StudioSheet.None -> Unit
    }
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBackground: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onNext: () -> Unit,
    busy: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val compact = LocalConfiguration.current.screenWidthDp < 360
    if (MeyouwwTheme.crayon.living) {
        val crayon = MeyouwwTheme.crayon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)
                .skyGlass(36.dp, crayon.card, crayon.hardShadow, crayon.shadow, crayon.sunX)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 3.dp),
        ) {
            SkyIconButton(SkyIcons.Back, stringResource(R.string.studio_back), onBack, size = SkyIconSize.Small)
            SkyIconButton(SkyIcons.Undo, stringResource(R.string.studio_undo), if (canUndo) onUndo else null, size = SkyIconSize.Small, modifier = Modifier.graphicsLayer { alpha = if (canUndo) 1f else 0.45f })
            SkyIconButton(SkyIcons.Redo, stringResource(R.string.studio_redo), if (canRedo) onRedo else null, size = SkyIconSize.Small, modifier = Modifier.graphicsLayer { alpha = if (canRedo) 1f else 0.45f })
            Box(Modifier.weight(1f))
            Box {
                SkyIconButton(SkyIcons.More, stringResource(R.string.studio_more), { menu = true }, size = SkyIconSize.Small)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_background)) }, onClick = { menu = false; onBackground() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_save_gallery)) }, enabled = !busy, onClick = { menu = false; onSave() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_share)) }, enabled = !busy, onClick = { menu = false; onShare() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.studio_clear)) }, onClick = { menu = false; onClear() })
                }
            }
            SkyButton(stringResource(R.string.studio_sky_next), onNext, small = true, enabled = !busy, modifier = Modifier.padding(bottom = 0.dp))
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            .studioBar(seed = 702, cornerRadius = 18.dp)
            .then(if (compact) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(PastelIcons.Back, contentDescription = stringResource(R.string.studio_back))
        }
        val undo = stringResource(R.string.studio_undo)
        val redo = stringResource(R.string.studio_redo)
        IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.semantics { contentDescription = undo }) {
            Text("↶", style = MaterialTheme.typography.headlineSmall)
        }
        IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.semantics { contentDescription = redo }) {
            Text("↷", style = MaterialTheme.typography.headlineSmall)
        }
        Box(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.studio_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_background)) }, onClick = { menu = false; onBackground() })
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_save_gallery)) }, enabled = !busy, onClick = { menu = false; onSave() })
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_share)) }, enabled = !busy, onClick = { menu = false; onShare() })
                DropdownMenuItem(text = { Text(stringResource(R.string.studio_clear)) }, onClick = { menu = false; onClear() })
            }
        }
        CrayonButton(stringResource(R.string.studio_next), onClick = onNext, enabled = !busy, kind = ButtonKind.Secondary, modifier = Modifier.padding(end = 4.dp))
    }
}

/** One row of big, equal tools that fits any phone width. */
@Composable
private fun ToolBar(
    current: StudioTool,
    brush: StudioTool,
    onDraw: () -> Unit,
    onTool: (StudioTool) -> Unit,
    onText: () -> Unit,
    onStickers: () -> Unit,
    onPhoto: () -> Unit,
) {
    val narrow = LocalConfiguration.current.screenWidthDp < 360
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .studioBar(seed = 703, cornerRadius = 20.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val drawing = current.drawTool != null && current != StudioTool.Eraser
        ToolItem(toolEmoji(brush), stringResource(R.string.tool_draw), drawing, narrow, onDraw)
        ToolItem("🧽", stringResource(R.string.tool_eraser), current == StudioTool.Eraser, narrow) { onTool(StudioTool.Eraser) }
        ToolItem("💖", stringResource(R.string.tool_shape), current == StudioTool.Shape, narrow) { onTool(StudioTool.Shape) }
        ToolItem("🔤", stringResource(R.string.tool_text), current == StudioTool.Text, narrow, onText)
        ToolItem("🐶", stringResource(R.string.tool_sticker), false, narrow, onStickers)
        ToolItem("🖼️", stringResource(R.string.tool_photo), false, narrow, onPhoto)
        ToolItem("✋", stringResource(R.string.tool_move), current == StudioTool.Move, narrow) { onTool(StudioTool.Move) }
    }
}

@Composable
private fun RowScope.ToolItem(emoji: String, label: String, chosen: Boolean, narrow: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (chosen) MeyouwwTheme.crayon.lilac else Color.Transparent)
            .border(if (chosen) 2.dp else 0.dp, if (chosen) MaterialTheme.colorScheme.onBackground else Color.Transparent, shape)
            .clickable(role = Role.Tab, onClickLabel = label, onClick = onClick)
            .padding(vertical = 6.dp)
            .semantics {
                selected = chosen
                contentDescription = label
            },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        if (!narrow) Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun toolEmoji(tool: StudioTool): String = when (tool) {
    StudioTool.Pencil -> "✏️"
    StudioTool.Crayon -> "🖍️"
    StudioTool.Marker -> "🖊️"
    StudioTool.Highlighter -> "🖌️"
    StudioTool.Neon -> "✨"
    StudioTool.Watercolor -> "💧"
    else -> "🖍️"
}

private val BRUSHES = listOf(StudioTool.Pencil, StudioTool.Crayon, StudioTool.Marker, StudioTool.Highlighter, StudioTool.Neon, StudioTool.Watercolor)

/** Only what the current tool needs: brush + size, shape + fill + size, or eraser size. */
@Composable
private fun OptionsRow(state: StudioUiState, viewModel: StudioViewModel) {
    val tool = state.tool
    if (tool.drawTool == null && tool != StudioTool.Shape) return
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        when {
            tool == StudioTool.Shape -> Box {
                CrayonChip(
                    text = stringResource(shapeLabel(state.shape)) + " ▾",
                    fill = MaterialTheme.colorScheme.surface,
                    onClick = { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    ShapeKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(stringResource(shapeLabel(kind))) }, onClick = {
                            menu = false
                            viewModel.setShape(kind)
                        })
                    }
                    DropdownMenuItem(
                        text = { Text((if (state.shapeFilled) "● " else "○ ") + stringResource(R.string.shape_filled)) },
                        onClick = {
                            menu = false
                            viewModel.setShapeFilled(!state.shapeFilled)
                        },
                    )
                }
            }
            tool != StudioTool.Eraser -> Box {
                CrayonChip(
                    text = "${toolEmoji(tool)} ${stringResource(tool.label)} ▾",
                    fill = MaterialTheme.colorScheme.surface,
                    onClick = { menu = true },
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    BRUSHES.forEach { b ->
                        DropdownMenuItem(text = { Text("${toolEmoji(b)}  ${stringResource(b.label)}") }, onClick = {
                            menu = false
                            viewModel.setTool(b)
                        })
                    }
                }
            }
        }
        Slider(
            value = state.size,
            onValueChange = viewModel::setSize,
            valueRange = 0.003f..0.07f,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorRow(current: Int, onColor: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Palette.colors.forEach { pc ->
            val name = stringResource(pc.name)
            val chosen = pc.color == current
            val label = if (chosen) stringResource(R.string.color_selected, name) else stringResource(R.string.color_named, name)
            Box(
                Modifier
                    .size(if (chosen) 34.dp else 28.dp)
                    .clip(CircleShape)
                    .background(Color(pc.color))
                    // The chosen colour gets a cocoa ring, so it reads without telling colours apart.
                    .border(if (chosen) 3.dp else 1.5.dp, if (chosen) MaterialTheme.colorScheme.onBackground else MeyouwwTheme.crayon.edge, CircleShape)
                    .clickable(role = Role.RadioButton) { onColor(pc.color) }
                    .semantics { contentDescription = label },
            )
        }
    }
}

private fun shapeLabel(kind: ShapeKind): Int = when (kind) {
    ShapeKind.Rectangle -> R.string.shape_rectangle
    ShapeKind.Oval -> R.string.shape_oval
    ShapeKind.Heart -> R.string.shape_heart
    ShapeKind.Star -> R.string.shape_star
    ShapeKind.Line -> R.string.shape_line
    ShapeKind.Arrow -> R.string.shape_arrow
}

/** Small icon row for the picked item; photo sliders only for photos. */
@Composable
private fun SelectionBar(
    photo: PhotoElement?,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    onBrightness: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onDelete) { Icon(PastelIcons.Trash, contentDescription = stringResource(R.string.sel_delete)) }
            TextButton(onClick = onCopy) { Text(stringResource(R.string.sel_copy)) }
            IconButton(onClick = onForward) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.sel_forward)) }
            IconButton(onClick = onBackward) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.sel_backward)) }
            Text(
                stringResource(R.string.sel_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MeyouwwTheme.crayon.textSoft,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.sel_done)) }
        }
        if (photo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sel_photo_brightness), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                Slider(value = photo.brightness, onValueChange = onBrightness, valueRange = -1f..1f, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sel_photo_opacity), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                Slider(value = photo.opacity, onValueChange = onOpacity, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextDialog(color: Int, onDismiss: () -> Unit, onAdd: (String, FontChoice) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var font by rememberSaveable { mutableStateOf(FontChoice.Fredoka) }
    val fonts = listOf(
        FontChoice.Fredoka to R.string.text_font_round,
        FontChoice.Handwritten to R.string.text_font_hand,
        FontChoice.Pop to R.string.text_font_pop,
        FontChoice.Nunito to R.string.text_font_plain,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.text_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CrayonTextField(value = text, onValueChange = { text = it.take(200) }, label = stringResource(R.string.text_hint), singleLine = false)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fonts.forEach { (choice, label) ->
                        CrayonChip(
                            text = stringResource(label),
                            fill = if (choice == font) Color(color).copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
                            onClick = { font = choice },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(text, font) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.text_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.create_cancel)) } },
    )
}

private val EMOJI = listOf("💖", "💌", "🌸", "⭐", "🌈", "☁️", "🎀", "🍓", "🐾", "✨", "🎈", "🌙", "☀️", "🍰", "🧸", "🌻")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sticker_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WhitySticker.entries.forEach { sticker ->
                        val label = stringResource(sticker.label)
                        Image(
                            painter = painterResource(sticker.drawable),
                            contentDescription = label,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onPick(sticker.name) },
                        )
                    }
                }
                Text(stringResource(R.string.sticker_emoji), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EMOJI.forEach { e ->
                        Text(
                            e,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.clickable(role = Role.Button) { onPick(StickerElement.EMOJI_PREFIX + e) }.padding(4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private val SOLIDS = listOf(0xFFFFF6E0, 0xFFFFE0EC, 0xFFDDF3FF, 0xFFE3F7D9, 0xFFFFF2B3, 0xFFEDE3FF, 0xFF231B1E, 0xFFFFFFFF).map { it.toInt() }
private val GRADIENTS = listOf(
    0xFFFFD6E8 to 0xFFFFE9C7,
    0xFFBDE7FF to 0xFFFFF6E0,
    0xFFE6D9FF to 0xFFFFD6E8,
    0xFF1F2A5C to 0xFF4A3F8C,
).map { (a, b) -> a.toInt() to b.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackgroundDialog(renderer: SceneRenderer, aspect: Float, onDismiss: () -> Unit, onPick: (Background) -> Unit) {
    val names = mapOf(
        "sky" to R.string.bg_sky,
        "night" to R.string.bg_night,
        "meadow" to R.string.bg_meadow,
        "candy" to R.string.bg_candy,
        "sea" to R.string.bg_sea,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bg_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.bg_scenes), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Background.presets.forEach { id ->
                        val label = stringResource(names[id] ?: R.string.bg_sky)
                        val thumb = remember(id, aspect) {
                            val w = 90
                            val h = (w * aspect).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            renderer.draw(android.graphics.Canvas(bmp), Scene(aspect = aspect, background = Background.Preset(id)), SceneTransform.cover(aspect, w, h))
                            bmp.asImageBitmap()
                        }
                        Image(
                            bitmap = thumb,
                            contentDescription = label,
                            modifier = Modifier
                                .width(64.dp)
                                .height((64 * aspect).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(2.dp, MeyouwwTheme.crayon.outline, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { onPick(Background.Preset(id)) },
                        )
                    }
                }
                Text(stringResource(R.string.bg_colors), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SOLIDS.forEach { c ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(Color(c)).border(2.dp, MeyouwwTheme.crayon.outline, CircleShape)
                                .clickable(role = Role.Button) { onPick(Background.Solid(c)) },
                        )
                    }
                }
                Text(stringResource(R.string.bg_gradients), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GRADIENTS.forEach { (top, bottom) ->
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(top), Color(bottom))))
                                .border(2.dp, MeyouwwTheme.crayon.outline, CircleShape)
                                .clickable(role = Role.Button) { onPick(Background.Gradient(top, bottom)) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** The studio's top and bottom bars: outlined paper, or glass floating over the sky in the Living Sky look. */
@Composable
private fun Modifier.studioBar(seed: Int, cornerRadius: androidx.compose.ui.unit.Dp): Modifier {
    val crayon = MeyouwwTheme.crayon
    return if (crayon.living) {
        skySurface(
            fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            edge = crayon.hardShadow,
            shadow = crayon.shadow,
            sunX = crayon.sunX,
            cornerRadius = cornerRadius + 8.dp,
            depth = 4.dp,
            lift = 14.dp,
            blur = 22.dp,
            belly = false,
        )
    } else {
        crayonSurface(fill = MaterialTheme.colorScheme.surface, outline = crayon.outline, seed = seed, cornerRadius = cornerRadius, strokeWidth = 2.dp)
    }
}

/** One tool in the Living Sky studio: an icon (or a little picture) over its name. */
private class SkyTool(val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector?, val emoji: String?, val chosen: (StudioUiState) -> Boolean, val onClick: () -> Unit)

/**
 * The Living Sky studio's panel, as on the board: the colours as round swatches, the size slider,
 * and the tools in a row (the chosen one on a pink pill): the brushes, eraser, shapes, text,
 * stickers, a photo and moving things about.
 */
@Composable
private fun SkyToolPanel(state: StudioUiState, viewModel: StudioViewModel, onText: () -> Unit, onStickers: () -> Unit, onPhoto: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
            .skyGlass(28.dp, crayon.card, crayon.hardShadow, crayon.shadow, crayon.sunX)
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
    ) {
        if (state.tool != StudioTool.Eraser) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp),
            ) {
                Palette.colors.forEach { pc ->
                    val name = stringResource(pc.name)
                    val chosen = pc.color == state.color
                    val label = if (chosen) stringResource(R.string.color_selected, name) else stringResource(R.string.color_named, name)
                    val ring = MaterialTheme.colorScheme.onSurface
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.RadioButton) { viewModel.setColor(pc.color) }
                            .semantics {
                                contentDescription = label
                                selected = chosen
                            },
                    ) {
                        Box(
                            Modifier
                                .size(if (chosen) 36.dp else 32.dp)
                                .drawBehind {
                                    if (chosen) drawCircle(ring, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                                }
                                .padding(if (chosen) 5.dp else 0.dp)
                                .skySurface(Color(pc.color), Color(pc.color).copy(alpha = 0.6f), Color.Transparent, 0f, cornerRadius = 18.dp, depth = 2.dp, belly = false),
                        )
                    }
                }
            }
        }
        if (state.tool.drawTool != null || state.tool == StudioTool.Shape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(stringResource(R.string.studio_sky_size), style = SkyType.cap, color = crayon.textSoft)
                Slider(value = state.size, onValueChange = viewModel::setSize, valueRange = 0.003f..0.07f, modifier = Modifier.weight(1f))
            }
        }
        if (state.tool == StudioTool.Shape) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ShapeKind.entries.forEach { kind ->
                    SkyChip(stringResource(shapeLabel(kind)), selected = state.shape == kind, height = 34.dp, onClick = { viewModel.setShape(kind) })
                }
                SkyChip(stringResource(R.string.shape_filled), selected = state.shapeFilled, height = 34.dp, onClick = { viewModel.setShapeFilled(!state.shapeFilled) })
            }
        }
        val tools = listOf(
            SkyTool(StudioTool.Crayon.label, SkyIcons.Pencil, null, { it.tool == StudioTool.Crayon }) { viewModel.setTool(StudioTool.Crayon) },
            SkyTool(StudioTool.Marker.label, SkyIcons.Marker, null, { it.tool == StudioTool.Marker }) { viewModel.setTool(StudioTool.Marker) },
            SkyTool(StudioTool.Neon.label, SkyIcons.Spark, null, { it.tool == StudioTool.Neon }) { viewModel.setTool(StudioTool.Neon) },
            SkyTool(R.string.tool_eraser, SkyIcons.Eraser, null, { it.tool == StudioTool.Eraser }) { viewModel.setTool(StudioTool.Eraser) },
            SkyTool(R.string.tool_shape, SkyIcons.Shapes, null, { it.tool == StudioTool.Shape }) { viewModel.setTool(StudioTool.Shape) },
            SkyTool(R.string.tool_text, SkyIcons.Text, null, { it.tool == StudioTool.Text }, onText),
            SkyTool(R.string.tool_sticker, SkyIcons.Sticker, null, { false }, onStickers),
            SkyTool(R.string.tool_photo, SkyIcons.Image, null, { false }, onPhoto),
            SkyTool(StudioTool.Pencil.label, null, "✏️", { it.tool == StudioTool.Pencil }) { viewModel.setTool(StudioTool.Pencil) },
            SkyTool(StudioTool.Highlighter.label, null, "🖌️", { it.tool == StudioTool.Highlighter }) { viewModel.setTool(StudioTool.Highlighter) },
            SkyTool(StudioTool.Watercolor.label, null, "💧", { it.tool == StudioTool.Watercolor }) { viewModel.setTool(StudioTool.Watercolor) },
            SkyTool(R.string.tool_move, null, "✋", { it.tool == StudioTool.Move }) { viewModel.setTool(StudioTool.Move) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            tools.forEach { tool ->
                val on = tool.chosen(state)
                val label = stringResource(tool.label)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .then(
                            if (on) {
                                Modifier.skySurface(SkyTone.Pink.fill(), crayon.pressShadowFor(SkyTone.Pink.fill()), Color.Transparent, 0f, cornerRadius = 16.dp, depth = 3.dp, belly = false)
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab, onClickLabel = label, onClick = tool.onClick)
                        .semantics {
                            selected = on
                            contentDescription = label
                        }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    val ink = if (on) SkyInk.onPastel else MaterialTheme.colorScheme.onSurface
                    if (tool.icon != null) {
                        Icon(tool.icon, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                    } else {
                        Text(tool.emoji.orEmpty(), fontSize = 18.sp, lineHeight = 22.sp)
                    }
                    Text(label, style = SkyType.pill.copy(fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = ink, maxLines = 1)
                }
            }
        }
    }
}
