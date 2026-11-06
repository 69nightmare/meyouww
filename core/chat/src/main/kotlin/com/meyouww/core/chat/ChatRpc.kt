package com.meyouww.core.chat

import com.meyouww.core.data.di.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal val ChatJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Calls a Postgres function and parses its JSON reply. */
internal suspend fun SupabaseProvider.call(function: String, params: JsonObject): JsonElement =
    ChatJson.parseToJsonElement(client().postgrest.rpc(function, params).data)
