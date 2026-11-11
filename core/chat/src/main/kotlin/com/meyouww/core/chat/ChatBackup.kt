package com.meyouww.core.chat

import android.content.Context
import androidx.room.withTransaction
import com.meyouww.core.data.di.SupabaseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Everything a backup holds besides the files: the chats, as they are stored on the phone. */
@Serializable
internal data class BackupManifest(
    val v: Int = 1,
    /** The meyouww account the chats belong to: a backup is only restored into the same one. */
    val owner: String,
    val createdAt: Long,
    val conversations: List<ConversationRow>,
    val messages: List<MessageRow>,
    val members: List<GroupMemberRow> = emptyList(),
)

/** The backup that is in Google Drive right now. */
data class BackupInfo(val at: Long, val bytes: Long, val files: Int)

sealed interface BackupResult {
    data class Done(val messages: Int, val files: Int) : BackupResult

    /** Google Drive refused: the permission was taken away. Ask again from the screen. */
    data object DriveDenied : BackupResult

    /** No connection, or Google Drive or meyouww could not be reached. Try later. */
    data object Offline : BackupResult

    data object Failed : BackupResult
}

sealed interface RestoreResult {
    /** [messages] were added; [filesWaiting] photos, videos and documents still have to come down. */
    data class Done(val messages: Int, val filesWaiting: Int) : RestoreResult

    data object NoBackup : RestoreResult

    /** The backup in this Drive belongs to another meyouww account. */
    data object WrongAccount : RestoreResult

    data object DriveDenied : RestoreResult

    data object Offline : RestoreResult

    data object Failed : RestoreResult
}

data class BackupState(
    /** Back up every night. */
    val nightly: Boolean = false,
    val includeMedia: Boolean = true,
    val wifiOnly: Boolean = true,
    /** When the last backup from this phone finished (0: never). */
    val lastAt: Long = 0,
    val running: Boolean = false,
    /** While running: how far it is, e.g. files done and files in all. */
    val done: Int = 0,
    val total: Int = 0,
)

/**
 * Chat backup in the person's own Google Drive, the way WhatsApp does it but with nothing to
 * remember: everything is encrypted on the phone (AES-256-GCM) with a key that only meyouww's
 * server keeps for this account, then stored in a hidden app folder of their Drive. Google has
 * the data without the key; the server has the key without the data.
 *
 * What goes in: conversations, messages (with pins, stars and nicknames), group members, and,
 * if wanted, the photos, videos and documents. What never goes in: the Signal keys and
 * sessions. A new phone makes new keys; the backup only brings the history.
 */
@Singleton
class ChatBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: ChatDatabase,
    private val supabase: SupabaseProvider,
    private val media: ChatMediaFiles,
    private val keys: KeyManager,
) {
    private val dao = db.messages()
    private val prefs = context.getSharedPreferences("chat_backup", Context.MODE_PRIVATE)
    private val lock = Mutex()

    private val _state = MutableStateFlow(
        BackupState(
            nightly = prefs.getBoolean(K_NIGHTLY, false),
            includeMedia = prefs.getBoolean(K_MEDIA, true),
            wifiOnly = prefs.getBoolean(K_WIFI, true),
            lastAt = prefs.getLong(K_LAST, 0),
        ),
    )
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun setNightly(on: Boolean) {
        prefs.edit().putBoolean(K_NIGHTLY, on).apply()
        _state.update { it.copy(nightly = on) }
    }

    fun setIncludeMedia(on: Boolean) {
        prefs.edit().putBoolean(K_MEDIA, on).apply()
        _state.update { it.copy(includeMedia = on) }
    }

    fun setWifiOnly(on: Boolean) {
        prefs.edit().putBoolean(K_WIFI, on).apply()
        _state.update { it.copy(wifiOnly = on) }
    }

    /** True once, on a phone with no chats yet: a good moment to offer bringing them back. */
    suspend fun shouldOfferRestore(): Boolean = withContext(Dispatchers.IO) {
        if (prefs.getBoolean(K_OFFERED, false)) return@withContext false
        dao.conversationCount() == 0
    }

    fun restoreOffered() {
        prefs.edit().putBoolean(K_OFFERED, true).apply()
    }

    /** What is in Google Drive now, or null if there is no backup (or it can't be reached). */
    suspend fun latest(token: String): BackupInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val files = DriveClient(token).list()
            val manifest = files.filter { it.name == MANIFEST }.maxByOrNull { it.modifiedTime.orEmpty() } ?: return@runCatching null
            BackupInfo(
                at = manifest.modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
                bytes = files.sumOf { it.size },
                files = files.count { it.name.startsWith(MEDIA_PREFIX) },
            )
        }.getOrNull()
    }

    /**
     * Backs up now. Files already in Drive are not sent again, and files deleted on the phone
     * are deleted from the backup, so each night only sends what is new.
     */
    suspend fun backUp(token: String): BackupResult = withContext(Dispatchers.IO) {
        lock.withLock {
            _state.update { it.copy(running = true, done = 0, total = 0) }
            val manifestBlob = File(context.cacheDir, "backup-manifest.bin")
            val mediaBlob = File(context.cacheDir, "backup-media.bin")
            try {
                val key = backupKey()
                val owner = keys.ownerId() ?: return@withLock BackupResult.Failed
                val messages = dao.allMessages()
                val manifest = BackupManifest(
                    owner = owner,
                    createdAt = System.currentTimeMillis(),
                    conversations = dao.allConversations(),
                    messages = messages,
                    members = dao.allGroupMembers(),
                )
                val json = ChatJson.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
                manifestBlob.outputStream().buffered().use { MediaCrypto.encrypt(json.inputStream(), it, key) }

                val drive = DriveClient(token)
                val existing = drive.list()
                var files = 0
                if (_state.value.includeMedia) {
                    val wanted = messages.filter { !it.deleted }.mapNotNull { StoredAttachment.decode(it.attachment)?.local }.toSet()
                    val have = existing.filter { it.name.startsWith(MEDIA_PREFIX) }.associateBy { it.name.removePrefix(MEDIA_PREFIX) }
                    val missing = wanted.filter { it !in have && media.media(it).exists() }
                    files = wanted.count { it in have }
                    _state.update { it.copy(done = 0, total = missing.size) }
                    for (name in missing) {
                        val plain = media.media(name)
                        mediaBlob.outputStream().buffered().use { out -> plain.inputStream().buffered().use { MediaCrypto.encrypt(it, out, key) } }
                        drive.upload(MEDIA_PREFIX + name, mediaBlob)
                        files++
                        _state.update { it.copy(done = it.done + 1) }
                    }
                    // Deleted on the phone (or the chat was cleared): out of the backup too.
                    have.filterKeys { it !in wanted }.values.forEach { runCatching { drive.delete(it.id) } }
                }
                // The new list of chats goes up last, then the copies it replaces go.
                drive.upload(MANIFEST, manifestBlob)
                existing.filter { it.name == MANIFEST }.forEach { runCatching { drive.delete(it.id) } }

                val at = System.currentTimeMillis()
                prefs.edit().putLong(K_LAST, at).apply()
                _state.update { it.copy(lastAt = at) }
                BackupResult.Done(messages.size, files)
            } catch (e: CancellationException) {
                throw e
            } catch (e: DriveException) {
                if (e.denied) BackupResult.DriveDenied else BackupResult.Offline
            } catch (e: IOException) {
                BackupResult.Offline
            } catch (e: Exception) {
                BackupResult.Failed
            } finally {
                manifestBlob.delete()
                mediaBlob.delete()
                _state.update { it.copy(running = false) }
            }
        }
    }

    /**
     * Brings the chats in Google Drive onto this phone. What is already here stays: only
     * missing chats and messages are added. Photos, videos and documents come down after,
     * with [restoreMedia] (they can be big).
     */
    suspend fun restore(token: String): RestoreResult = withContext(Dispatchers.IO) {
        lock.withLock {
            _state.update { it.copy(running = true, done = 0, total = 0) }
            val blob = File(context.cacheDir, "restore-manifest.bin")
            try {
                val key = backupKey()
                val owner = keys.ownerId() ?: return@withLock RestoreResult.Failed
                val drive = DriveClient(token)
                val entry = drive.list().filter { it.name == MANIFEST }.maxByOrNull { it.modifiedTime.orEmpty() }
                    ?: return@withLock RestoreResult.NoBackup
                drive.download(entry.id, blob)
                val plain = ByteArrayOutputStream()
                MediaCrypto.decrypt(blob, KeyManager.b64(key), MediaCrypto.sha256Of(blob), plain)
                val manifest = ChatJson.decodeFromString(BackupManifest.serializer(), plain.toString(Charsets.UTF_8.name()))
                if (manifest.owner != owner) return@withLock RestoreResult.WrongAccount

                var added = 0
                db.withTransaction {
                    // A chat that already exists here (same ID, or the same person) keeps its row.
                    val idHere = mutableMapOf<String, String>()
                    for (c in manifest.conversations) {
                        val local = dao.conversation(c.id) ?: dao.conversationWith(c.peerId)
                        if (local == null) dao.putConversation(c.copy(unread = 0))
                        idHere[c.id] = local?.id ?: c.id
                    }
                    val rows = manifest.messages.mapNotNull { m ->
                        val conversation = idHere[m.conversationId] ?: return@mapNotNull null
                        // Something that was still on its way out when the backup was made never left.
                        val status = if (m.status == ChatRepository.STATUS_SENDING || m.status == ChatRepository.STATUS_WAITING) {
                            ChatRepository.STATUS_FAILED
                        } else {
                            m.status
                        }
                        m.copy(conversationId = conversation, status = status)
                    }
                    rows.chunked(400).forEach { chunk -> added += dao.insertMessagesIfNew(chunk).count { it != -1L } }
                    val groupsHere = manifest.members.groupBy { it.groupId }
                    for ((group, members) in groupsHere) {
                        if (dao.groupMembers(group).isEmpty()) dao.putGroupMembers(members)
                    }
                }
                RestoreResult.Done(added, filesWaiting())
            } catch (e: CancellationException) {
                throw e
            } catch (e: DriveException) {
                if (e.denied) RestoreResult.DriveDenied else RestoreResult.Offline
            } catch (e: IOException) {
                RestoreResult.Offline
            } catch (e: Exception) {
                // Wrong key or a damaged file: nothing was changed.
                RestoreResult.Failed
            } finally {
                blob.delete()
                _state.update { it.copy(running = false) }
            }
        }
    }

    /** Downloads the photos, videos and documents that messages here point to. Returns how many are still missing. */
    suspend fun restoreMedia(token: String): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            val blob = File(context.cacheDir, "restore-media.bin")
            try {
                val key = KeyManager.b64(backupKey())
                val missing = missingFiles()
                if (missing.isEmpty()) return@withLock 0
                val drive = DriveClient(token)
                val have = drive.list().filter { it.name.startsWith(MEDIA_PREFIX) }.associateBy { it.name.removePrefix(MEDIA_PREFIX) }
                val wanted = missing.filter { it in have }
                _state.update { it.copy(running = true, done = 0, total = wanted.size) }
                for (name in wanted) {
                    val target = media.media(name)
                    val part = File(target.parentFile, "$name.part")
                    try {
                        drive.download(have.getValue(name).id, blob)
                        part.outputStream().buffered().use { MediaCrypto.decrypt(blob, key, MediaCrypto.sha256Of(blob), it) }
                        if (!part.renameTo(target)) part.copyTo(target, overwrite = true)
                    } finally {
                        part.delete()
                    }
                    _state.update { it.copy(done = it.done + 1) }
                }
                filesWaiting()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                filesWaiting()
            } finally {
                blob.delete()
                _state.update { it.copy(running = false) }
            }
        }
    }

    /** Files that messages point to but that aren't on this phone. */
    private suspend fun missingFiles(): List<String> =
        dao.liveAttachments().mapNotNull { StoredAttachment.decode(it)?.local }.distinct().filter { !media.media(it).exists() }

    private suspend fun filesWaiting(): Int = missingFiles().size

    /** This account's backup key from the server (the same one every time). */
    private suspend fun backupKey(): ByteArray {
        val reply = supabase.call("backup_key", buildJsonObject { }).jsonObject
        val key = (reply["key"] as? JsonPrimitive)?.content ?: throw IOException("no backup key")
        return KeyManager.unb64(key).also { check(it.size == 32) { "bad backup key" } }
    }

    private companion object {
        const val MANIFEST = "meyouww-chats-v1"
        const val MEDIA_PREFIX = "m-"
        const val K_NIGHTLY = "nightly"
        const val K_MEDIA = "include_media"
        const val K_WIFI = "wifi_only"
        const val K_LAST = "last_at"
        const val K_OFFERED = "restore_offered"
    }
}
