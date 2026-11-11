package com.meyouww.feature.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.meyouww.core.chat.LinkFinder
import com.meyouww.core.chat.LinkPreview

/** Opens a web link in the phone's browser (or the app that handles it, like YouTube). */
internal fun openLink(context: Context, link: String, noApp: String) {
    val url = LinkFinder.normalize(link)
    if (!LinkFinder.isWeb(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noApp, Toast.LENGTH_SHORT).show()
    }
}

/** A message's text with its web links underlined; tapping one opens it. */
@Composable
internal fun LinkedText(text: String, ink: Color) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.chat_file_no_app)
    val annotated = remember(text, ink) {
        val links = LinkFinder.find(text)
        buildAnnotatedString {
            var at = 0
            for ((range, link) in links) {
                append(text.substring(at, range.first))
                withLink(
                    LinkAnnotation.Url(
                        url = LinkFinder.normalize(link),
                        styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)),
                        linkInteractionListener = { openLink(context, link, noApp) },
                    ),
                ) { append(text.substring(range.first, range.last + 1)) }
                at = range.last + 1
            }
            append(text.substring(at))
        }
    }
    Text(annotated, style = MaterialTheme.typography.bodyLarge, color = ink)
}

/** The preview card under a message with a link: picture, title, a line about it and the site. */
@Composable
internal fun LinkPreviewCard(preview: LinkPreview, ink: Color) {
    val context = LocalContext.current
    val noApp = stringResource(R.string.chat_file_no_app)
    val image = rememberThumb(preview.image)
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(max = 300.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(ink.copy(alpha = 0.08f))
            .border(1.5.dp, ink.copy(alpha = 0.35f), shape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.chat_link_open)) { openLink(context, preview.url, noApp) },
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            preview.title?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            preview.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "🔗 " + (preview.site ?: preview.domain),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
