package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What the server knows about notifications for this person and phone (`my_push_status`). */
@Serializable
data class PushStatus(
    @SerialName("device_registered") val deviceRegistered: Boolean = false,
    @SerialName("has_push_token") val hasPushToken: Boolean = false,
    @SerialName("token_updated_at") val tokenUpdatedAt: String? = null,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean? = null,
    @SerialName("dispatcher_configured") val dispatcherConfigured: Boolean = false,
    val dispatcher: List<DispatcherReply> = emptyList(),
    @SerialName("last_test") val lastTest: ServerTest? = null,
    val recent: List<PushAttempt> = emptyList(),
)

/** One reply the push sender gave the database (status code and our own error word only). */
@Serializable
data class DispatcherReply(
    val at: String? = null,
    val status: Int? = null,
    @SerialName("timed_out") val timedOut: Boolean = false,
    val error: String? = null,
)

@Serializable
data class ServerTest(
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("sent_at") val sentAt: String? = null,
    @SerialName("send_error") val sendError: String? = null,
    val arrived: Boolean = false,
)

/** One event the server tried to push to this person. */
@Serializable
data class PushAttempt(
    val at: String,
    val type: String,
    val channel: String = "",
    val devices: Int = 0,
    val tokens: Int = 0,
    val sent: Int = 0,
    val error: String? = null,
)

/** The single most useful explanation of why notifications might not arrive. */
enum class PushProblem {
    /** This phone has not registered with the server yet. */
    NotRegistered,

    /** Google Play services gave this phone no notification token. */
    NoTokenOnPhone,

    /** The phone has a token but the server does not: registration needs to run again. */
    TokenNotUploaded,

    /** The server's push sender is not connected (owner setup). */
    SenderNotSetUp,

    /** Firebase refused the server's credentials or project (owner setup). */
    FirebaseRejected,

    /** Firebase says the token belongs to a different app or project. */
    WrongProject,

    /** Firebase says the token is no longer valid; registering again fixes it. */
    TokenExpired,

    /** Everything on the server side worked: anything missing is on the phone. */
    PhoneSide,

    /** Nothing has been sent to this person yet, and no problems are known. */
    NothingYet,
}

object PushDiagnosis {
    /**
     * Picks the first problem in the order a person can act on it.
     * [phoneHasToken] is whether Firebase gave this phone a token just now.
     */
    fun diagnose(status: PushStatus, phoneHasToken: Boolean): PushProblem {
        if (!status.deviceRegistered) return PushProblem.NotRegistered
        if (!phoneHasToken) return PushProblem.NoTokenOnPhone
        if (!status.hasPushToken) return PushProblem.TokenNotUploaded
        if (!status.dispatcherConfigured) return PushProblem.SenderNotSetUp
        val lastReply = status.dispatcher.firstOrNull()
        if (lastReply != null && lastReply.error in setOf("unauthorized", "push_not_configured")) return PushProblem.SenderNotSetUp

        val errors = (status.recent.mapNotNull { it.error } + listOfNotNull(status.lastTest?.sendError)).joinToString(" ")
        return when {
            "SENDER_ID_MISMATCH" in errors -> PushProblem.WrongProject
            "UNREGISTERED" in errors || "INVALID_ARGUMENT" in errors -> PushProblem.TokenExpired
            "THIRD_PARTY_AUTH_ERROR" in errors || "UNAUTHENTICATED" in errors || "PERMISSION_DENIED" in errors ||
                "Google OAuth" in errors -> PushProblem.FirebaseRejected
            status.recent.isEmpty() && status.lastTest == null -> PushProblem.NothingYet
            else -> PushProblem.PhoneSide
        }
    }
}
