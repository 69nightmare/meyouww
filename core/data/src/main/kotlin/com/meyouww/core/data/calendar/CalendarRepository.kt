package com.meyouww.core.data.calendar

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.EventCreated
import com.meyouww.core.model.EventInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The shared calendar. Postgres decides who may invite whom and who sees what. */
@Singleton
class CalendarRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    private val cached = MutableStateFlow<List<EventInfo>?>(null)

    /** The last window loaded (roughly this month and the next two). */
    val events: StateFlow<List<EventInfo>?> = cached.asStateFlow()

    suspend fun list(from: LocalDate, to: LocalDate): Outcome<List<EventInfo>> = outcomeOf {
        val body = supabase.rpcBody(
            "list_events",
            buildJsonObject {
                put("p_from", from.toString())
                put("p_to", to.toString())
            },
        )
        RpcJson.decodeFromString<List<EventInfo>>(body)
    }

    /** Loads [from]..[to] into [events]. */
    suspend fun refresh(from: LocalDate, to: LocalDate): Outcome<List<EventInfo>> =
        list(from, to).also { if (it is Outcome.Success) cached.value = it.value }

    /** [start] is a wall-clock time on this phone; for all-day events only the date counts. */
    suspend fun create(
        title: String,
        start: LocalDateTime,
        allDay: Boolean,
        note: String?,
        emoji: String,
        remindMinutes: Int?,
        invitees: List<String>,
    ): Outcome<EventCreated> = outcomeOf {
        val body = supabase.rpcBody(
            "create_event",
            buildJsonObject {
                put("p_title", title.trim())
                put("p_start_local", start.withSecond(0).withNano(0).toString())
                put("p_all_day", allDay)
                put("p_note", note?.trim()?.ifBlank { null })
                put("p_emoji", emoji)
                put("p_remind_minutes", remindMinutes)
                put("p_invitees", JsonArray(invitees.map { JsonPrimitive(it) }))
            },
        )
        val reply = RpcJson.parseToJsonElement(body).jsonObject
        EventCreated(
            id = reply["event_id"]?.jsonPrimitive?.content,
            invited = reply["invited"]?.jsonPrimitive?.int ?: 0,
            skipped = reply["skipped"]?.jsonPrimitive?.int ?: 0,
            ok = statusOf(body) == "ok",
        )
    }

    /** Going (true) or not (false); also how an invitee leaves later. */
    suspend fun respond(eventId: String, going: Boolean): Outcome<Boolean> = outcomeOf {
        val ok = statusOf(
            supabase.rpcBody(
                "respond_event",
                buildJsonObject {
                    put("p_event", eventId)
                    put("p_going", going)
                },
            ),
        ) == "ok"
        if (ok) {
            cached.value = cached.value?.map { if (it.id == eventId) it.copy(myStatus = if (going) "going" else "declined") else it }
        }
        ok
    }

    suspend fun cancel(eventId: String): Outcome<Boolean> = outcomeOf {
        val ok = statusOf(supabase.rpcBody("cancel_event", buildJsonObject { put("p_event", eventId) })) == "ok"
        if (ok) cached.value = cached.value?.filterNot { it.id == eventId }
        ok
    }

    fun clear() {
        cached.value = null
    }
}
