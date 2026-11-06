package com.meyouww.core.data.wallpaper

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.ReceivedWallpaper
import com.meyouww.core.model.SendOutcome
import com.meyouww.core.model.SentWallpaper
import com.meyouww.core.model.WallpaperCommandReply
import com.meyouww.core.model.WallpaperTarget
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class SendResult(val outcome: SendOutcome, val commandId: String?)

/**
 * Remote wallpapers. Images go to the private "wallpapers" bucket under the
 * sender's own folder; everything else is decided by Postgres functions.
 */
@Singleton
class WallpaperRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    /**
     * Uploads the image and asks the server to deliver it. [applyLocal] is a wall-clock
     * time in the RECIPIENT's time zone ("7:00 tomorrow, their time"); the server converts it.
     */
    suspend fun send(
        recipientId: String,
        target: WallpaperTarget,
        bytes: ByteArray,
        mime: String,
        width: Int,
        height: Int,
        isDoodle: Boolean,
        applyLocal: LocalDateTime?,
    ): Outcome<SendResult> = outcomeOf {
        val client = supabase.client()
        val uid = client.auth.currentUserOrNull()?.id ?: error("signed out")
        val ext = if (mime == "image/png") "png" else "webp"
        val path = "$uid/${UUID.randomUUID()}.$ext"
        val bucket = client.storage.from(BUCKET)
        bucket.upload(path, bytes) {
            upsert = false
            contentType = ContentType.parse(mime)
        }
        val body = supabase.rpcBody(
            "create_wallpaper_command",
            buildJsonObject {
                put("p_recipient", recipientId)
                put("p_target", target.wire)
                put("p_path", path)
                put("p_mime", mime)
                put("p_width", width)
                put("p_height", height)
                put("p_bytes", bytes.size)
                put("p_sha256", sha256(bytes))
                put("p_is_doodle", isDoodle)
                put("p_apply_local", applyLocal?.withNano(0)?.toString())
            },
        )
        val outcome = SendOutcome.fromWire(statusOf(body))
        val commandId = runCatching { RpcJson.parseToJsonElement(body).jsonObject["command_id"]?.jsonPrimitive?.content }.getOrNull()
        if (outcome !in setOf(SendOutcome.Sent, SendOutcome.Scheduled, SendOutcome.Held)) {
            runCatching { bucket.delete(path) } // nothing will ever read it
        }
        SendResult(outcome, commandId)
    }

    suspend fun fetch(commandId: String): Outcome<WallpaperCommandReply> = outcomeOf {
        RpcJson.decodeFromString<WallpaperCommandReply>(
            supabase.rpcBody("get_wallpaper_command", buildJsonObject { put("p_id", commandId) }),
        )
    }

    suspend fun download(path: String): Outcome<ByteArray> = outcomeOf {
        supabase.client().storage.from(BUCKET).downloadAuthenticated(path)
    }

    /** Reports the real outcome. Returns false if the server no longer accepts it. */
    suspend fun report(commandId: String, status: String, reason: String? = null): Outcome<Boolean> = outcomeOf {
        val body = supabase.rpcBody(
            "report_wallpaper_result",
            buildJsonObject {
                put("p_id", commandId)
                put("p_status", status)
                put("p_reason", reason)
            },
        )
        statusOf(body) == "ok"
    }

    suspend fun cancel(commandId: String): Outcome<Boolean> = outcomeOf {
        statusOf(supabase.rpcBody("cancel_wallpaper_command", buildJsonObject { put("p_id", commandId) })) == "ok"
    }

    suspend fun sent(): Outcome<List<SentWallpaper>> = outcomeOf {
        RpcJson.decodeFromString<List<SentWallpaper>>(supabase.rpcBody("list_sent_wallpapers"))
    }

    suspend fun received(): Outcome<List<ReceivedWallpaper>> = outcomeOf {
        RpcJson.decodeFromString<List<ReceivedWallpaper>>(supabase.rpcBody("list_received_wallpapers"))
    }

    /** The signed-in user's ID, to double-check commands are really for this account. */
    suspend fun myId(): String? = supabase.client().auth.currentUserOrNull()?.id

    companion object {
        const val BUCKET = "wallpapers"

        private const val HEX = "0123456789abcdef"

        fun sha256(bytes: ByteArray): String = buildString {
            for (b in MessageDigest.getInstance("SHA-256").digest(bytes)) {
                append(HEX[(b.toInt() shr 4) and 0xF])
                append(HEX[b.toInt() and 0xF])
            }
        }
    }
}
