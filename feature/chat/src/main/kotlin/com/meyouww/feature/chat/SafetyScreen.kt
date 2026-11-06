package com.meyouww.feature.chat

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.SafetyNumber
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.designsystem.component.ButtonKind
import com.meyouww.core.designsystem.component.CrayonButton
import com.meyouww.core.designsystem.component.CrayonCard
import com.meyouww.core.designsystem.component.CrayonScreen
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SafetyUiState(
    val loading: Boolean = true,
    val name: String = "",
    val number: SafetyNumber? = null,
    /** null: not scanned yet; true / false: the result of the last scan. */
    val scanMatched: Boolean? = null,
    val scanFailed: Boolean = false,
)

@HiltViewModel
class SafetyViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val friends: FriendsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SafetyUiState())
    val state: StateFlow<SafetyUiState> = _state.asStateFlow()
    private var peerId: String? = null

    fun load(peerId: String) {
        this.peerId = peerId
        viewModelScope.launch {
            val name = friends.friends.value?.firstOrNull { it.id == peerId }?.card?.name
                ?: chat.conversationWith(peerId)?.peerName.orEmpty()
            _state.update { it.copy(loading = false, name = name, number = chat.safetyNumber(peerId)) }
        }
    }

    fun scanned(text: String?) {
        val id = peerId ?: return
        if (text == null) return
        viewModelScope.launch {
            val matched = chat.verifyScanned(id, text)
            _state.update { it.copy(scanMatched = matched, scanFailed = false, number = chat.safetyNumber(id)) }
        }
    }

    fun scanFailed() = _state.update { it.copy(scanFailed = true) }

    fun setVerified(verified: Boolean) {
        val id = peerId ?: return
        viewModelScope.launch {
            chat.setVerified(id, verified)
            _state.update { it.copy(number = chat.safetyNumber(id)) }
        }
    }
}

/**
 * The safety number: if it matches on both phones (read aloud or scanned), nobody
 * is sitting in the middle of the encryption.
 */
@Composable
fun SafetyScreen(
    peerId: String,
    onBack: () -> Unit,
    viewModel: SafetyViewModel = hiltViewModel(),
) {
    LaunchedEffect(peerId) { viewModel.load(peerId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val crayon = MeyouwwTheme.crayon
    val name = state.name.ifBlank { stringResource(R.string.chat_friend) }

    CrayonScreen(title = stringResource(R.string.safety_title), onBack = onBack) {
        val number = state.number
        if (state.loading) return@CrayonScreen
        if (number == null) {
            StickerMessage(WhitySticker.Peek, title = stringResource(R.string.safety_none_title), body = stringResource(R.string.safety_none_body, name))
            return@CrayonScreen
        }
        Text(stringResource(R.string.safety_body, name), style = MaterialTheme.typography.bodyLarge)
        CrayonCard(modifier = Modifier.fillMaxWidth(), fill = crayon.yellow, seed = 12) {
            number.digits.chunked(5).chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { group ->
                        Text(group, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        val dark = MaterialTheme.colorScheme.onSurface.toArgb()
        val light = MaterialTheme.colorScheme.surface.toArgb()
        val qr = remember(number.qrPayload, dark, light) { qrBitmap(number.qrPayload, 720, dark, light) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(qr.asImageBitmap(), contentDescription = stringResource(R.string.safety_qr_description, name), modifier = Modifier.size(220.dp))
        }
        CrayonButton(
            stringResource(R.string.safety_scan),
            onClick = { scanQr(context, viewModel::scanned, viewModel::scanFailed) },
            modifier = Modifier.fillMaxWidth(),
        )
        val result = when {
            state.scanFailed -> stringResource(R.string.safety_scan_unavailable)
            state.scanMatched == true -> stringResource(R.string.safety_scan_match, name)
            state.scanMatched == false -> stringResource(R.string.safety_scan_mismatch, name)
            number.verified -> stringResource(R.string.safety_verified, name)
            else -> null
        }
        result?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        CrayonButton(
            stringResource(if (number.verified) R.string.safety_clear else R.string.safety_mark),
            onClick = { viewModel.setVerified(!number.verified) },
            kind = ButtonKind.Quiet,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.safety_explainer, name), style = MaterialTheme.typography.bodySmall, color = crayon.textSoft)
    }
}

private fun qrBitmap(text: String, sizePx: Int, dark: Int, light: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) dark else light }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

/** Google's scanner runs in Play services: no camera permission for meyouww. */
private fun scanQr(context: Context, onResult: (String?) -> Unit, onFailed: () -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode -> onResult(barcode.rawValue) }
        .addOnCanceledListener { onResult(null) }
        .addOnFailureListener { onFailed() }
}
