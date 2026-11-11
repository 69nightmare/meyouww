package com.meyouww.core.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * A link's preview card: the page's title, a line about it and a small picture.
 *
 * Made on the sender's phone and sent inside the encrypted message (like Signal), so the
 * person receiving it never contacts the website until they tap the link.
 */
@Serializable
data class LinkPreview(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val site: String? = null,
    /** Base64 JPEG, about 300 px wide at most. */
    val image: String? = null,
) {
    /** "youtube.com" from the link. */
    val domain: String get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

    fun encode(): String = ChatJson.encodeToString(serializer(), this)

    /** Kept within limits, or null if it says nothing worth showing. */
    fun cleaned(): LinkPreview? {
        val clean = copy(
            url = url.take(MAX_URL),
            title = title?.trim()?.take(MAX_TITLE)?.ifBlank { null },
            description = description?.trim()?.take(MAX_DESCRIPTION)?.ifBlank { null },
            site = site?.trim()?.take(MAX_SITE)?.ifBlank { null },
            image = image?.takeIf { it.length <= Attachment.MAX_THUMB_CHARS },
        )
        return clean.takeIf { LinkFinder.isWeb(it.url) && (it.title != null || it.description != null || it.image != null) }
    }

    companion object {
        const val MAX_URL = 2000
        const val MAX_TITLE = 200
        const val MAX_DESCRIPTION = 300
        const val MAX_SITE = 80

        fun decode(json: String?): LinkPreview? = json?.let { runCatching { ChatJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** Web links in message text. */
object LinkFinder {
    private val LINK = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

    /** Each link with where it sits in [text] (for making it tappable). */
    fun find(text: String): List<Pair<IntRange, String>> = LINK.findAll(text).mapNotNull { m ->
        val value = m.value.trimEnd('.', ',', '!', '?', ')', ']', '"', '\'', ';', ':')
        if (value.length <= 4) return@mapNotNull null
        IntRange(m.range.first, m.range.first + value.length - 1) to value
    }.toList()

    /** "www.x.com" → "https://www.x.com"; anything else unchanged. */
    fun normalize(link: String): String = if (link.startsWith("http", ignoreCase = true)) link else "https://$link"

    fun isWeb(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}

/** Reads a page's preview (its Open Graph tags, or its title) and shrinks its picture. */
internal object LinkPreviewFetcher {
    private const val MAX_HTML = 512 * 1024
    private const val MAX_IMAGE = 3 * 1024 * 1024
    private const val IMAGE_SIDE = 320
    private const val TIMEOUT_MS = 6_000

    /** A browser's own User-Agent, so sites send the same page a person would see. */
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun fetch(link: String): LinkPreview? = runCatching {
        val url = LinkFinder.normalize(link)
        if (!LinkFinder.isWeb(url)) return null
        val (finalUrl, html) = get(url, MAX_HTML, accept = "text/html,application/xhtml+xml") ?: return null
        val page = html.toString(Charsets.UTF_8)
        val head = page.substringBefore("</head>", page)
        val title = meta(head, "og:title") ?: meta(head, "twitter:title") ?: tag(head, "title")
        val description = meta(head, "og:description") ?: meta(head, "twitter:description") ?: meta(head, "description")
        val site = meta(head, "og:site_name")
        val image = (meta(head, "og:image") ?: meta(head, "og:image:url") ?: meta(head, "twitter:image"))
            ?.let { resolve(finalUrl, it) }
            ?.let(::thumbnail)
        LinkPreview(url = url, title = title, description = description, site = site, image = image).cleaned()
    }.getOrNull()

    /** Downloads up to [limit] bytes; returns where it ended up (after redirects) and the bytes. */
    private fun get(url: String, limit: Int, accept: String): Pair<String, ByteArray>? {
        var current = url
        repeat(5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: return null
                    current = resolve(current, next) ?: return null
                    if (!LinkFinder.isWeb(current)) return null
                    return@repeat
                }
                if (code !in 200..299) return null
                return current to connection.inputStream.use { readUpTo(it, limit) }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun thumbnail(url: String): String? = runCatching {
        val (_, bytes) = get(url, MAX_IMAGE, accept = "image/*") ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = IMAGE_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        // Small enough to travel inside the message.
        for (quality in listOf(70, 55, 40)) {
            val out = ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, quality, out)
            val text = KeyManager.b64(out.toByteArray())
            if (text.length <= Attachment.MAX_THUMB_CHARS) return text
        }
        null
    }.getOrNull()

    private fun resolve(base: String, link: String): String? = runCatching { URI(base).resolve(link.trim()).toString() }.getOrNull()

    /** <meta property="og:title" content="…"> in either attribute order. */
    internal fun meta(html: String, name: String): String? {
        val key = Regex.escape(name)
        val patterns = listOf(
            Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$key["'][^>]*content\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+(?:property|name)\s*=\s*["']$key["'][^>]*content\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content\s*=\s*"([^"]*)"[^>]*(?:property|name)\s*=\s*["']$key["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content\s*=\s*'([^']*)'[^>]*(?:property|name)\s*=\s*["']$key["']""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }?.let(::unescape)?.trim()?.ifBlank { null }
    }

    internal fun tag(html: String, name: String): String? =
        Regex("""<$name[^>]*>([\s\S]*?)</$name>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            ?.let(::unescape)?.replace(Regex("""\s+"""), " ")?.trim()?.ifBlank { null }

    internal fun unescape(text: String): String = text
        .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
        .replace(Regex("""&#[xX]([0-9a-fA-F]+);""")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
}
