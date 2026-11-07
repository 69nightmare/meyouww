package com.meyouww.feature.studio

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceProfileRepository
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.wallpaper.WallpaperApplier
import com.meyouww.core.database.DraftStore
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonChip
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.SwitchRow
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.Capability
import com.meyouww.core.model.ContactDevice
import com.meyouww.core.model.Friend
import com.meyouww.core.model.PhonePreset
import com.meyouww.core.model.PreviewTarget
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.notifications.push.SendWallpaperWorker
import com.meyouww.core.render.AndroidRenderAssets
import com.meyouww.core.render.SceneExporter
import com.meyouww.core.render.SceneRenderer
import com.meyouww.core.scene.Scene
import com.meyouww.core.scene.SceneTransform
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject

data class PreviewUiState(
    val scene: Scene? = null,
    val friends: List<Friend> = emptyList(),
    val friendId: String? = null,
    val devices: List<ContactDevice> = emptyList(),
    val deviceIndex: Int = 0,
    val preset: PhonePreset = PhonePreset.Typical,
    val lock: Boolean = false,
    val after: Boolean = true,
    val guides: Boolean = true,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val image: ImageBitmap? = null,
    val sendTarget: WallpaperTarget = WallpaperTarget.Lock,
    val mine: PreviewTarget? = null,
    val applying: Boolean = false,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val message: Int? = null,
) {
    val device: ContactDevice? get() = devices.getOrNull(deviceIndex)
    /** A friend's screen when one is picked (or a standard phone), otherwise this phone's. */
    val target: PreviewTarget
        get() = device?.let(PreviewTarget::of) ?: if (friendId == null) mine ?: preset.target else preset.target
    val friend: Friend? get() = friends.firstOrNull { it.id == friendId }
}

@HiltViewModel
class PreviewViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val drafts: DraftStore,
    private val friendsRepo: FriendsRepository,
    private val profiles: DeviceProfileRepository,
) : ViewModel() {
    private var draftId: String? = null
    private val renderThread = Executors.newSingleThreadExecutor()
    private val renderDispatcher = renderThread.asCoroutineDispatcher()

    /** Used only on [renderDispatcher]. Same renderer and cover transform as the export. */
    private val renderer = SceneRenderer(AndroidRenderAssets(context) { draftId?.let(drafts::dir) })
    private val exporter = SceneExporter(renderer)
    private var renderJob: Job? = null

    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    fun open(id: String, friendId: String?) {
        if (draftId != null) return
        draftId = id
        viewModelScope.launch {
            val scene = drafts.load(id) ?: Scene()
            _state.update { it.copy(scene = scene, mine = myScreen(), friends = friendsRepo.friends.value.orEmpty()) }
            if (friendsRepo.friends.value == null) {
                friendsRepo.refreshFriends()
                _state.update { it.copy(friends = friendsRepo.friends.value.orEmpty()) }
            }
            chooseFriend(friendId)
        }
    }

    fun chooseFriend(id: String?) {
        _state.update { it.copy(friendId = id, devices = emptyList(), deviceIndex = 0) }
        if (id == null) {
            render()
            return
        }
        viewModelScope.launch {
            val devices = (profiles.contactDevices(id) as? Outcome.Success)?.value.orEmpty()
            if (_state.value.friendId == id) _state.update { it.copy(devices = devices) }
            render()
        }
    }

    fun chooseDevice(index: Int) = change { it.copy(deviceIndex = index) }
    fun choosePreset(preset: PhonePreset) = change { it.copy(preset = preset) }
    fun setSendTarget(target: WallpaperTarget) = _state.update { it.copy(sendTarget = target, message = null) }

    /** Puts it on this phone straight away, on the screen(s) picked above. */
    fun setOnMyPhone() {
        val s = _state.value
        val scene = s.scene ?: return
        if (s.applying) return
        val screen = s.mine ?: PhonePreset.Typical.target
        _state.update { it.copy(applying = true, message = null) }
        viewModelScope.launch {
            val ok = withContext(renderDispatcher) {
                runCatching {
                    val bitmap = exporter.render(scene, screen.widthPx, screen.heightPx, s.focusX, s.focusY)
                    try {
                        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperApplier.flags(s.sendTarget))
                    } finally {
                        bitmap.recycle()
                    }
                }.isSuccess
            }
            _state.update { it.copy(applying = false, message = if (ok) R.string.set_mine_done else R.string.set_mine_failed) }
        }
    }

    /** This phone's full screen size in pixels, portrait. */
    private fun myScreen(): PreviewTarget {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }
        val base = PhonePreset.Typical.target
        return base.copy(widthPx = minOf(w, h).coerceAtLeast(320), heightPx = maxOf(w, h).coerceAtLeast(480), estimate = false)
    }

    /**
     * Renders the wallpaper at the recipient's resolution (same crop as the preview),
     * saves it, and hands it to a background job. The app can close straight away.
     */
    fun send(applyLocal: LocalDateTime? = null) {
        val s = _state.value
        val scene = s.scene ?: return
        val friend = s.friend ?: return
        if (s.sending) return
        val target = s.target
        _state.update { it.copy(sending = true, message = null) }
        viewModelScope.launch {
            val prepared = withContext(renderDispatcher) {
                runCatching {
                    val bitmap = exporter.render(scene, target.widthPx, target.heightPx, s.focusX, s.focusY)
                    // Lossy WebP: a fraction of a PNG's size, so the upload (and their download) is quick.
                    val image = exporter.encode(bitmap, preferPng = false)
                    bitmap.recycle()
                    val dir = File(context.filesDir, "outbox").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.${image.extension}")
                    file.writeBytes(image.bytes)
                    Triple(file, image, image.mimeType)
                }.getOrNull()
            }
            if (prepared == null) {
                _state.update { it.copy(sending = false, message = R.string.send_failed) }
                return@launch
            }
            val (file, image, mime) = prepared
            SendWallpaperWorker.enqueue(
                context = context,
                file = file,
                recipientId = friend.id,
                recipientName = friend.card.name,
                target = s.sendTarget,
                mime = mime,
                width = image.width,
                height = image.height,
                isDoodle = !scene.hasPhotos,
                applyLocal = applyLocal,
                expectAuto = friend.capability == Capability.Auto,
            )
            _state.update { it.copy(sending = false, sent = true, message = R.string.send_on_its_way) }
        }
    }
    fun setAfter(after: Boolean) = _state.update { it.copy(after = after) }
    fun setGuides(guides: Boolean) = _state.update { it.copy(guides = guides) }
    fun setFocus(x: Float = _state.value.focusX, y: Float = _state.value.focusY) =
        change { it.copy(focusX = x.coerceIn(0f, 1f), focusY = y.coerceIn(0f, 1f)) }

    private fun change(block: (PreviewUiState) -> PreviewUiState) {
        _state.update(block)
        render()
    }

    /** Renders at a preview size with exactly the wallpaper's aspect and crop. */
    private fun render() {
        val s = _state.value
        val scene = s.scene ?: return
        val target = s.target
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val image = withContext(renderDispatcher) {
                val w = PREVIEW_WIDTH
                val h = (PREVIEW_WIDTH * target.aspect).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                renderer.draw(android.graphics.Canvas(bitmap), scene, SceneTransform.cover(scene.aspect, w, h, s.focusX, s.focusY))
                bitmap.asImageBitmap()
            }
            _state.update { it.copy(image = image) }
        }
    }

    override fun onCleared() {
        renderDispatcher.close()
        super.onCleared()
    }

    private companion object {
        const val PREVIEW_WIDTH = 540
    }
}

@Composable
fun PreviewScreen(
    draftId: String,
    friendId: String?,
    onBack: () -> Unit,
    onSent: () -> Unit = onBack,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    LaunchedEffect(draftId) { viewModel.open(draftId, friendId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val crayon = MeyouwwTheme.crayon
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sent) {
        if (state.sent) {
            kotlinx.coroutines.delay(1_200)
            onSent()
        }
    }

    CrayonScreen(title = stringResource(R.string.preview_title), onBack = onBack) {
        val scene = state.scene
        if (scene == null) {
            WhityLoading(stringResource(R.string.studio_loading), Modifier.fillMaxWidth().padding(top = 48.dp))
            return@CrayonScreen
        }
        val target = state.target

        // The phone fits the screen: never wider than ~3/4 of it, never taller than about half.
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val screenH = LocalConfiguration.current.screenHeightDp.dp
            val byHeight = screenH * 0.48f / target.aspect
            val frameW = minOf(maxWidth * 0.75f, byHeight, 340.dp)
            var zoom by remember { mutableFloatStateOf(1f) }
            PhoneFrame(
                image = state.image,
                target = target,
                lock = state.sendTarget != WallpaperTarget.Home,
                after = true,
                guides = false,
                modifier = Modifier
                    .width(frameW)
                    .graphicsLayer { scaleX = zoom; scaleY = zoom }
                    .pointerInput(Unit) { detectTransformGestures { _, _, z, _ -> zoom = (zoom * z).coerceIn(1f, 2.5f) } },
            )
        }
        if (target.estimate) {
            Text(
                state.friend?.let { stringResource(R.string.preview_estimate_friend, it.card.name) } ?: stringResource(R.string.preview_estimate),
                style = MaterialTheme.typography.bodySmall,
                color = crayon.textSoft,
            )
        }

        // Crop: only the direction that actually overflows can move.
        val box = SceneTransform.cover(scene.aspect, target.widthPx, target.heightPx, state.focusX, state.focusY)
            .visibleDesignBox(target.widthPx, target.heightPx)
        if (box.height < scene.aspect - 0.001f) {
            Text(stringResource(R.string.preview_move_up_down), style = MaterialTheme.typography.labelLarge)
            Slider(value = state.focusY, onValueChange = { viewModel.setFocus(y = it) })
        } else if (box.width < 0.999f) {
            Text(stringResource(R.string.preview_move_left_right), style = MaterialTheme.typography.labelLarge)
            Slider(value = state.focusX, onValueChange = { viewModel.setFocus(x = it) })
        }

        Text(stringResource(R.string.send_target), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WallpaperTarget.entries.forEach { t ->
                CrayonChip(
                    stringResource(targetLabel(t)),
                    fill = if (t == state.sendTarget) crayon.yellow else MaterialTheme.colorScheme.surface,
                    onClick = { viewModel.setSendTarget(t) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.message?.let {
            Text(stringResource(it), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }

        // On my own phone, right now.
        CrayonButton(
            stringResource(R.string.set_mine),
            onClick = viewModel::setOnMyPhone,
            loading = state.applying,
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        // Or on a friend's.
        Text(stringResource(R.string.preview_for), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.friends.forEach { f ->
                CrayonChip(
                    f.card.name,
                    fill = if (f.id == state.friendId) crayon.yellow else MaterialTheme.colorScheme.surface,
                    onClick = { viewModel.chooseFriend(if (f.id == state.friendId) null else f.id) },
                )
            }
        }
        if (state.devices.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.devices.forEachIndexed { i, d ->
                    CrayonChip(
                        d.label ?: stringResource(R.string.preview_device_n, i + 1),
                        fill = if (i == state.deviceIndex) crayon.blue else MaterialTheme.colorScheme.surface,
                        onClick = { viewModel.chooseDevice(i) },
                    )
                }
            }
        }
        val friend = state.friend
        if (friend == null) {
            Text(stringResource(R.string.send_pick_friend), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
        } else {
            val name = friend.card.name
            val capability = friend.capability
            Text(
                when (capability) {
                    Capability.Auto -> stringResource(R.string.send_auto)
                    Capability.Ask -> stringResource(R.string.send_ask)
                    Capability.Unavailable -> stringResource(R.string.send_unavailable, name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = crayon.textSoft,
            )
            val canSend = capability != Capability.Unavailable && !state.sent
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CrayonButton(
                    stringResource(R.string.send_now, name),
                    onClick = { viewModel.send() },
                    loading = state.sending,
                    enabled = canSend,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { picking = true }, enabled = canSend && !state.sending) {
                    Text(stringResource(R.string.send_later))
                }
            }
        }
    }
    if (picking) {
        ScheduleDialogs(
            onDismiss = { picking = false },
            onPick = {
                picking = false
                viewModel.send(it)
            },
        )
    }
}

private fun targetLabel(t: WallpaperTarget): Int = when (t) {
    WallpaperTarget.Home -> R.string.send_home
    WallpaperTarget.Lock -> R.string.send_lock
    WallpaperTarget.Both -> R.string.send_both
}

/** Day, then time. Both are read as the recipient's wall-clock time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialogs(onDismiss: () -> Unit, onPick: (LocalDateTime) -> Unit) {
    var day by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val chosenDay = day
    if (chosenDay == null) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis() + 86_400_000L)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                    },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.send_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.create_cancel)) } },
        ) { DatePicker(state = dateState, title = { Text(stringResource(R.string.send_pick_date), Modifier.padding(16.dp)) }) }
    } else {
        val timeState = rememberTimePickerState(initialHour = 7, initialMinute = 0, is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.send_pick_time)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalDate.parse(chosenDay).atTime(timeState.hour, timeState.minute))
                }) { Text(stringResource(R.string.send_confirm)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.create_cancel)) } },
        )
    }
}

/**
 * A phone-shaped frame showing the wallpaper with illustrative home or lock
 * screen furniture. Sizes come from the target's real pixel measurements.
 */
@Composable
fun PhoneFrame(
    image: ImageBitmap?,
    target: PreviewTarget,
    lock: Boolean,
    after: Boolean,
    guides: Boolean,
    modifier: Modifier = Modifier,
) {
    val crayon = MeyouwwTheme.crayon
    val description = stringResource(
        R.string.preview_frame_description,
        stringResource(if (lock) R.string.preview_lock else R.string.preview_home),
        target.widthPx,
        target.heightPx,
    )
    BoxWithConstraints(modifier.aspectRatio(1f / target.aspect).semantics { contentDescription = description }) {
        val frameW = maxWidth
        val k = frameW / target.widthPx.toFloat() // dp per device pixel
        val corner = (k * target.cornerRadiusPx.coerceAtLeast(24)).coerceAtMost(48.dp)
        val shape = RoundedCornerShape(corner)
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .border(5.dp, crayon.outline, shape),
        ) {
            if (after && image != null) {
                Image(image, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF9FB3C8), Color(0xFFD9E2EC)))))
            }
            val statusH = k * target.statusBarPx.coerceAtLeast(60)
            val navH = k * target.navBarPx.coerceAtLeast(40)
            val textColor = Color.White
            val small = with(density) { (frameW * 0.045f).toSp() }
            // Status bar: time and icons.
            Row(
                Modifier.fillMaxWidth().height(statusH).padding(horizontal = frameW * 0.06f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.preview_clock), color = textColor, fontSize = small)
                Spacer(Modifier.weight(1f))
                Text("▂▄▆ ▮", color = textColor, fontSize = small)
            }
            if (lock) {
                Column(
                    Modifier.fillMaxWidth().offset(y = frameW * 0.28f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🔒", fontSize = small)
                    Text(
                        stringResource(R.string.preview_clock),
                        color = textColor,
                        fontSize = with(density) { (frameW * 0.2f).toSp() },
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                AppGrid(frameW, statusH, navH)
            }
            // Navigation handle.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navH * 0.4f)
                    .size(width = frameW * 0.3f, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(textColor.copy(alpha = 0.85f)),
            )
            if (guides) Guides(target, lock)
        }
    }
}

@Composable
private fun AppGrid(frameW: Dp, top: Dp, navH: Dp) {
    val tile = frameW * 0.14f
    val gap = (frameW - tile * 4) / 5
    val colors = listOf(Color(0xCCFFD84D), Color(0xCC4DB8FF), Color(0xCC6CCB5F), Color(0xCCFF8FB1))
    Column(Modifier.padding(top = top + frameW * 0.1f, start = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(4) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(4) { col ->
                    Box(Modifier.size(tile).clip(RoundedCornerShape(tile * 0.3f)).background(colors[(row + col) % colors.size]))
                }
            }
        }
    }
    // Dock.
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.BottomStart).padding(start = gap, bottom = navH + gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            repeat(4) { Box(Modifier.size(tile).clip(RoundedCornerShape(tile * 0.3f)).background(Color(0xCCFFFFFF))) }
        }
    }
}

/** Dashed outlines where bars, cut-outs, the clock and the dock usually cover the picture. */
@Composable
private fun Guides(target: PreviewTarget, lock: Boolean) {
    val pink = Color(0xFFFF4FA3)
    Canvas(Modifier.fillMaxSize()) {
        val px = size.width / target.widthPx
        val dash = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        fun box(top: Float, height: Float) = drawRect(pink, Offset(4f, top), Size(size.width - 8f, height), style = dash)
        box(0f, target.statusBarPx.coerceAtLeast(60) * px)
        if (target.cutoutTopPx > 0) {
            drawRoundRect(
                pink,
                Offset(size.width * 0.4f, 2f),
                Size(size.width * 0.2f, target.cutoutTopPx * px),
                androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                style = dash,
            )
        }
        if (lock) {
            box(size.height * 0.12f, size.height * 0.2f) // clock
            box(size.height * 0.62f, size.height * 0.28f) // notifications
        } else {
            box(size.height - (target.navBarPx.coerceAtLeast(40) * px) - size.width * 0.25f, size.width * 0.22f) // dock
        }
        box(size.height - target.navBarPx.coerceAtLeast(40) * px, target.navBarPx.coerceAtLeast(40) * px)
    }
}
