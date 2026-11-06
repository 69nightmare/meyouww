package com.meyouww.core.data.common

import com.meyouww.core.data.di.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared JSON settings for reading function replies. */
internal val RpcJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Calls a Postgres function and returns the raw JSON body. */
internal suspend fun SupabaseProvider.rpcBody(function: String, params: JsonObject = buildJsonObject { }): String =
    client().postgrest.rpc(function, params).data

/** Reads `status` from a `{ "status": ... }` reply. */
internal fun statusOf(body: String): String? =
    runCatching { RpcJson.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.content }.getOrNull()
