package com.meyouww.app

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.Test

class UpdateReleaseTest {
    private val releases = Json.parseToJsonElement(
        """
        [
          {"tag_name": "v0.18.0", "draft": true, "prerelease": true, "html_url": "d", "body": "", "assets": []},
          {"tag_name": "v0.17.0", "draft": false, "prerelease": true, "html_url": "https://x/v0.17.0",
           "body": "**meyouww 0.17.0**\r\n\r\n### What's new\r\n- Media, docs and `links` tabs.\r\n- Update pop-up with\r\n  Whity.\r\n\r\n### How to install\r\n- Download it.",
           "assets": [
             {"name": "meyouww-v0.17.0.apk", "browser_download_url": "https://x/a.apk"},
             {"name": "meyouww-v0.17.0.apk.sha256", "browser_download_url": "https://x/a.apk.sha256"}
           ]},
          {"tag_name": "v0.16.0", "draft": false, "prerelease": true, "html_url": "https://x/v0.16.0", "body": "", "assets": []}
        ]
        """.trimIndent(),
    ).jsonArray

    @Test
    fun `picks the newest pre-release and skips drafts`() {
        val newer = newestRelease(releases, installed = "0.16.0")!!
        assertThat(newer.version).isEqualTo("0.17.0")
        assertThat(newer.apkUrl).isEqualTo("https://x/a.apk")
        assertThat(newer.sha256Url).isEqualTo("https://x/a.apk.sha256")
    }

    @Test
    fun `nothing when already up to date`() {
        assertThat(newestRelease(releases, installed = "0.17.0")).isNull()
    }

    @Test
    fun `whats new bullets come without markdown and wrapped lines join`() {
        val notes = newestRelease(releases, installed = "0.16.0")!!.notes
        assertThat(notes).containsExactly("Media, docs and links tabs.", "Update pop-up with Whity.").inOrder()
    }
}
