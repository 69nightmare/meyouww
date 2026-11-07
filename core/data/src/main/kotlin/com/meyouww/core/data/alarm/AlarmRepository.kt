package com.meyouww.core.data.alarm

import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.AlarmSendOutcome
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Shared and personal alarms. Every rule (friendship, consent, limits) is checked by Postgres. */
@Singleton
class AlarmRepository @Inject constructor(
    private val supabase: SupabaseProvider,
) {
    /**
     * Asks [recipientId]'s phone to ring at [ringLocal], a wall-clock time in the
     * RECIPIENT's time zone. Use your own ID for an alarm on this phone.
     */
    suspend fun create(
        recipientId: String,
        title: String,
        message: String?,
        ringLocal: LocalDateTime,
        repeatDays: Int,
        snoozeMinutes: Int,
        vibrate: Boolean,
        dismiss: String,
    ): Outcome<AlarmSendOutcome> = outcomeOf {
        val body = supabase.rpcBody(
            "create_alarm",
            buildJsonObject {
                put("p_recipient", recipientId)
                put("p_title", title.trim())
                put("p_message", message?.trim()?.ifBlank { null })
                put("p_ring_local", ringLocal.withSecond(0).withNano(0).toString())
                put("p_repeat_days", repeatDays)
                put("p_snooze_minutes", snoozeMinutes)
                put("p_vibrate", vibrate)
                put("p_sound", "default")
                put("p_dismiss", dismiss)
            },
        )
        AlarmSendOutcome.fromWire(statusOf(body))
    }

    /** Accept or decline a request; declining an automatic alarm is its Undo. */
    suspend fun respond(alarmId: String, accept: Boolean): Outcome<String?> = outcomeOf {
        statusOf(
            supabase.rpcBody(
                "respond_alarm",
                buildJsonObject {
                    put("p_alarm", alarmId)
                    put("p_accept", accept)
                },
            ),
        )
    }

    /** The sender takes it back, or the recipient deletes it. */
    suspend fun cancel(alarmId: String): Outcome<Unit> = outcomeOf {
        supabase.rpcBody("cancel_alarm", buildJsonObject { put("p_alarm", alarmId) })
        Unit
    }

    /** Alarms that belong on this person's phone, and requests waiting for them. */
    suspend fun mine(): Outcome<List<AlarmInfo>> = outcomeOf {
        RpcJson.decodeFromString<List<AlarmInfo>>(supabase.rpcBody("list_my_alarms"))
    }

    suspend fun sent(): Outcome<List<AlarmInfo>> = outcomeOf {
        RpcJson.decodeFromString<List<AlarmInfo>>(supabase.rpcBody("list_sent_alarms"))
    }
}
