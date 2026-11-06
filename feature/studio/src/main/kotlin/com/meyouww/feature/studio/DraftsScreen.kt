package com.meyouww.feature.studio

import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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

    CrayonScreen(title = stringResource(R.string.create_title)) {
        CrayonButton(stringResource(R.string.create_new), onClick = onNew, modifier = Modifier.fillMaxWidth())
        val list = drafts
        when {
            list == null -> Unit
            list.isEmpty() -> StickerMessage(
                sticker = WhitySticker.Flowers,
                title = stringResource(R.string.create_empty_title),
                body = stringResource(R.string.create_empty_body),
            )
            else -> {
                SectionTitle(stringResource(R.string.create_drafts))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    list.forEach { draft ->
                        DraftTile(
                            draft = draft,
                            onOpen = { onOpen(draft.id) },
                            onDuplicate = { viewModel.duplicate(draft.id) },
                            onDelete = { confirmDelete = draft.id },
                        )
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
private fun DraftTile(draft: Draft, onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    val edited = DateUtils.getRelativeTimeSpanString(draft.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val thumb by produceState<ImageBitmap?>(null, draft.thumbnail, draft.updatedAt) {
        value = withContext(Dispatchers.IO) {
            draft.thumbnail?.let { f -> runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
        }
    }
    CrayonCard(
        modifier = Modifier.width(156.dp),
        seed = draft.id.hashCode(),
        onClick = onOpen,
        onClickLabel = stringResource(R.string.create_open),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / draft.aspect.coerceIn(1f, 3f))
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, MeyouwwTheme.crayon.outline, RoundedCornerShape(12.dp))
                .clearAndSetSemantics { },
        ) {
            thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth()) }
        }
        Text(stringResource(R.string.create_edited, edited), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CrayonButton(stringResource(R.string.create_duplicate), onClick = onDuplicate, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
            CrayonButton(stringResource(R.string.create_delete), onClick = onDelete, kind = ButtonKind.Quiet, modifier = Modifier.weight(1f))
        }
    }
}
