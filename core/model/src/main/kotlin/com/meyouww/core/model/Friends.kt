package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How close two people are. Upgrades need both to accept; either can step down. */
enum class RelationshipLevel(val wire: String, val rank: Int) {
    Friend("friend", 1),
    BestFriend("best_friend", 2),
    Partner("partner", 3);

    /** Levels this one can be lowered to, closest first. */
    val lower: List<RelationshipLevel> get() = entries.filter { it.rank < rank }.sortedByDescending { it.rank }

    /** Levels you can invite someone up to from here. */
    val higher: List<RelationshipLevel> get() = entries.filter { it.rank > rank }

    /** Automatic wallpapers, preapproved alarms and auto-added reminders need at least this. */
    val allowsAutomatic: Boolean get() = rank >= BestFriend.rank

    companion object {
        fun fromWire(value: String?): RelationshipLevel = entries.firstOrNull { it.wire == value } ?: Friend
    }
}

/**
 * What happens if you send this person a wallpaper. Deliberately neutral: the
 * sender never learns why something is unavailable (pause, quiet hours, block).
 */
enum class Capability {
    Auto,
    Ask,
    Unavailable;

    companion object {
        fun fromWire(value: String?): Capability = when (value) {
            "auto" -> Auto
            "ask" -> Ask
            else -> Unavailable
        }
    }
}

enum class WallpaperMode(val wire: String) {
    Ask("ask"),
    Auto("auto"),
    Paused("paused"),
    Never("never");

    companion object {
        fun fromWire(value: String?): WallpaperMode = entries.firstOrNull { it.wire == value } ?: Ask
    }
}

/** What anyone may see of another person: their ID and display name. */
@Serializable
data class PersonCard(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    /** Storage path of their profile picture; readable only by them and their friends. */
    val avatar: String? = null,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username.orEmpty()
    val handle: String get() = username?.let { "@$it" }.orEmpty()
    val initial: String get() = name.firstOrNull()?.uppercase() ?: "♥"
}

@Serializable
data class RelationshipInvite(
    val id: String,
    val direction: String,
    val level: String,
    val label: String? = null,
) {
    val incoming: Boolean get() = direction == "in"
    val levelValue: RelationshipLevel get() = RelationshipLevel.fromWire(level)
}

@Serializable
data class Friend(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val level: String = RelationshipLevel.Friend.wire,
    val label: String? = null,
    val since: String? = null,
    @SerialName("my_mode") val myMode: String = WallpaperMode.Ask.wire,
    @SerialName("they_allow") val theyAllow: String = "unavailable",
    val invite: RelationshipInvite? = null,
) {
    val card: PersonCard get() = PersonCard(id, username, displayName)
    val levelValue: RelationshipLevel get() = RelationshipLevel.fromWire(level)
    val capability: Capability get() = Capability.fromWire(theyAllow)
    val myModeValue: WallpaperMode get() = WallpaperMode.fromWire(myMode)
}

@Serializable
data class FriendRequest(
    val id: String,
    @SerialName("created_at") val createdAt: String? = null,
    val user: PersonCard,
)

@Serializable
data class FriendRequests(
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
)

/** How someone you looked up relates to you. */
enum class LookupRelation {
    Self,
    Friend,
    Requested,
    Incoming,
    None;

    companion object {
        fun fromWire(value: String?): LookupRelation = when (value) {
            "self" -> Self
            "friend" -> Friend
            "requested" -> Requested
            "incoming" -> Incoming
            else -> None
        }
    }
}

@Serializable
data class LookupUser(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val relation: String? = null,
) {
    val card: PersonCard get() = PersonCard(id, username, displayName)
    val relationValue: LookupRelation get() = LookupRelation.fromWire(relation)
}

@Serializable
data class LookupReply(val status: String = "ok", val user: LookupUser? = null)

/** What one contact may do on your phone. Owned and edited only by you. */
@Serializable
data class ContactPermissions(
    @SerialName("contact_id") val contactId: String,
    val version: Int = 1,
    @SerialName("wallpaper_home") val wallpaperHome: Boolean = true,
    @SerialName("wallpaper_lock") val wallpaperLock: Boolean = true,
    @SerialName("doodle_wallpapers") val doodleWallpapers: Boolean = true,
    @SerialName("wallpaper_mode") val wallpaperMode: String = WallpaperMode.Ask.wire,
    @SerialName("paused_until") val pausedUntil: String? = null,
    @SerialName("wallpaper_daily_limit") val wallpaperDailyLimit: Int = 5,
    @SerialName("quiet_start") val quietStart: String? = null,
    @SerialName("quiet_end") val quietEnd: String? = null,
    @SerialName("quiet_behavior") val quietBehavior: String = "hold",
    val receipts: Boolean = true,
    @SerialName("reminder_suggestions") val reminderSuggestions: Boolean = true,
    @SerialName("reminder_auto_categories") val reminderAutoCategories: List<String> = emptyList(),
    @SerialName("alarm_requests") val alarmRequests: Boolean = true,
    @SerialName("alarm_auto") val alarmAuto: Boolean = false,
    @SerialName("alarm_window_start") val alarmWindowStart: String? = null,
    @SerialName("alarm_window_end") val alarmWindowEnd: String? = null,
    @SerialName("alarm_daily_max") val alarmDailyMax: Int = 1,
    @SerialName("share_screen_specs") val shareScreenSpecs: Boolean = false,
) {
    val mode: WallpaperMode get() = WallpaperMode.fromWire(wallpaperMode)
    val hasQuietHours: Boolean get() = quietStart != null && quietEnd != null
}

/** Answers from the permission and relationship functions. */
enum class ChangeResult {
    Ok,
    Pending,
    Accepted,
    Declined,
    AlreadyFriends,
    NotFound,
    NotFriends,
    Gone,
    Conflict,
    NeedsCloserLevel,
    NeedsNotifications,
    AnswerTheirsFirst,
    RateLimited,
    Invalid;

    companion object {
        fun fromWire(status: String?): ChangeResult = when (status) {
            "ok" -> Ok
            "pending" -> Pending
            "accepted" -> Accepted
            "declined" -> Declined
            "already_friends" -> AlreadyFriends
            "not_found" -> NotFound
            "not_friends" -> NotFriends
            "gone" -> Gone
            "conflict" -> Conflict
            "needs_closer_level" -> NeedsCloserLevel
            "needs_notifications" -> NeedsNotifications
            "answer_theirs_first" -> AnswerTheirsFirst
            "rate_limited" -> RateLimited
            else -> Invalid
        }
    }
}

/** Invite links and QR codes: `meyouww://add/<id>`. */
object InviteLinks {
    const val SCHEME = "meyouww"
    const val HOST = "add"

    fun forUsername(username: String): String = "$SCHEME://$HOST/${UserIdRules.normalize(username)}"

    private val linkPattern = Regex("""(?:^|[/\s])add/@?([A-Za-z0-9._]{3,20})(?:$|[/?#\s])""")

    /**
     * Pulls a public ID out of whatever was scanned or pasted: an invite link,
     * "@riya" or "riya". Returns null if nothing valid is in it.
     */
    fun parse(text: String?): String? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 300) return null
        val candidate = linkPattern.find(raw)?.groupValues?.get(1) ?: raw.takeIf { ' ' !in it && '/' !in it && ':' !in it }
        return when (val check = UserIdRules.check(candidate ?: return null)) {
            is UserIdCheck.Valid -> check.normalized
            // Reserved words can still be looked up; the server decides.
            is UserIdCheck.Invalid -> if (check.reason == UserIdCheck.Reason.RESERVED) UserIdRules.normalize(candidate) else null
        }
    }
}
