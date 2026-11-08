package com.meyouww.core.notifications.push

import android.content.Context
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.wallpaper.WallpaperApplier
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.database.ChatWallpaperEntity
import com.meyouww.core.database.ProcessedCommandEntity
import com.meyouww.core.database.WallpaperDao
import com.meyouww.core.model.FailReason
import com.meyouww.core.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A wallpaper sent or received, as the chat shows it. [file] is null when it isn't on this phone. */
data class ChatWallpaper(
    val commandId: String,
    val peerId: String,
    val fromMe: Boolean,
    val file: File?,
    val target: WallpaperTarget,
    val status: String,
    val at: Long,
)

/**
 * Every wallpaper sent or received, kept on this phone: the server deletes its copy
 * as soon as the picture arrives, so the chat shows these files.
 */
@Singleton
class WallpaperGallery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: WallpaperDao,
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun forPeer(peerId: String): Flow<List<ChatWallpaper>> = dao.gallery(peerId).map { rows ->
        rows.map { row ->
            ChatWallpaper(
                commandId = row.commandId,
                peerId = row.peerId,
                fromMe = row.fromMe,
                file = row.file.takeIf { it.isNotEmpty() }?.let { File(dir, it) }?.takeIf { it.isFile },
                target = WallpaperTarget.fromWire(row.target),
                status = row.status,
                at = row.at,
            )
        }
    }.flowOn(Dispatchers.IO) // file checks stay off the main thread, so the chat scrolls smoothly

    /** A picture applied before the chat kept its own copy: the Undo copy, if it's still here. */
    suspend fun historyCopy(commandId: String): File? = withContext(Dispatchers.IO) {
        val dir = WallpaperApplier.historyDir(context)
        HISTORY_EXTENSIONS.map { File(dir, "${safe(commandId)}.$it") }.firstOrNull { it.isFile }
    }

    /** Saves the picture (before anything else can go wrong) and remembers it for the chat. */
    suspend fun keep(commandId: String, peerId: String, fromMe: Boolean, bytes: ByteArray, extension: String, target: WallpaperTarget, status: String) =
        withContext(Dispatchers.IO) {
            val name = "${safe(commandId)}.$extension"
            File(dir, name).writeBytes(bytes)
            val existing = dao.galleryItem(commandId)
            dao.saveToGallery(
                ChatWallpaperEntity(commandId, peerId, fromMe, name, target.wire, status, existing?.at ?: System.currentTimeMillis()),
            )
        }

    /** The picture already on this phone for [commandId], if any (a second try needs no download). */
    suspend fun bytes(commandId: String): ByteArray? = withContext(Dispatchers.IO) {
        val row = dao.galleryItem(commandId) ?: return@withContext null
        File(dir, row.file).takeIf { row.file.isNotEmpty() && it.isFile }?.readBytes()
    }

    /** Keeps the chat's status in step with the server (delivered, applied, ...). */
    suspend fun setStatus(commandId: String, status: String) = dao.setGalleryStatus(commandId, status)

    /** Sign-out: this phone forgets the old account's pictures. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearGallery()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun safe(id: String) = id.filter { it.isLetterOrDigit() || it == '-' }.take(64)

    companion object {
        private const val DIR = "wallpaper_gallery"
        private val HISTORY_EXTENSIONS = listOf("webp", "png", "jpg")
    }
}

/**
 * Applies a friend's wallpaper on this phone, even with meyouww closed and the phone
 * locked: fetch the command over HTTPS → check it is for us, newer than the last one
 * from that sender and still allowed → download (kept in the gallery) → check SHA-256
 * → apply with WallpaperManager → report the real outcome. Never acts on the push alone.
 *
 * Runs straight from the push (FCM gives a few seconds with network) and, if that
 * doesn't finish, again from [WallpaperWorker]. One at a time.
 */
@Singleton
class WallpaperDelivery @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val wallpapers: WallpaperRepository,
    private val dao: WallpaperDao,
    private val gallery: WallpaperGallery,
    private val notifications: AndroidPushNotifications,
) {
    enum class Step { Done, Retry }

    private val lock = Mutex()

    /**
     * [target] overrides the sender's choice (an owner's own Apply); [canRetry] says
     * whether a network problem should be tried again later rather than reported.
     */
    suspend fun process(id: String, approved: Boolean, target: WallpaperTarget?, canRetry: Boolean): Step =
        lock.withLock { run(id, approved, target, canRetry) }

    private suspend fun run(id: String, approved: Boolean, override: WallpaperTarget?, canRetry: Boolean): Step {
        if (!auth.ensureFreshSession()) return if (canRetry) Step.Retry else Step.Done

        val done = dao.processed(id)
        if (done != null) {
            if (done.outcome == OUT_APPLIED_UNREPORTED) return reportApplied(id, done.senderId, done.sequence, canRetry)
            // An "ask" command may still be approved later; everything else is finished.
            if (!(done.outcome == OUT_AWAITING && approved)) return Step.Done
        }

        val reply = when (val r = wallpapers.fetch(id)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return retryOrGiveUp(r.error, id, null, canRetry)
        }
        val cmd = reply.command
        if (reply.status != "ok" || cmd == null) {
            notifications.cancelWallpaper(id)
            dao.markProcessed(ProcessedCommandEntity(id, "", 0, reply.status, now()))
            return Step.Done
        }
        val name = cmd.sender.name
        if (cmd.recipientId != wallpapers.myId()) {
            notifications.cancelWallpaper(id)
            return Step.Done
        }
        val last = dao.lastSequence(cmd.sender.id)
        if (done == null && last != null && cmd.sequence <= last) {
            return fail(id, cmd.sender.id, cmd.sequence, name, FailReason.REPLAY)
        }
        if (!approved && !(cmd.isAuto && cmd.stillAuto)) {
            // Needs the owner's OK: the inbox and its notification carry Put it on / Not now.
            notifications.cancelWallpaper(id)
            wallpapers.report(id, "awaiting_approval")
            dao.markProcessed(ProcessedCommandEntity(id, cmd.sender.id, cmd.sequence, OUT_AWAITING, now()))
            return Step.Done
        }
        if (!WallpaperApplier.canApply(context)) return fail(id, cmd.sender.id, cmd.sequence, name, FailReason.NOT_ALLOWED)
        if (approved) notifications.showWallpaperIncoming(id)
        wallpapers.report(id, "applying")

        val target = override ?: cmd.targetValue
        val extension = if (cmd.mime == "image/png") "png" else "webp"
        val bytes = gallery.bytes(id) ?: when (val d = wallpapers.download(cmd.storagePath)) {
            is Outcome.Success -> d.value.also {
                // Kept on this phone first, then nothing stays on the server.
                gallery.keep(id, cmd.sender.id, fromMe = false, bytes = it, extension = extension, target = target, status = "delivered")
                wallpapers.deleteMedia(cmd.storagePath)
            }
            is Outcome.Failure -> return retryOrGiveUp(d.error, id, Triple(cmd.sender.id, cmd.sequence, name), canRetry)
        }
        if (bytes.size != cmd.bytes || WallpaperRepository.sha256(bytes) != cmd.sha256) {
            return fail(id, cmd.sender.id, cmd.sequence, name, FailReason.INTEGRITY)
        }
        val file = WallpaperApplier.saveToHistory(context, cmd.id, bytes, extension)
        try {
            WallpaperApplier.apply(context, bytes, target)
        } catch (e: OutOfMemoryError) {
            file.delete()
            return fail(id, cmd.sender.id, cmd.sequence, name, FailReason.MEMORY)
        } catch (e: Exception) {
            file.delete()
            return fail(id, cmd.sender.id, cmd.sequence, name, FailReason.APPLY_ERROR)
        }
        dao.markProcessed(ProcessedCommandEntity(id, cmd.sender.id, cmd.sequence, OUT_APPLIED_UNREPORTED, now()))
        dao.addApplied(cmd.id, target.wire, file.name, name, now())
        gallery.setStatus(id, "applied")
        trimHistory()
        notifications.showWallpaperApplied(id, cmd.sender.id, name, target)
        return reportApplied(id, cmd.sender.id, cmd.sequence, canRetry)
    }

    private suspend fun reportApplied(id: String, senderId: String, sequence: Long, canRetry: Boolean): Step =
        when (wallpapers.report(id, "applied")) {
            is Outcome.Success -> {
                dao.markProcessed(ProcessedCommandEntity(id, senderId, sequence, OUT_APPLIED, now()))
                Step.Done
            }
            // Already applied: only the receipt is pending, so keep retrying it.
            is Outcome.Failure -> if (canRetry) Step.Retry else Step.Done
        }

    private suspend fun fail(id: String, senderId: String, sequence: Long, name: String?, reason: String): Step {
        wallpapers.report(id, "failed", reason)
        dao.markProcessed(ProcessedCommandEntity(id, senderId, sequence, "failed", now()))
        gallery.setStatus(id, "failed")
        notifications.showWallpaperFailed(id, name)
        return Step.Done
    }

    private suspend fun retryOrGiveUp(error: AppError, id: String, cmd: Triple<String, Long, String>?, canRetry: Boolean): Step {
        if (error == AppError.Network && canRetry) return Step.Retry
        if (cmd != null) return fail(id, cmd.first, cmd.second, cmd.third, FailReason.DOWNLOAD)
        notifications.cancelWallpaper(id)
        return Step.Done
    }

    /** Keeps the last 10 meyouww wallpapers per target for Undo (the chat gallery keeps its own copy). */
    private suspend fun trimHistory() {
        val all = dao.allApplied()
        val keep = HashMap<String, Int>()
        for (row in all) {
            val n = (keep[row.target] ?: 0) + 1
            keep[row.target] = n
            if (n > HISTORY) {
                File(WallpaperApplier.historyDir(context), row.file).delete()
                dao.deleteApplied(row.id)
            }
        }
    }

    companion object {
        private const val HISTORY = 10
        const val OUT_APPLIED = "applied"
        const val OUT_APPLIED_UNREPORTED = "applied_unreported"
        const val OUT_AWAITING = "awaiting"

        private fun now() = System.currentTimeMillis()
    }
}
