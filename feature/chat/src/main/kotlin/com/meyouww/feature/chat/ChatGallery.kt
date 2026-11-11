package com.meyouww.feature.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meyouww.core.chat.ChatAttachment
import com.meyouww.core.chat.ChatMessage
import com.meyouww.core.chat.MediaKind
import com.meyouww.core.designsystem.component.FullImageDialog
import com.meyouww.core.designsystem.component.StickerMessage
import com.meyouww.core.designsystem.component.WhitySticker
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.component.rememberFileImage
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A link someone shared in the chat. */
internal data class SharedLink(val url: String, val fromMe: Boolean, val at: Long)

/** Web links in a message's text: http(s)://… or www.… */
internal fun linksIn(text: String): List<String> =
    LINK.findAll(text).map { it.value.trimEnd('.', ',', '!', '?', ')', ']', '"', '\'') }.filter { it.length > 4 }.toList()

private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

/**
 * Everything shared in this chat, in four tabs: photos and videos, PDFs and Word files,
 * wallpapers and doodles (applied or only suggested), and links. Newest first.
 */
@Composable
internal fun ChatGalleryDialog(timeline: List<TimelineItem>, peerName: String, onDismiss: () -> Unit) {
    val crayon = MeyouwwTheme.crayon
    val messages = remember(timeline) {
        timeline.filterIsInstance<TimelineItem.Message>().map { it.message }.filter { !it.deleted }.sortedByDescending { it.sentAt }
    }
    val media = remember(messages) { messages.filter { it.attachment?.kind == MediaKind.Image || it.attachment?.kind == MediaKind.Video } }
    val docs = remember(messages) {
        messages.filter { it.attachment?.kind == MediaKind.Pdf || it.attachment?.kind == MediaKind.Doc || it.attachment?.kind == MediaKind.Audio }
    }
    val walls = remember(timeline) { timeline.filterIsInstance<TimelineItem.Wallpaper>().sortedByDescending { it.at } }
    val links = remember(messages) {
        messages.flatMap { m -> m.text?.let(::linksIn).orEmpty().map { SharedLink(it, m.fromMe, m.sentAt) } }
    }
    val starred = remember(messages) { messages.filter { it.starred } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().kasukabeBackdrop().safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                }
                Column {
                    Text(stringResource(R.string.chat_gallery_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(peerName, style = MaterialTheme.typography.labelMedium, color = crayon.textSoft)
                }
            }
            val titles = listOf(
                stringResource(R.string.chat_gallery_media, media.size),
                stringResource(R.string.chat_gallery_docs, docs.size),
                stringResource(R.string.chat_gallery_walls, walls.size),
                stringResource(R.string.chat_gallery_links, links.size),
                stringResource(R.string.chat_gallery_starred, starred.size),
            )
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 12.dp) {
                titles.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> MediaTab(media)
                    1 -> DocsTab(docs)
                    2 -> WallsTab(walls, peerName)
                    3 -> LinksTab(links, peerName)
                    else -> StarredTab(starred, peerName)
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        StickerMessage(WhitySticker.Peek, title = text, body = "")
    }
}

@Composable
private fun MediaTab(items: List<ChatMessage>) {
    if (items.isEmpty()) return Empty(stringResource(R.string.chat_gallery_media_empty))
    val files = rememberFileActions()
    var open by remember { mutableStateOf<ChatMessage?>(null) }
    open?.let { message ->
        val a = message.attachment
        val file = a?.file
        if (a != null && file != null) {
            FullImageDialog(rememberFileImage(file, maxPx = 2048), a.name, onDismiss = { open = null }, onSave = { files.save(file) })
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { message ->
            val a = message.attachment ?: return@items
            MediaTile(a) {
                val file = a.file ?: return@MediaTile
                if (a.kind == MediaKind.Image) open = message else files.open(file, a.mime)
            }
        }
    }
}

@Composable
private fun MediaTile(a: ChatAttachment, onClick: () -> Unit) {
    val picture = (if (a.kind == MediaKind.Image) rememberFileImage(a.file, maxPx = 360) else null) ?: rememberThumb(a.thumb)
    val label = stringResource(mediaLabel(a.kind))
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MeyouwwTheme.crayon.outline.copy(alpha = 0.08f))
            .clickable(enabled = a.file != null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (a.kind == MediaKind.Video) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Text("▶", color = Color.White)
            }
        }
        if (a.file == null) {
            Text(
                stringResource(if (a.unavailable) R.string.chat_file_unavailable else R.string.chat_file_downloading),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun DocsTab(items: List<ChatMessage>) {
    if (items.isEmpty()) return Empty(stringResource(R.string.chat_gallery_docs_empty))
    val files = rememberFileActions()
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { message ->
            val a = message.attachment ?: return@items
            val file: File? = a.file
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(enabled = file != null, role = Role.Button) { if (file != null) files.open(file, a.mime) }
                    .padding(12.dp),
            ) {
                Text(if (a.kind == MediaKind.Doc) "📝" else "📄", style = MaterialTheme.typography.headlineSmall)
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(
                            if (a.kind == MediaKind.Doc) "Word" else "PDF",
                            Formatter.formatShortFileSize(context, a.size),
                            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(message.sentAt)),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MeyouwwTheme.crayon.textSoft,
                    )
                }
                if (file != null) {
                    TextButton(onClick = { files.save(file) }) { Text(stringResource(R.string.chat_file_save)) }
                } else {
                    Text(stringResource(if (a.unavailable) R.string.chat_file_unavailable else R.string.chat_file_downloading), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun WallsTab(items: List<TimelineItem.Wallpaper>, peerName: String) {
    if (items.isEmpty()) return Empty(stringResource(R.string.chat_gallery_walls_empty))
    val files = rememberFileActions()
    var open by remember { mutableStateOf<TimelineItem.Wallpaper?>(null) }
    open?.let { w ->
        val file = w.file
        if (file != null) FullImageDialog(rememberFileImage(file, maxPx = 2048), wallStatus(w, peerName), onDismiss = { open = null }, onSave = { files.save(file) })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.id }) { w ->
            val picture = rememberFileImage(w.file, maxPx = 360)
            val status = wallStatus(w, peerName)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(enabled = w.file != null, role = Role.Button, onClickLabel = status) { open = w },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.6f).clip(RoundedCornerShape(12.dp)).background(MeyouwwTheme.crayon.outline.copy(alpha = 0.08f)),
                ) {
                    if (picture != null) Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) else Text("🖼", style = MaterialTheme.typography.headlineMedium)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** "You sent · applied", "From Riya · waiting", … */
@Composable
private fun wallStatus(w: TimelineItem.Wallpaper, peerName: String): String {
    val who = if (w.fromMe) stringResource(R.string.chat_gallery_wall_sent) else stringResource(R.string.chat_gallery_wall_from, peerName)
    val state = stringResource(
        when (w.status) {
            "applied" -> R.string.chat_gallery_wall_applied
            "delivered", "awaiting_approval", "applying", "sent", "scheduled", "held" -> R.string.chat_gallery_wall_suggested
            "declined" -> R.string.chat_gallery_wall_declined
            else -> R.string.chat_gallery_wall_not_applied
        },
    )
    return "$who · $state"
}

/** Messages I starred in this chat, newest first. */
@Composable
private fun StarredTab(items: List<ChatMessage>, peerName: String) {
    if (items.isEmpty()) return Empty(stringResource(R.string.chat_gallery_starred_empty))
    val you = stringResource(R.string.chat_you)
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { message ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
            ) {
                Text("★ " + previewText(message), style = MaterialTheme.typography.bodyLarge, maxLines = 8, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (message.fromMe) you else peerName) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(message.sentAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MeyouwwTheme.crayon.textSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LinksTab(items: List<SharedLink>, peerName: String) {
    if (items.isEmpty()) return Empty(stringResource(R.string.chat_gallery_links_empty))
    val context = LocalContext.current
    val noApp = stringResource(R.string.chat_file_no_app)
    val you = stringResource(R.string.chat_you)
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(items) { link ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Button) {
                        val target = if (link.url.startsWith("http", ignoreCase = true)) link.url else "https://" + link.url
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(12.dp),
            ) {
                Text(
                    "🔗 " + link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    (if (link.fromMe) you else peerName) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(link.at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MeyouwwTheme.crayon.textSoft,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
