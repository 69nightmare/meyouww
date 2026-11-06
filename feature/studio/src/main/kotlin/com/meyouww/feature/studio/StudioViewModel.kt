package com.meyouww.feature.studio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meyouww.core.database.DraftStore
import com.meyouww.core.render.AndroidRenderAssets
import com.meyouww.core.render.PhotoSanitizer
import com.meyouww.core.render.SceneExporter
import com.meyouww.core.render.SceneRenderer
import com.meyouww.core.scene.Background
import com.meyouww.core.scene.Element
import com.meyouww.core.scene.FontChoice
import com.meyouww.core.scene.History
import com.meyouww.core.scene.PhotoElement
import com.meyouww.core.scene.Placed
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneEdits
import com.meyouww.core.scene.ShapeElement
import com.meyouww.core.scene.ShapeKind
import com.meyouww.core.scene.StickerElement
import com.meyouww.core.scene.TextElement
import com.meyouww.core.scene.Tool
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/** What a finger does on the canvas. */
enum class StudioTool(val drawTool: Tool?, @param:StringRes val label: Int) {
    Move(null, R.string.tool_move),
    Pencil(Tool.Pencil, R.string.tool_pencil),
    Crayon(Tool.Crayon, R.string.tool_crayon),
    Marker(Tool.Marker, R.string.tool_marker),
    Highlighter(Tool.Highlighter, R.string.tool_highlighter),
    Neon(Tool.Neon, R.string.tool_neon),
    Watercolor(Tool.Watercolor, R.string.tool_watercolor),
    Eraser(Tool.Eraser, R.string.tool_eraser),
    Shape(null, R.string.tool_shape),
    Text(null, R.string.tool_text),
}

data class StudioUiState(
    val loading: Boolean = true,
    val scene: Scene = Scene(),
    val tool: StudioTool = StudioTool.Crayon,
    val color: Int = Palette.colors.first().color,
    val size: Float = 0.016f,
    val opacity: Float = 1f,
    val shape: ShapeKind = ShapeKind.Heart,
    val shapeFilled: Boolean = false,
    val selectedId: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
) {
    val selected: Placed? get() = selectedId?.let { scene.element(it) as? Placed }
}

/** One-off things the screen must do (open a share sheet). */
sealed interface StudioEvent {
    data class Share(val uri: Uri, val mimeType: String) : StudioEvent
}

@HiltViewModel
class StudioViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val drafts: DraftStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var draftId: String? = savedState[KEY_DRAFT]

    /** Used only on the main thread, for the canvas. */
    val renderer = SceneRenderer(AndroidRenderAssets(context) { draftId?.let(drafts::dir) })

    /** A separate renderer for thumbnails and exports on background threads. */
    private val exporter = SceneExporter(SceneRenderer(AndroidRenderAssets(context) { draftId?.let(drafts::dir) }))

    private val history = History()
    private val saveLock = Mutex()
    private var dirty = false
    private var lastTransformAt = 0L

    private val _state = MutableStateFlow(StudioUiState())
    val state: StateFlow<StudioUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StudioEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StudioEvent> = _events.asSharedFlow()

    private var opened = false

    /** Opens a draft, or starts a new one shaped like this phone's screen. */
    fun open(requestedId: String?) {
        if (opened) return
        opened = true
        viewModelScope.launch {
            val id = draftId ?: requestedId?.takeIf { drafts.exists(it) }
            val scene = id?.let { drafts.load(it) }
            if (id != null && scene != null) {
                draftId = id
                _state.update { it.copy(loading = false, scene = scene) }
            } else {
                val fresh = Scene(aspect = screenAspect())
                val newId = drafts.create(fresh, "")
                draftId = newId
                savedState[KEY_DRAFT] = newId
                _state.update { it.copy(loading = false, scene = fresh) }
            }
            savedState[KEY_DRAFT] = draftId
            autosaveLoop()
        }
    }

    // ------------------------------------------------------------ tool settings

    fun setTool(tool: StudioTool) = _state.update { it.copy(tool = tool, selectedId = if (tool == StudioTool.Move) it.selectedId else null) }
    fun setColor(color: Int) {
        _state.update { it.copy(color = color) }
        // Recolour the selected text or shape straight away.
        when (val sel = _state.value.selected) {
            is TextElement -> commit(SceneEdits.replace(scene(), sel.copy(color = color)))
            is ShapeElement -> commit(SceneEdits.replace(scene(), sel.copy(color = color)))
            else -> Unit
        }
    }
    fun setSize(size: Float) = _state.update { it.copy(size = size) }
    fun setOpacity(opacity: Float) = _state.update { it.copy(opacity = opacity) }
    fun setShape(kind: ShapeKind) = _state.update { it.copy(shape = kind) }
    fun setShapeFilled(filled: Boolean) = _state.update { it.copy(shapeFilled = filled) }
    fun messageShown() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------ edits

    fun addElement(element: Element) {
        commit(SceneEdits.add(scene(), element))
        if (element is Placed) _state.update { it.copy(tool = StudioTool.Move, selectedId = element.id) }
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun addText(text: String, font: FontChoice) {
        val clean = text.trim().take(200)
        if (clean.isEmpty()) return
        val s = scene()
        addElement(TextElement(newId(), clean, 0.5f, s.aspect * 0.4f, 0.09f, _state.value.color, font))
    }

    fun addSticker(name: String) {
        val s = scene()
        addElement(StickerElement(newId(), name, 0.5f, s.aspect * 0.5f, 0.32f))
    }

    fun addShape(x1: Float, y1: Float, x2: Float, y2: Float) {
        val st = _state.value
        val w = x2 - x1
        val h = y2 - y1
        if (max(kotlin.math.abs(w), kotlin.math.abs(h)) < 0.02f) return
        val lineLike = st.shape == ShapeKind.Line || st.shape == ShapeKind.Arrow
        val element = ShapeElement(
            id = newId(),
            kind = st.shape,
            x = (x1 + x2) / 2f,
            y = (y1 + y2) / 2f,
            // Lines keep their direction; boxes use positive sizes.
            w = if (lineLike) w else kotlin.math.abs(w),
            h = if (lineLike) h else kotlin.math.abs(h),
            color = st.color,
            strokeWidth = st.size,
            filled = st.shapeFilled,
        )
        commit(SceneEdits.add(scene(), element))
    }

    fun importPhoto(uri: Uri) {
        val id = draftId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val file = drafts.newPhotoFile(id)
            val ratio = withContext(Dispatchers.IO) {
                runCatching { PhotoSanitizer.importInto({ context.contentResolver.openInputStream(uri) }, file) }.getOrNull()
            }
            _state.update { it.copy(busy = false) }
            if (ratio == null) {
                file.delete()
                _state.update { it.copy(message = R.string.studio_photo_failed) }
                return@launch
            }
            val s = scene()
            addElement(PhotoElement(newId(), file.name, 0.5f, s.aspect / 2f, 0.85f, ratio))
        }
    }

    fun setBackground(background: Background) = commit(SceneEdits.setBackground(scene(), background))

    fun clearAll() = commit(SceneEdits.clear(scene())).also { _state.update { it.copy(selectedId = null) } }

    /** Finished freehand stroke from the canvas. */
    fun addStroke(element: Element) = commit(SceneEdits.add(scene(), element))

    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    /** Drag, pinch and twist of the selected element. Recorded in history once per gesture. */
    fun transformSelected(dx: Float, dy: Float, zoom: Float, rotation: Float) {
        val sel = _state.value.selected ?: return
        val now = System.currentTimeMillis()
        if (now - lastTransformAt > GESTURE_GAP_MS) history.record(scene())
        lastTransformAt = now
        var moved = SceneEdits.moveBy(sel, dx, dy)
        if (zoom != 1f || rotation != 0f) moved = SceneEdits.scaleRotate(moved, zoom, rotation)
        replaceQuietly(SceneEdits.replace(scene(), moved))
    }

    fun adjustPhoto(brightness: Float? = null, opacity: Float? = null) {
        val photo = _state.value.selected as? PhotoElement ?: return
        val now = System.currentTimeMillis()
        if (now - lastTransformAt > GESTURE_GAP_MS) history.record(scene())
        lastTransformAt = now
        replaceQuietly(
            SceneEdits.replace(
                scene(),
                photo.copy(brightness = brightness ?: photo.brightness, opacity = (opacity ?: photo.opacity).coerceIn(0.1f, 1f)),
            ),
        )
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        commit(SceneEdits.remove(scene(), id))
        _state.update { it.copy(selectedId = null) }
    }

    fun reorderSelected(up: Boolean) {
        val id = _state.value.selectedId ?: return
        commit(SceneEdits.reorder(scene(), id, up))
    }

    fun duplicateSelected() {
        val id = _state.value.selectedId ?: return
        val newId = newId()
        commit(SceneEdits.duplicate(scene(), id, newId))
        _state.update { it.copy(selectedId = newId) }
    }

    fun undo() {
        val previous = history.undo(scene()) ?: return
        setScene(previous)
    }

    fun redo() {
        val next = history.redo(scene()) ?: return
        setScene(next)
    }

    // ------------------------------------------------------------ saving and export

    /** Called when the screen stops: never lose work. */
    fun saveNow() {
        viewModelScope.launch { withContext(NonCancellable) { save() } }
    }

    fun saveToGallery() = exportThen { image ->
        val name = "meyouww_${System.currentTimeMillis()}.${image.extension}"
        val ok = withContext(Dispatchers.IO) { runCatching { writeToGallery(name, image.mimeType, image.bytes) }.isSuccess }
        _state.update { it.copy(message = if (ok) R.string.studio_saved else R.string.studio_save_failed) }
    }

    fun share() = exportThen { image ->
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "meyouww_${System.currentTimeMillis()}.${image.extension}")
            file.writeBytes(image.bytes)
            FileProvider.getUriForFile(context, context.packageName + ".files", file)
        }
        _events.tryEmit(StudioEvent.Share(uri, image.mimeType))
    }

    private fun exportThen(block: suspend (com.meyouww.core.render.ExportedImage) -> Unit) {
        val scene = scene()
        viewModelScope.launch {
            _state.update { it.copy(busy = true, selectedId = null) }
            val image = withContext(Dispatchers.Default) { runCatching { exporter.export(scene) }.getOrNull() }
            if (image == null) {
                _state.update { it.copy(busy = false, message = R.string.studio_save_failed) }
            } else {
                block(image)
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun writeToGallery(name: String, mime: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/meyouww")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "meyouww").apply { mkdirs() }
            val file = File(dir, name)
            file.writeBytes(bytes)
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf(mime), null)
        }
    }

    private suspend fun save() {
        val id = draftId ?: return
        if (!dirty) return
        saveLock.withLock {
            val scene = scene()
            dirty = false
            val thumb = withContext(Dispatchers.Default) { runCatching { exporter.thumbnail(scene) }.getOrNull() }
            runCatching { drafts.save(id, scene, thumb) }.onFailure { dirty = true }
            thumb?.recycle()
        }
    }

    private suspend fun autosaveLoop() {
        while (viewModelScope.isActive) {
            delay(AUTOSAVE_MS)
            if (dirty) save()
        }
    }

    // ------------------------------------------------------------ helpers

    private fun scene(): Scene = _state.value.scene

    private fun commit(newScene: Scene) {
        if (newScene == scene()) return
        history.record(scene())
        setScene(newScene)
    }

    private fun replaceQuietly(newScene: Scene) {
        dirty = true
        _state.update { it.copy(scene = newScene, canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    private fun setScene(newScene: Scene) {
        dirty = true
        _state.update {
            it.copy(
                scene = newScene,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                selectedId = it.selectedId?.takeIf { id -> newScene.element(id) != null },
            )
        }
    }

    /** Portrait height/width of this phone's full screen. */
    private fun screenAspect(): Float {
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        if (w <= 0 || h <= 0) return Scene.DEFAULT_ASPECT
        return (max(w, h).toFloat() / min(w, h)).coerceIn(1.2f, 2.6f)
    }

    private companion object {
        const val KEY_DRAFT = "draft_id"
        const val AUTOSAVE_MS = 3_000L
        const val GESTURE_GAP_MS = 600L
    }
}
