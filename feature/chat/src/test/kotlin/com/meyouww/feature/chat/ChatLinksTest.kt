package com.meyouww.feature.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatLinksTest {
    @Test
    fun `finds http, https and www links`() {
        assertThat(linksIn("see https://example.com/a?b=1 and http://x.org/y"))
            .containsExactly("https://example.com/a?b=1", "http://x.org/y").inOrder()
        assertThat(linksIn("www.meyouww.app is cute")).containsExactly("www.meyouww.app")
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        assertThat(linksIn("look (https://example.com/page).")).containsExactly("https://example.com/page")
        assertThat(linksIn("wow https://a.co/x!")).containsExactly("https://a.co/x")
    }

    @Test
    fun `plain text has no links`() {
        assertThat(linksIn("Kisi or pr change kar k dekh. ok.")).isEmpty()
        assertThat(linksIn("email me at a.b@c.com")).isEmpty()
    }
}
