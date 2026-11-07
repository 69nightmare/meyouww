package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/** Everything that can show up in the heart inbox. Unknown kinds come from newer servers. */
enum class ActivityKind(val wire: String) {
    FriendRequest("friend_request"),
    FriendAccepted("friend_accepted"),
    RelationshipInvite("relationship_invite"),
    RelationshipAccepted("relationship_accepted"),
    WallpaperRequest("wallpaper_request"),
    WallpaperReceived("wallpaper_received"),
    WallpaperApplied("wallpaper_applied"),
    WallpaperDeclined("wallpaper_declined"),
    DoodleReceived("doodle_received"),
    EventInvite("event_invite"),
    EventAdded("event_added"),
    EventAccepted("event_accepted"),
    EventDeclined("event_declined"),
    EventCancelled("event_cancelled"),
    AlarmRequest("alarm_request"),
    AlarmAdded("alarm_added"),
    AlarmAccepted("alarm_accepted"),
    AlarmDeclined("alarm_declined"),
    Unknown("");

    companion object {
        fun fromWire(value: String?): ActivityKind = entries.firstOrNull { it.wire == value && it != Unknown } ?: Unknown
    }
}

/** One heart-inbox entry. */
@Serializable
data class ActivityItem(
    val id: String,
    val kind: String,
    @SerialName("created_at") val createdAt: String,
    val read: Boolean = false,
    val resolution: String? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    val actor: PersonCard? = null,
    val actionable: Boolean = false,
) {
    val kindValue: ActivityKind get() = ActivityKind.fromWire(kind)
    val createdInstant: Instant? get() = parseInstant(createdAt)

    /** A string field from [data], for example the relationship level or a partner label. */
    fun dataString(key: String): String? = (data[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** "just now", "5 min", "3 h", "2 d", or a date: decided here, worded by the UI. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Long) : RelativeTime
    data class Hours(val value: Long) : RelativeTime
    data class Days(val value: Long) : RelativeTime
    data class OnDate(val instant: Instant) : RelativeTime

    companion object {
        fun between(then: Instant, now: Instant): RelativeTime {
            val d = Duration.between(then, now)
            return when {
                d.isNegative || d.toMinutes() < 1 -> JustNow
                d.toHours() < 1 -> Minutes(d.toMinutes())
                d.toDays() < 1 -> Hours(d.toHours())
                d.toDays() < 7 -> Days(d.toDays())
                else -> OnDate(then)
            }
        }
    }
}

/** Parses Postgres timestamps as returned in JSON ("2026-11-07T09:00:00.123+00:00"). */
fun parseInstant(text: String?): Instant? =
    text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull() }
