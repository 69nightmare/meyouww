package com.meyouww.core.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class MediaCryptoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val plain = Random(7).nextBytes(300_000)

    private fun seal(): Pair<java.io.File, MediaCrypto.Sealed> {
        val blob = tmp.newFile("blob.bin")
        val sealed = blob.outputStream().use { MediaCrypto.encrypt(plain.inputStream(), it) }
        return blob to sealed
    }

    @Test
    fun `a file comes back exactly as it went in`() {
        val (blob, sealed) = seal()
        val out = ByteArrayOutputStream()
        MediaCrypto.decrypt(blob, sealed.key, sealed.sha256, out)
        assertThat(out.toByteArray()).isEqualTo(plain)
    }

    @Test
    fun `the server only ever holds ciphertext`() {
        val (blob, sealed) = seal()
        val stored = blob.readBytes()
        assertThat(stored.size).isEqualTo(plain.size + 12 + 16) // nonce + GCM tag
        assertThat(stored.copyOfRange(12, 12 + 64)).isNotEqualTo(plain.copyOfRange(0, 64))
        assertThat(sealed.sha256).isEqualTo(MediaCrypto.sha256Of(blob))
    }

    @Test
    fun `a changed blob is refused`() {
        val (blob, sealed) = seal()
        val bytes = blob.readBytes().also { it[500] = (it[500].toInt() xor 1).toByte() }
        blob.writeBytes(bytes)
        assertThrows(IllegalStateException::class.java) { MediaCrypto.decrypt(blob, sealed.key, sealed.sha256, ByteArrayOutputStream()) }
        // Even with a matching checksum, GCM's tag catches it.
        assertThrows(Exception::class.java) { MediaCrypto.decrypt(blob, sealed.key, MediaCrypto.sha256Of(blob), ByteArrayOutputStream()) }
    }

    @Test
    fun `the wrong key cannot open it`() {
        val (blob, sealed) = seal()
        val otherKey = KeyManager.b64(MediaCrypto.newKey())
        assertThrows(Exception::class.java) { MediaCrypto.decrypt(blob, otherKey, sealed.sha256, ByteArrayOutputStream()) }
    }

    @Test
    fun `only files at the sender's own path, of a known type and size, are accepted`() {
        val ok = Attachment(
            path = "sender/conv/msg", key = "k".repeat(44), sha256 = "a".repeat(64),
            mime = "image/jpeg", name = "cat.jpg", size = 1_000,
        )
        assertThat(ok.isAcceptable("sender", "conv", "msg")).isTrue()
        assertThat(ok.isAcceptable("someone-else", "conv", "msg")).isFalse()
        assertThat(ok.copy(mime = "application/zip").isAcceptable("sender", "conv", "msg")).isFalse()
        assertThat(ok.copy(size = Attachment.MAX_BYTES + 1).isAcceptable("sender", "conv", "msg")).isFalse()
        assertThat(ok.copy(mime = "application/pdf").kind).isEqualTo(MediaKind.Pdf)
        assertThat(ok.copy(mime = "video/mp4").kind).isEqualTo(MediaKind.Video)
        assertThat(ok.copy(mime = MediaKind.DOCX).kind).isEqualTo(MediaKind.Doc)
        assertThat(ok.copy(mime = MediaKind.DOCX).isAcceptable("sender", "conv", "msg")).isTrue()
    }

    @Test
    fun `word files reported as octet-stream are recognised by their name`() {
        assertThat(MediaKind.guessMime("application/octet-stream", "Notes.DOCX")).isEqualTo(MediaKind.DOCX)
        assertThat(MediaKind.guessMime(null, "old.doc")).isEqualTo(MediaKind.DOC)
        assertThat(MediaKind.guessMime("image/jpeg", "x.docx")).isEqualTo("image/jpeg")
        // Sound files go as voice / audio messages; anything else unknown is still refused.
        assertThat(MediaKind.of(MediaKind.guessMime("application/octet-stream", "song.mp3")!!)).isEqualTo(MediaKind.Audio)
        assertThat(MediaKind.of(MediaKind.guessMime(null, "voice.m4a")!!)).isEqualTo(MediaKind.Audio)
        assertThat(MediaKind.of(MediaKind.guessMime("application/octet-stream", "setup.exe")!!)).isEqualTo(MediaKind.Other)
        assertThat(ChatMediaFiles.extensionFor("audio/mp4", "Voice message.m4a")).isEqualTo("m4a")
    }
}
