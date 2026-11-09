package com.meyouww.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meyouww.core.designsystem.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads a picture file scaled down to about [maxPx] on its longer side, off the main thread. */
@Composable
fun rememberFileImage(file: File?, maxPx: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, file?.path, maxPx) {
        value = file?.let { decodeScaled(it, maxPx) }
    }
    return image
}

private suspend fun decodeScaled(file: File, maxPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

/** A person's profile photo, full size, over a dark backdrop. Tap anywhere to close. */
@Composable
fun FullPhotoDialog(photo: String?, caption: String, onDismiss: () -> Unit) {
    val loader = LocalAvatarLoader.current
    val image by produceState<ImageBitmap?>(null, photo, loader) {
        value = if (photo != null && loader != null) loader.load(photo) else null
    }
    FullImageDialog(image, caption, onDismiss)
}

/**
 * Any picture (a wallpaper in the chat, a photo), full screen. Tap anywhere to close.
 * With [onSave], a Save to phone button sits under the caption.
 */
@Composable
fun FullImageDialog(image: ImageBitmap?, caption: String, onDismiss: () -> Unit, onSave: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(role = Role.Button, onClickLabel = caption, onClick = onDismiss)
                .safeDrawingPadding(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (image != null) {
                    Image(
                        bitmap = image,
                        contentDescription = caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().widthIn(max = MAX_CONTENT_WIDTH).weight(1f, fill = false),
                    )
                } else {
                    WhityLoading(caption, Modifier.padding(32.dp))
                }
                Text(caption, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                if (onSave != null && image != null) {
                    CrayonButton(stringResource(R.string.ds_save_to_phone), onClick = onSave, kind = ButtonKind.Secondary)
                }
            }
        }
    }
}
