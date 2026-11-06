package com.meyouww.core.data.friends

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.ChangeResult
import com.meyouww.core.model.ContactPermissions
import com.meyouww.core.model.Friend
import com.meyouww.core.model.FriendRequests
import com.meyouww.core.model.LookupReply
import com.meyouww.core.model.PersonCard
import com.meyouww.core.model.RelationshipLevel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Your settings for one contact, with your relationship level. */
data class PermissionsSnapshot(val level: RelationshipLevel, val permissions: ContactPermissions)

/** Result of a permission change; [permissions] is the server's latest copy when known. */
data class PermissionUpdate(val result: ChangeResult, val permissions: ContactPermissions?)

@Serializable
private data class UserSettingsRow(@SerialName("remote_paused") val remotePaused: Boolean = false)

/**
 * Friends, requests, relationship levels, blocks and per-contact permissions.
 * Every write goes through a Postgres function that checks it on the server.
 */
@Singleton
class FriendsRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    private val _friends = MutableStateFlow<List<Friend>?>(null)

    /** Last friend list loaded in this process (null until the first load). */
    val friends: StateFlow<List<Friend>?> = _friends.asStateFlow()

    private val _requests = MutableStateFlow(FriendRequests())
    val requests: StateFlow<FriendRequests> = _requests.asStateFlow()

    private val _remotePaused = MutableStateFlow(false)
    val remotePaused: StateFlow<Boolean> = _remotePaused.asStateFlow()

    suspend fun refreshFriends(): Outcome<List<Friend>> = outcomeOf {
        val list = RpcJson.decodeFromString<List<Friend>>(supabase.rpcBody("list_friends"))
        _friends.value = list
        list
    }

    suspend fun refreshRequests(): Outcome<FriendRequests> = outcomeOf {
        val value = RpcJson.decodeFromString<FriendRequests>(supabase.rpcBody("list_friend_requests"))
        _requests.value = value
        value
    }

    suspend fun lookup(username: String): Outcome<LookupReply> = outcomeOf {
        RpcJson.decodeFromString<LookupReply>(
            supabase.rpcBody("lookup_user_id", buildJsonObject { put("p_username", username) }),
        )
    }

    suspend fun sendRequest(username: String): Outcome<ChangeResult> =
        change("send_friend_request", buildJsonObject { put("p_username", username) })

    suspend fun respondRequest(requestId: String, accept: Boolean): Outcome<ChangeResult> =
        change("respond_friend_request", buildJsonObject {
            put("p_request_id", requestId)
            put("p_accept", accept)
        })

    suspend fun cancelRequest(requestId: String): Outcome<ChangeResult> =
        change("cancel_friend_request", buildJsonObject { put("p_request_id", requestId) })

    suspend fun removeFriend(userId: String): Outcome<ChangeResult> =
        change("remove_friend", buildJsonObject { put("p_user", userId) })

    suspend fun invite(userId: String, level: RelationshipLevel, label: String?): Outcome<ChangeResult> =
        change("send_relationship_invite", buildJsonObject {
            put("p_user", userId)
            put("p_level", level.wire)
            put("p_label", label?.trim()?.takeIf { it.isNotEmpty() })
        })

    suspend fun respondInvite(requestId: String, accept: Boolean): Outcome<ChangeResult> =
        change("respond_relationship_invite", buildJsonObject {
            put("p_request_id", requestId)
            put("p_accept", accept)
        })

    suspend fun cancelInvite(requestId: String): Outcome<ChangeResult> =
        change("cancel_relationship_invite", buildJsonObject { put("p_request_id", requestId) })

    /** Steps the relationship down, or renames the partner label when [level] is Partner. */
    suspend fun setLevel(userId: String, level: RelationshipLevel, label: String? = null): Outcome<ChangeResult> =
        change("set_relationship_level", buildJsonObject {
            put("p_user", userId)
            put("p_level", level.wire)
            put("p_label", label?.trim()?.takeIf { it.isNotEmpty() })
        })

    suspend fun block(userId: String): Outcome<ChangeResult> =
        change("block_user", buildJsonObject { put("p_user", userId) })

    suspend fun unblock(userId: String): Outcome<ChangeResult> =
        change("unblock_user", buildJsonObject { put("p_user", userId) })

    suspend fun blocked(): Outcome<List<PersonCard>> = outcomeOf {
        RpcJson.decodeFromString<List<PersonCard>>(supabase.rpcBody("list_blocked"))
    }

    suspend fun permissions(contactId: String): Outcome<PermissionsSnapshot?> = outcomeOf {
        val body = RpcJson.parseToJsonElement(
            supabase.rpcBody("get_contact_permissions", buildJsonObject { put("p_contact", contactId) }),
        ).jsonObject
        if (statusOf(body.toString()) != "ok") {
            null
        } else {
            PermissionsSnapshot(
                level = RelationshipLevel.fromWire(body["level"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }),
                permissions = RpcJson.decodeFromJsonElement<ContactPermissions>(body["permissions"]!!),
            )
        }
    }

    /** Applies only the keys in [changes]; refused as a whole if anything is invalid. */
    suspend fun updatePermissions(contactId: String, changes: JsonObject, expectedVersion: Int?): Outcome<PermissionUpdate> =
        outcomeOf {
            val body = RpcJson.parseToJsonElement(
                supabase.rpcBody(
                    "update_contact_permissions",
                    buildJsonObject {
                        put("p_contact", contactId)
                        put("p_changes", changes)
                        put("p_expected_version", expectedVersion)
                    },
                ),
            ).jsonObject
            val permissions = body["permissions"]?.let { RpcJson.decodeFromJsonElement<ContactPermissions>(it) }
            PermissionUpdate(ChangeResult.fromWire(statusOf(body.toString())), permissions)
        }

    /** Turns off every remote permission for every contact. */
    suspend fun revokeEverything(): Outcome<ChangeResult> = change("revoke_all_permissions", buildJsonObject { })

    suspend fun setRemotePause(paused: Boolean): Outcome<ChangeResult> {
        val result = change("set_remote_pause", buildJsonObject { put("p_paused", paused) })
        if (result is Outcome.Success && result.value == ChangeResult.Ok) _remotePaused.value = paused
        return result
    }

    suspend fun refreshRemotePause(): Outcome<Boolean> = outcomeOf {
        val client = supabase.client()
        val uid = client.auth.currentUserOrNull()?.id ?: error("signed out")
        val row = client.from("user_settings")
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull<UserSettingsRow>()
        val paused = row?.remotePaused ?: false
        _remotePaused.value = paused
        paused
    }

    fun clear() {
        _friends.value = null
        _requests.value = FriendRequests()
        _remotePaused.value = false
    }

    private suspend fun change(function: String, params: JsonObject): Outcome<ChangeResult> = outcomeOf {
        ChangeResult.fromWire(statusOf(supabase.rpcBody(function, params)))
    }
}
