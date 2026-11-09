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
          {"tag_name": "v0.16.0", "draft": false, "prerelease": true, "html_url": "https://x/v0.16.0",
           "body": "### What's new\r\n- Photos and videos in chats.", "assets": []},
          {"tag_name": "v0.15.1", "draft": false, "prerelease": true, "html_url": "https://x/v0.15.1", "body": "no notes", "assets": []}
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
        assertThat(notes).containsExactly(ReleaseNotes("0.17.0", listOf("Media, docs and links tabs.", "Update pop-up with Whity.")))
    }

    @Test
    fun `after updating from v0_13 the whats new list covers every version since`() {
        val notes = releasesBetween(releases, after = "0.13.0", upTo = "0.17.0")
        assertThat(notes.map { it.version }).containsExactly("0.17.0", "0.16.0").inOrder()
        assertThat(releasesBetween(releases, after = "0.16.0", upTo = "0.16.0")).isEmpty()
        // The draft v0.18.0 never counts.
        assertThat(releasesBetween(releases, after = "0.13.0", upTo = "0.19.0").map { it.version }).containsExactly("0.17.0", "0.16.0").inOrder()
    }

    @Test
    fun `stray lines after the list are not glued onto a bullet`() {
        val body = "### What's new\n- One.\n  still one.\n\n<!-- a note -->\nloose text\n### How to install"
        assertThat(whatsNew(body)).containsExactly("One. still one.")
    }

    @Test
    fun `a big jump shows every version in between, newest first`() {
        val notes = newestRelease(releases, installed = "0.13.0")!!.notes
        assertThat(notes.map { it.version }).containsExactly("0.17.0", "0.16.0").inOrder()
        assertThat(notes[1].bullets).containsExactly("Photos and videos in chats.")
    }
}
