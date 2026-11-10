package com.meyouww.core.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A poll's choices and everyone's votes (user ID -> chosen option indexes). Kept on the phone. */
@Serializable
data class PollState(
    val options: List<String>,
    val multi: Boolean = false,
    val votes: Map<String, List<Int>> = emptyMap(),
) {
    /** How many people chose each option. */
    fun counts(): List<Int> = options.indices.map { i -> votes.values.count { i in it } }

    fun encode(): String = ChatJson.encodeToString(serializer(), this)

    companion object {
        const val MAX_OPTIONS = 8
        const val MAX_OPTION_CHARS = 80

        fun decode(json: String?): PollState? = json?.let { runCatching { ChatJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** A reminder for everyone in a chat; [added] once it is in this phone's calendar. */
@Serializable
data class ReminderState(
    /** Local date-time, e.g. "2026-11-20T18:30". */
    val start: String,
    val added: Boolean = false,
    /** The calendar event it became on this phone (cancelled if the reminder is deleted). */
    val eventId: String? = null,
) {
    fun encode(): String = ChatJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String?): ReminderState? = json?.let { runCatching { ChatJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/** What creating a group, adding, removing or leaving came to. */
sealed interface GroupChange {
    data class Done(val conversation: ChatConversation) : GroupChange

    /**
     * The server's reason: not_friends, invalid, full, not_admin, not_member, rate_limited, or
     * needs_update ([users] are on a version without groups and must update first).
     */
    data class Refused(val reason: String, val users: List<String> = emptyList()) : GroupChange
}

/** Someone in a group, as the UI shows them. */
data class ChatGroupMember(
    val userId: String,
    val name: String,
    val username: String?,
    val avatar: String?,
    val admin: Boolean,
)

/**
 * Group receipts: who one of my messages went to, each with their furthest receipt
 * (sent, delivered, read). The light is yellow once it reached all their phones and
 * green once all of them have seen it. People added later don't hold it back.
 */
object GroupReceipts {
    private val RANK = mapOf(ChatRepository.STATUS_DELIVERED to 1, ChatRepository.STATUS_READ to 2)

    fun decode(json: String?): Map<String, String> = json?.let {
        runCatching { ChatJson.decodeFromString<Map<String, String>>(it) }.getOrNull()
    }.orEmpty()

    fun encode(map: Map<String, String>): String = ChatJson.encodeToString(map)

    /** [current] with [from]'s receipt moved forward (never back); ignored if it didn't go to them. */
    fun advance(current: Map<String, String>, from: String, status: String): Map<String, String> {
        if (from !in current) return current
        val before = RANK[current[from]] ?: 0
        val after = RANK[status] ?: return current
        return if (after > before) current + (from to status) else current
    }

    /** The message's status once these receipts are in ([fallback] until everyone has it). */
    fun status(receipts: Map<String, String>, fallback: String): String {
        if (receipts.isEmpty()) return fallback
        val ranks = receipts.values.map { RANK[it] ?: 0 }
        return when {
            ranks.all { it >= 2 } -> ChatRepository.STATUS_READ
            ranks.all { it >= 1 } -> ChatRepository.STATUS_DELIVERED
            else -> fallback
        }
    }
}

/** One group from list_my_groups. */
@Serializable
internal data class RemoteGroup(
    @SerialName("group_id") val groupId: String,
    @SerialName("created_by") val createdBy: String? = null,
    val members: List<RemoteMember> = emptyList(),
)

@Serializable
internal data class RemoteMember(
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    val name: String = "",
    val username: String? = null,
    val avatar: String? = null,
)
