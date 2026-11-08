package com.meyouww.feature.friends

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.meyouww.core.designsystem.component.Sticker
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.model.Capability
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.RelationshipLevel

@Composable
fun levelText(level: RelationshipLevel, label: String?): String = when (level) {
    RelationshipLevel.Friend -> stringResource(R.string.level_friend)
    RelationshipLevel.BestFriend -> stringResource(R.string.level_best_friend)
    RelationshipLevel.Partner -> stringResource(R.string.level_partner, label ?: stringResource(R.string.level_partner_default))
}

/** The plain level name, used inside sentences. */
@Composable
fun levelName(level: RelationshipLevel, label: String?): String = when (level) {
    RelationshipLevel.Friend -> stringResource(R.string.level_friend)
    RelationshipLevel.BestFriend -> stringResource(R.string.level_best_friend)
    RelationshipLevel.Partner -> label ?: stringResource(R.string.level_partner_default)
}

@StringRes
fun capabilityText(capability: Capability): Int = when (capability) {
    Capability.Auto -> R.string.capability_auto
    Capability.Ask -> R.string.capability_ask
    Capability.Unavailable -> R.string.capability_unavailable
}

/** A short message for results that are not plain success. */
@StringRes
fun resultMessage(result: ChangeResult): Int = when (result) {
    ChangeResult.RateLimited -> R.string.result_rate_limited
    ChangeResult.Gone, ChangeResult.NotFriends -> R.string.result_gone
    ChangeResult.NeedsCloserLevel -> R.string.perm_needs_closer
    ChangeResult.NeedsNotifications -> R.string.perm_needs_notifications
    ChangeResult.Conflict -> R.string.perm_conflict
    ChangeResult.AlreadyPartnered -> R.string.result_already_partnered
    ChangeResult.AlreadyAsked -> R.string.result_already_asked
    ChangeResult.Unavailable -> R.string.result_unavailable
    else -> R.string.result_generic
}

fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Whity explains why notifications matter, then Android asks. Shown in context:
 * right after the first friend is added.
 */
@Composable
fun NotificationExplainer(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onDone() }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Sticker(WhitySticker.Peek, decorative = true, size = 96.dp) },
        title = { Text(stringResource(R.string.notif_title)) },
        text = { Text(stringResource(R.string.notif_body)) },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onDone()
                }
            }) { Text(stringResource(R.string.notif_allow)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.notif_later)) } },
    )
}

/** Draws a QR code. Dark modules use [dark], the background [light]. */
fun qrBitmap(text: String, sizePx: Int, dark: Int, light: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) dark else light }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

fun shareText(context: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

/**
 * Opens Google's QR scanner. It runs in Google Play services, so meyouww needs
 * no camera permission and never sees the camera feed.
 */
fun scanQr(context: Context, onResult: (String?) -> Unit, onFailed: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
        .addOnCanceledListener { onResult(null) }
        .addOnFailureListener { onFailed() }
}
