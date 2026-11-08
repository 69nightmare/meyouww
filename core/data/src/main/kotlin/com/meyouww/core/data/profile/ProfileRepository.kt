package com.meyouww.core.data.profile

import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.Profile
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

enum class UsernameClaim { Ok, Taken, Invalid, Reserved, TooSoon, RateLimited }

enum class AgeGateRecord { Ok, Underage, Invalid }

/** The signed-in person's own profile. Writes go through Postgres functions. */
@Singleton
class ProfileRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cached = MutableStateFlow<Profile?>(null)

    /** Last profile loaded in this process. */
    val profile: StateFlow<Profile?> = cached.asStateFlow()

    suspend fun refresh(): Outcome<Profile?> = outcomeOf {
        val client = supabase.client()
        val uid = client.auth.currentUserOrNull()?.id ?: error("signed out")
        val row = client.from("profiles")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Profile>()
        cached.value = row
        row
    }

    /** female, male, other, or null to clear. */
    suspend fun setGender(gender: String?): Outcome<Boolean> = outcomeOf {
        val result = supabase.client().postgrest.rpc("set_gender", buildJsonObject { put("p_gender", gender) })
        val ok = statusOf(result.data) == "ok"
        if (ok) cached.value = cached.value?.copy(gender = gender)
        ok
    }

    /** Records the age-gate answers on the server. The server re-checks them. */
    suspend fun recordAgeGate(birthYear: Int, confirmedAdult: Boolean): Outcome<AgeGateRecord> = outcomeOf {
        val result = supabase.client().postgrest.rpc(
            "complete_age_gate",
            buildJsonObject {
                put("p_birth_year", birthYear)
                put("p_confirmed_adult", confirmedAdult)
            },
        )
        when (statusOf(result.data)) {
            "ok" -> AgeGateRecord.Ok
            "underage" -> AgeGateRecord.Underage
            else -> AgeGateRecord.Invalid
        }
    }

    suspend fun claimUsername(username: String, displayName: String, nickname: String?): Outcome<UsernameClaim> =
        outcomeOf {
            val result = supabase.client().postgrest.rpc(
                "claim_username",
                buildJsonObject {
                    put("p_username", username)
                    put("p_display_name", displayName.trim())
                    put("p_nickname", nickname?.trim()?.takeIf { it.isNotEmpty() })
                },
            )
            when (statusOf(result.data)) {
                "ok" -> UsernameClaim.Ok
                "taken" -> UsernameClaim.Taken
                "reserved" -> UsernameClaim.Reserved
                "too_soon" -> UsernameClaim.TooSoon
                "rate_limited" -> UsernameClaim.RateLimited
                else -> UsernameClaim.Invalid
            }
        }

    fun clear() {
        cached.value = null
    }

    private fun statusOf(body: String): String =
        runCatching { json.parseToJsonElement(body).jsonObject["status"]?.jsonPrimitive?.content }
            .getOrNull() ?: "error"
}

/** Maps a failed profile call to something the UI can say. */
fun AppError.isRetryable(): Boolean = this == AppError.Network || this == AppError.Unknown

internal fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
