package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a wallpaper goes. */
enum class WallpaperTarget(val wire: String) {
    Home("home"),
    Lock("lock"),
    Both("both");

    companion object {
        fun fromWire(value: String?): WallpaperTarget = entries.firstOrNull { it.wire == value } ?: Lock
    }
}

/** A command as fetched by the recipient's phone. */
@Serializable
data class WallpaperCommand(
    val id: String,
    val sender: PersonCard,
    @SerialName("recipient_id") val recipientId: String,
    val target: String,
    @SerialName("storage_path") val storagePath: String,
    val mime: String,
    val width: Int,
    val height: Int,
    val bytes: Int,
    val sha256: String,
    val mode: String,
    val sequence: Long,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("still_auto") val stillAuto: Boolean = false,
) {
    val targetValue: WallpaperTarget get() = WallpaperTarget.fromWire(target)
    val isAuto: Boolean get() = mode == "auto"
}

@Serializable
data class WallpaperCommandReply(val status: String, val command: WallpaperCommand? = null)

/** Answer to "send this". The sender only ever sees neutral outcomes. */
enum class SendOutcome {
    Sent,
    Scheduled,
    Held,
    Unavailable,
    RateLimited,
    Invalid;

    companion object {
        fun fromWire(status: String?): SendOutcome = when (status) {
            "sent" -> Sent
            "scheduled" -> Scheduled
            "held" -> Held
            "unavailable" -> Unavailable
            "rate_limited" -> RateLimited
            else -> Invalid
        }
    }
}

/** A wallpaper you sent, with a neutral status. */
@Serializable
data class SentWallpaper(
    val id: String,
    val recipient: PersonCard,
    val target: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("apply_at") val applyAt: String? = null,
)

/** "What happened on my phone": one remote wallpaper and its outcome. */
@Serializable
data class ReceivedWallpaper(
    val id: String,
    val sender: PersonCard,
    val target: String,
    val mode: String,
    val status: String,
    @SerialName("fail_reason") val failReason: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** Why applying failed, reported to the server (short codes only). */
object FailReason {
    const val NOT_ALLOWED = "not_allowed"
    const val INTEGRITY = "integrity"
    const val MEMORY = "memory"
    const val APPLY_ERROR = "apply_error"
    const val DOWNLOAD = "download"
    const val REPLAY = "replay"
    const val EXPIRED = "expired"
}
