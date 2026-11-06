package com.meyouww.core.data.activity

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.ActivityItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The heart inbox: requests, invitations and everything friends did for you. */
@Singleton
class ActivityRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    private val _items = MutableStateFlow<List<ActivityItem>?>(null)
    val items: StateFlow<List<ActivityItem>?> = _items.asStateFlow()

    private val _unread = MutableStateFlow(0)

    /** Badge count on the heart. */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun refresh(): Outcome<List<ActivityItem>> = outcomeOf {
        val list = RpcJson.decodeFromString<List<ActivityItem>>(supabase.rpcBody("list_activity"))
        _items.value = list
        _unread.value = list.count { !it.read }
        list
    }

    suspend fun get(id: String): Outcome<ActivityItem?> = outcomeOf {
        val body = supabase.rpcBody("get_activity", buildJsonObject { put("p_id", id) })
        if (body.isBlank() || body.trim() == "null") null else RpcJson.decodeFromString<ActivityItem>(body)
    }

    suspend fun refreshUnread(): Outcome<Int> = outcomeOf {
        val count = supabase.rpcBody("unread_activity_count").trim().toIntOrNull() ?: 0
        _unread.value = count
        count
    }

    suspend fun markAllRead(): Outcome<Unit> = outcomeOf {
        supabase.rpcBody("mark_activity_read", buildJsonObject { put("p_ids", JsonNull) })
        _unread.value = 0
        _items.value = _items.value?.map { it.copy(read = true) }
    }

    suspend fun markRead(id: String): Outcome<Unit> = outcomeOf {
        supabase.rpcBody("mark_activity_read", buildJsonObject { put("p_ids", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(id)) }) })
        _items.value = _items.value?.map { if (it.id == id) it.copy(read = true) else it }
        _unread.value = (_unread.value - 1).coerceAtLeast(0)
    }

    suspend fun dismiss(id: String): Outcome<Unit> = outcomeOf {
        supabase.rpcBody("dismiss_activity", buildJsonObject { put("p_id", id) })
        _items.value = _items.value?.filterNot { it.id == id }
    }

    /** Updates one item locally after it was answered somewhere else (a notification button). */
    fun markResolved(subjectId: String, resolution: String) {
        _items.value = _items.value?.map {
            if (it.subjectId == subjectId) it.copy(actionable = false, resolution = resolution, read = true) else it
        }
    }

    fun clear() {
        _items.value = null
        _unread.value = 0
    }
}
