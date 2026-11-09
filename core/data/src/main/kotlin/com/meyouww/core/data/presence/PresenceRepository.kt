package com.meyouww.core.data.presence

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.parseInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What a friend is up to right now, as far as the chat shows it. */
data class Presence(
    val online: Boolean,
    /** When they last had meyouww open; null if never reported. */
    val lastSeen: Instant?,
    /** They have your chat open on their screen. */
    val inChat: Boolean,
    val typing: Boolean,
)

/**
 * Online / last seen / typing, for friends only (see migration 20261109160000_presence).
 * The phone reports a heartbeat while the app is in front; a chat asks about the friend
 * on the other side while it is open.
 */
@Singleton
class PresenceRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    @Serializable
    private data class Reply(
        val status: String? = null,
        val online: Boolean = false,
        @SerialName("last_seen") val lastSeen: String? = null,
        @SerialName("in_chat") val inChat: Boolean = false,
        val typing: Boolean = false,
    )

    /** [openChat]: the conversation on screen, if any; [typing]: they are typing in it. */
    suspend fun heartbeat(openChat: String?, typing: Boolean = false): Outcome<Unit> = outcomeOf {
        supabase.rpcBody(
            "set_presence",
            buildJsonObject {
                put("p_open_chat", openChat)
                put("p_typing", typing && openChat != null)
            },
        )
        Unit
    }

    suspend fun peer(userId: String): Outcome<Presence?> = outcomeOf {
        val reply = RpcJson.decodeFromString(Reply.serializer(), supabase.rpcBody("peer_presence", buildJsonObject { put("p_user", userId) }))
        if (reply.status != "ok") {
            null
        } else {
            Presence(
                online = reply.online,
                lastSeen = parseInstant(reply.lastSeen),
                inChat = reply.inChat,
                typing = reply.typing,
            )
        }
    }
}
