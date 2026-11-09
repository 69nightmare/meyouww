package com.meyouww.core.chat

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A photo, video or PDF in a chat, as it travels INSIDE the Signal-encrypted payload.
 * The file itself is stored on the server only as AES-256-GCM ciphertext at [path];
 * [key] (and so the file) is known to the two phones only. See docs/E2EE.md.
 */
@Serializable
data class Attachment(
    /** Where the ciphertext waits: <sender>/<conversation>/<message id> in the chat-media bucket. */
    val path: String,
    /** Base64 AES-256 key, fresh for every file. */
    val key: String,
    /** Hex SHA-256 of the ciphertext, checked before decrypting. */
    val sha256: String,
    val mime: String,
    val name: String,
    /** Size of the original file in bytes. */
    val size: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    /** A tiny JPEG preview (base64), shown until the file itself has arrived. */
    val thumb: String? = null,
) {
    val kind: MediaKind get() = MediaKind.of(mime)

    /** Only what the app knows how to show; anything else is refused on arrival. */
    fun isAcceptable(senderId: String, conversationId: String, messageId: String): Boolean =
        path == "$senderId/$conversationId/$messageId" &&
            size in 1..MAX_BYTES && kind != MediaKind.Other &&
            key.length in 40..48 && sha256.length == 64 &&
            name.length <= 200 && (thumb?.length ?: 0) <= MAX_THUMB_CHARS

    companion object {
        /** 25 MB, the most a chat file may be (the bucket allows this plus encryption overhead). */
        const val MAX_BYTES = 25L * 1024 * 1024

        /** About 12 KB of JPEG once base64-encoded. */
        const val MAX_THUMB_CHARS = 16_384
    }
}

enum class MediaKind {
    Image, Video, Pdf,

    /** A Word document (.docx or .doc). */
    Doc,
    Other;

    /** Stored as the chat-list preview text of a file message: image, video or pdf. */
    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String?): MediaKind? = entries.firstOrNull { it.wire == value }

        const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val DOC = "application/msword"
        val WORD_TYPES = setOf(DOCX, DOC)

        /** Some file apps call every file application/octet-stream: go by the name then. */
        fun guessMime(reported: String?, name: String): String? {
            val clean = reported?.lowercase()
            if (clean != null && of(clean) != Other) return clean
            return when (name.substringAfterLast('.', "").lowercase()) {
                "pdf" -> "application/pdf"
                "docx" -> DOCX
                "doc" -> DOC
                else -> clean
            }
        }

        fun of(mime: String): MediaKind = when {
            mime.startsWith("image/") -> Image
            mime.startsWith("video/") -> Video
            mime == "application/pdf" -> Pdf
            mime in WORD_TYPES -> Doc
            else -> Other
        }
    }
}

/** What the database keeps for a file message: the wire metadata plus where the plain file is on this phone. */
@Serializable
internal data class StoredAttachment(
    val meta: Attachment,
    /** File name inside [ChatMediaFiles.mediaDir], once the file is on this phone. */
    val local: String? = null,
    /** Set when the file can no longer be fetched (gone from the server, or it failed its check). */
    val unavailable: Boolean = false,
) {
    fun encode(): String = ChatJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String?): StoredAttachment? =
            json?.let { runCatching { ChatJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** The app-private folders for chat files. Never backed up or shared, except through a one-off grant. */
@Singleton
class ChatMediaFiles @Inject constructor(@ApplicationContext context: Context) {
    /** Plain files, named <message id>.<extension>. */
    val mediaDir: File = File(context.filesDir, "chat_media").apply { mkdirs() }

    /** Encrypted copies waiting to be uploaded, named <message id>.bin. */
    val outboxDir: File = File(context.filesDir, "chat_outbox").apply { mkdirs() }

    /** Scratch space for downloads being checked and decrypted. */
    val tempDir: File = File(context.cacheDir, "chat_incoming").apply { mkdirs() }

    fun media(name: String): File = File(mediaDir, name)

    fun outbox(messageId: String): File = File(outboxDir, "$messageId.bin")

    companion object {
        fun extensionFor(mime: String, fallbackName: String): String = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "video/mp4" -> "mp4"
            "video/3gpp" -> "3gp"
            "video/webm" -> "webm"
            "application/pdf" -> "pdf"
            MediaKind.DOCX -> "docx"
            MediaKind.DOC -> "doc"
            else -> fallbackName.substringAfterLast('.', "bin").lowercase().filter { it.isLetterOrDigit() }.take(5).ifEmpty { "bin" }
        }
    }
}

/**
 * File encryption for chat media: AES-256-GCM with a random key per file and a random
 * 12-byte nonce written in front of the ciphertext. GCM's tag catches any change; the
 * SHA-256 of the whole blob (sent inside the Signal message) is checked first as well.
 */
object MediaCrypto {
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    class Sealed(val key: String, val sha256: String)

    fun newKey(): ByteArray = ByteArray(32).also(random::nextBytes)

    /** Encrypts [input] into [output]; returns the base64 key and the ciphertext's SHA-256. */
    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray = newKey()): Sealed {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        }
        val digest = MessageDigest.getInstance("SHA-256")
        fun write(bytes: ByteArray, count: Int = bytes.size) {
            if (count <= 0) return
            output.write(bytes, 0, count)
            digest.update(bytes, 0, count)
        }
        write(nonce)
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            cipher.update(buffer, 0, read)?.let { write(it) }
        }
        write(cipher.doFinal())
        return Sealed(KeyManager.b64(key), hex(digest.digest()))
    }

    /** Checks [sha256] and decrypts; throws if the blob was changed or the key is wrong. */
    fun decrypt(blob: File, key: String, sha256: String, output: OutputStream) {
        check(sha256Of(blob).equals(sha256, ignoreCase = true)) { "checksum mismatch" }
        blob.inputStream().buffered().use { input ->
            val nonce = ByteArray(NONCE_BYTES)
            check(input.readNBytesCompat(nonce) == NONCE_BYTES) { "too short" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(KeyManager.unb64(key), "AES"), GCMParameterSpec(TAG_BITS, nonce))
            }
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                cipher.update(buffer, 0, read)?.let { output.write(it) }
            }
            // GCM releases the last block only after the tag checks out.
            output.write(cipher.doFinal())
        }
    }

    fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    private fun InputStream.readNBytesCompat(target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val read = read(target, total, target.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private const val BUFFER = 64 * 1024
}
