package com.meyouww.feature.home

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.MAX_CONTENT_WIDTH
import com.meyouww.core.designsystem.component.WhityLoading
import com.meyouww.core.render.AvatarCrop
import com.meyouww.core.render.AvatarEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Frames a new profile picture before it is uploaded: the photo sits under a circle that
 * shows exactly what friends will see. Pinch to zoom, drag with a finger to move. The photo
 * always fills the circle, so there are never empty edges.
 */
@Composable
internal fun AvatarCropDialog(uri: Uri, onDismiss: () -> Unit, onDone: (AvatarCrop) -> Unit) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { AvatarEncoder.preview({ context.contentResolver.openInputStream(uri) }) }.getOrNull() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .safeDrawingPadding()
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.home_crop_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                stringResource(R.string.home_crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            val bitmap = photo
            var crop by remember(bitmap) { mutableStateOf<AvatarCrop?>(null) }
            if (bitmap == null) {
                WhityLoading(stringResource(R.string.home_crop_loading), Modifier.padding(32.dp))
            } else {
                CropArea(bitmap, onCrop = { crop = it }, modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) {
                CrayonButton(stringResource(R.string.home_crop_cancel), onClick = onDismiss, kind = ButtonKind.Secondary, modifier = Modifier.weight(1f))
                CrayonButton(
                    stringResource(R.string.home_crop_use),
                    onClick = { crop?.let(onDone) },
                    enabled = crop != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The photo, its pinch / drag handling and the circle guide. Reports the framed square as it changes. */
@Composable
private fun CropArea(bitmap: Bitmap, onCrop: (AvatarCrop) -> Unit, modifier: Modifier = Modifier) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val guide = stringResource(R.string.home_crop_guide)
    BoxWithConstraints(modifier.aspectRatio(1f).semantics { contentDescription = guide }) {
        val density = LocalDensity.current
        val box = with(density) { maxWidth.toPx() }
        val margin = with(density) { 20.dp.toPx() }
        val circle = box - 2 * margin
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        // Smallest zoom fills the circle; up to 5x closer.
        val minScale = circle / minOf(w, h)
        val maxScale = minScale * 5f
        var scale by remember(bitmap, box) { mutableFloatStateOf(minScale) }
        var ox by remember(bitmap, box) { mutableFloatStateOf(margin + (circle - w * minScale) / 2f) }
        var oy by remember(bitmap, box) { mutableFloatStateOf(margin + (circle - h * minScale) / 2f) }

        fun report() {
            onCrop(
                AvatarCrop(
                    left = ((margin - ox) / scale / w).coerceIn(0f, 1f),
                    top = ((margin - oy) / scale / h).coerceIn(0f, 1f),
                    side = (circle / scale / w).coerceIn(0f, 1f),
                ),
            )
        }
        LaunchedEffect(bitmap, box) { report() }

        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(bitmap, box) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(minScale, maxScale)
                        val applied = next / scale
                        // Zoom around the fingers, then follow the drag.
                        var nx = centroid.x - (centroid.x - ox) * applied + pan.x
                        var ny = centroid.y - (centroid.y - oy) * applied + pan.y
                        // Keep the circle covered by the photo.
                        nx = nx.coerceIn(margin + circle - w * next, margin)
                        ny = ny.coerceIn(margin + circle - h * next, margin)
                        scale = next
                        ox = nx
                        oy = ny
                        report()
                    }
                },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(ox.toInt(), oy.toInt()),
                dstSize = IntSize((w * scale).toInt(), (h * scale).toInt()),
            )
            val ring = Path().apply { addOval(Rect(Offset(margin, margin), Size(circle, circle))) }
            // Dim everything outside the circle.
            clipPath(ring, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
            drawPath(ring, Color.White, style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
        }
    }
}
