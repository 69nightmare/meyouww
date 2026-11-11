package com.meyouww.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkPreviewTest {
    @Test
    fun `links are found where they sit, without trailing punctuation`() {
        val text = "see https://youtu.be/abc123, and www.example.com/page!"
        val found = LinkFinder.find(text)
        assertThat(found.map { it.second }).containsExactly("https://youtu.be/abc123", "www.example.com/page").inOrder()
        assertThat(text.substring(found[0].first)).isEqualTo("https://youtu.be/abc123")
        assertThat(LinkFinder.normalize("www.example.com/page")).isEqualTo("https://www.example.com/page")
        assertThat(LinkFinder.find("no links here, just text")).isEmpty()
    }

    @Test
    fun `open graph tags are read in either attribute order`() {
        val head = """
            <title>Fallback &amp; title</title>
            <meta property="og:title" content="A &quot;great&quot; video">
            <meta content='Watch it now' name='og:description'>
            <meta property="og:site_name" content="YouTube" />
        """.trimIndent()
        assertThat(LinkPreviewFetcher.meta(head, "og:title")).isEqualTo("A \"great\" video")
        assertThat(LinkPreviewFetcher.meta(head, "og:description")).isEqualTo("Watch it now")
        assertThat(LinkPreviewFetcher.meta(head, "og:site_name")).isEqualTo("YouTube")
        assertThat(LinkPreviewFetcher.meta(head, "og:image")).isNull()
        assertThat(LinkPreviewFetcher.tag(head, "title")).isEqualTo("Fallback & title")
    }

    @Test
    fun `a preview is trimmed and must point to a web page`() {
        val long = LinkPreview(url = "https://x.com/a", title = "t".repeat(500), description = "  ")
        val clean = long.cleaned()!!
        assertThat(clean.title).hasLength(LinkPreview.MAX_TITLE)
        assertThat(clean.description).isNull()
        assertThat(clean.domain).isEqualTo("x.com")
        assertThat(LinkPreview(url = "javascript:alert(1)", title = "x").cleaned()).isNull()
        assertThat(LinkPreview(url = "https://x.com").cleaned()).isNull() // nothing to show
        assertThat(LinkPreview.decode(clean.encode())).isEqualTo(clean)
    }

    @Test
    fun `older apps ignore the preview message`() {
        val p = ChatPayload(id = "p", kind = ChatPayload.KIND_LINK_PREVIEW, target = "m", preview = LinkPreview("https://a.b", title = "T"), sentAt = 1)
        assertThat(p.visible).isFalse()
        assertThat(ChatPayload.decode(ChatPayload.encode(p))).isEqualTo(p)
    }
}
