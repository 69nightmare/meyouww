package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class PushDiagnosisTest {

    private val healthy = PushStatus(deviceRegistered = true, hasPushToken = true, dispatcherConfigured = true)

    private fun attempt(error: String? = null, sent: Int = if (error == null) 1 else 0) =
        PushAttempt(at = "2026-11-07T00:00:00Z", type = "activity", devices = 1, tokens = 1, sent = sent, error = error)

    @Test
    fun `problems are reported in the order a person can fix them`() {
        assertThat(PushDiagnosis.diagnose(PushStatus(), phoneHasToken = true)).isEqualTo(PushProblem.NotRegistered)
        assertThat(PushDiagnosis.diagnose(healthy, phoneHasToken = false)).isEqualTo(PushProblem.NoTokenOnPhone)
        assertThat(PushDiagnosis.diagnose(healthy.copy(hasPushToken = false), phoneHasToken = true))
            .isEqualTo(PushProblem.TokenNotUploaded)
        assertThat(PushDiagnosis.diagnose(healthy.copy(dispatcherConfigured = false), phoneHasToken = true))
            .isEqualTo(PushProblem.SenderNotSetUp)
    }

    @Test
    fun `firebase error codes map to their causes`() {
        fun with(error: String) = PushDiagnosis.diagnose(healthy.copy(recent = listOf(attempt(error))), phoneHasToken = true)
        assertThat(with("403 SENDER_ID_MISMATCH")).isEqualTo(PushProblem.WrongProject)
        assertThat(with("404 UNREGISTERED")).isEqualTo(PushProblem.TokenExpired)
        assertThat(with("401 THIRD_PARTY_AUTH_ERROR")).isEqualTo(PushProblem.FirebaseRejected)
        assertThat(with("401 UNAUTHENTICATED")).isEqualTo(PushProblem.FirebaseRejected)
    }

    @Test
    fun `a sender that refuses the database is an owner setup problem`() {
        val status = healthy.copy(dispatcher = listOf(DispatcherReply(status = 401, error = "unauthorized")))
        assertThat(PushDiagnosis.diagnose(status, phoneHasToken = true)).isEqualTo(PushProblem.SenderNotSetUp)
    }

    @Test
    fun `when the server sent fine, the phone is the place to look`() {
        assertThat(PushDiagnosis.diagnose(healthy.copy(recent = listOf(attempt())), phoneHasToken = true))
            .isEqualTo(PushProblem.PhoneSide)
        assertThat(PushDiagnosis.diagnose(healthy, phoneHasToken = true)).isEqualTo(PushProblem.NothingYet)
    }

    @Test
    fun `the server reply parses`() {
        val json = Json { ignoreUnknownKeys = true }
        val status = json.decodeFromString(
            PushStatus.serializer(),
            """{"device_registered":true,"has_push_token":true,"token_updated_at":"2026-11-07T00:00:00+00:00",
               "notifications_enabled":null,"dispatcher_configured":true,
               "dispatcher":[{"at":"2026-11-07T00:00:00+00:00","status":200,"timed_out":false,"error":null}],
               "last_test":null,
               "recent":[{"at":"2026-11-07T00:00:00+00:00","type":"activity","channel":"friends_v1","devices":1,"tokens":1,"sent":1,"error":null}]}""",
        )
        assertThat(status.recent.single().sent).isEqualTo(1)
        assertThat(status.dispatcher.single().status).isEqualTo(200)
    }
}
