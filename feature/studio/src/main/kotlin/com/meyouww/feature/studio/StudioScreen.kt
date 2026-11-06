package com.meyouww.feature.studio

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonTextField
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.component.WhitySticker
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            onPreview = { viewModel.saveThen(onPreview) },
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
        Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
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
                isPhoto = selected is PhotoElement,
                photo = selected as? PhotoElement,
                onDelete = viewModel::deleteSelected,
                onForward = { viewModel.reorderSelected(true) },
                onBackward = { viewModel.reorderSelected(false) },
                onCopy = viewModel::duplicateSelected,
                onDone = { viewModel.select(null) },
                onBrightness = { viewModel.adjustPhoto(brightness = it) },
                onOpacity = { viewModel.adjustPhoto(opacity = it) },
            )
        }
        ToolRow(
            current = state.tool,
            onTool = viewModel::setTool,
            onStickers = { sheet = StudioSheet.Stickers },
            onPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
        BrushPanel(state, viewModel)
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
    onPreview: () -> Unit,
    busy: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.studio_back))
        }
        TextButton(onClick = onUndo, enabled = canUndo) { Text("↶ " + stringResource(R.string.studio_undo)) }
        TextButton(onClick = onRedo, enabled = canRedo) { Text(stringResource(R.string.studio_redo) + " ↷") }
        Box(Modifier.weight(1f))
        IconButton(onClick = onClear) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.studio_clear)) }
        IconButton(onClick = onBackground) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.studio_background)) }
        IconButton(onClick = onShare, enabled = !busy) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.studio_share)) }
        IconButton(onClick = onSave, enabled = !busy) { Icon(Icons.Filled.Done, contentDescription = stringResource(R.string.studio_save_gallery)) }
        TextButton(onClick = onPreview, enabled = !busy) { Text(stringResource(R.string.studio_preview)) }
    }
}

@Composable
private fun ToolRow(current: StudioTool, onTool: (StudioTool) -> Unit, onStickers: () -> Unit, onPhoto: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        StudioTool.entries.forEach { tool ->
            val label = stringResource(tool.label)
            val chosen = tool == current
            CrayonChip(
                text = "${toolEmoji(tool)} $label",
                fill = if (chosen) crayon.yellow else MaterialTheme.colorScheme.surface,
                onClick = { onTool(tool) },
                modifier = Modifier.semantics { selected = chosen },
            )
        }
        CrayonChip(text = "🐶 " + stringResource(R.string.tool_sticker), fill = MaterialTheme.colorScheme.surface, onClick = onStickers)
        CrayonChip(text = "🖼️ " + stringResource(R.string.tool_photo), fill = MaterialTheme.colorScheme.surface, onClick = onPhoto)
    }
}

private fun toolEmoji(tool: StudioTool): String = when (tool) {
    StudioTool.Move -> "✋"
    StudioTool.Pencil -> "✏️"
    StudioTool.Crayon -> "🖍️"
    StudioTool.Marker -> "🖊️"
    StudioTool.Highlighter -> "🖌️"
    StudioTool.Neon -> "✨"
    StudioTool.Watercolor -> "💧"
    StudioTool.Eraser -> "🧽"
    StudioTool.Shape -> "💖"
    StudioTool.Text -> "🔤"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrushPanel(state: StudioUiState, viewModel: StudioViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        ) {
            Palette.colors.forEach { pc ->
                val name = stringResource(pc.name)
                val chosen = pc.color == state.color
                val label = if (chosen) stringResource(R.string.color_selected, name) else stringResource(R.string.color_named, name)
                Box(
                    Modifier
                        .size(if (chosen) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(Color(pc.color))
                        .border(if (chosen) 4.dp else 2.dp, MeyouwwTheme.crayon.outline, CircleShape)
                        .clickable(role = Role.RadioButton) { viewModel.setColor(pc.color) }
                        .semantics { contentDescription = label },
                )
            }
        }
        if (state.tool == StudioTool.Shape) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShapeKind.entries.forEach { kind ->
                    CrayonChip(
                        text = stringResource(shapeLabel(kind)),
                        fill = if (kind == state.shape) MeyouwwTheme.crayon.bubblegum else MaterialTheme.colorScheme.surface,
                        onClick = { viewModel.setShape(kind) },
                    )
                }
                CrayonChip(
                    text = (if (state.shapeFilled) "● " else "○ ") + stringResource(R.string.shape_filled),
                    fill = if (state.shapeFilled) MeyouwwTheme.crayon.bubblegum else MaterialTheme.colorScheme.surface,
                    onClick = { viewModel.setShapeFilled(!state.shapeFilled) },
                )
            }
        }
        if (state.tool.drawTool != null || state.tool == StudioTool.Shape) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                Slider(value = state.size, onValueChange = viewModel::setSize, valueRange = 0.003f..0.07f, modifier = Modifier.weight(1f))
            }
        }
        if (state.tool.drawTool != null && state.tool != StudioTool.Eraser) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.brush_opacity), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(72.dp))
                Slider(value = state.opacity, onValueChange = viewModel::setOpacity, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
            }
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

@Composable
private fun SelectionBar(
    isPhoto: Boolean,
    photo: PhotoElement?,
    onDelete: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    onBrightness: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(stringResource(R.string.sel_hint), style = MaterialTheme.typography.bodySmall, color = MeyouwwTheme.crayon.textSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            CrayonButton(stringResource(R.string.sel_delete), onClick = onDelete, kind = ButtonKind.Quiet)
            CrayonButton(stringResource(R.string.sel_forward), onClick = onForward, kind = ButtonKind.Quiet)
            CrayonButton(stringResource(R.string.sel_backward), onClick = onBackward, kind = ButtonKind.Quiet)
            CrayonButton(stringResource(R.string.sel_copy), onClick = onCopy, kind = ButtonKind.Quiet)
            CrayonButton(stringResource(R.string.sel_done), onClick = onDone, kind = ButtonKind.Secondary)
        }
        if (isPhoto && photo != null) {
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
