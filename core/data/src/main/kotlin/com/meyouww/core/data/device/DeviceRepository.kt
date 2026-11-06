package com.meyouww.core.data.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.model.DeviceStateReport
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies this device's FCM token. Implemented in :core:notifications. */
interface PushTokenProvider {
    /** The current FCM token, or null if push is unavailable on this build or device. */
    suspend fun currentToken(): String?

    /** Deletes the token so this device stops receiving pushes (logout). */
    suspend fun deleteToken()
}

@Serializable
private data class RegisterDeviceRequest(
    val action: String,
    @SerialName("installation_id") val installationId: String,
    @SerialName("push_token") val pushToken: String? = null,
    val platform: String = "android",
    val manufacturer: String? = null,
    @SerialName("os_sdk") val osSdk: Int? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val state: DeviceStateReport? = null,
)

/** A started closed-app delivery self-test. */
data class StartedDeliveryTest(val testId: String, val sendAtServerMs: Long)

/** Registers this phone and its push token, and reports delivery-relevant state. */
@Singleton
class DeviceRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val supabase: SupabaseProvider,
    private val prefs: AppPreferences,
    private val stateCollector: DeviceStateCollector,
) {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }

    suspend fun register(pushToken: String?): Outcome<Unit> = outcomeOf {
        val request = RegisterDeviceRequest(
            action = "register",
            installationId = prefs.installationId(),
            pushToken = pushToken,
            manufacturer = Build.MANUFACTURER,
            osSdk = Build.VERSION.SDK_INT,
            appVersion = appVersion(),
            state = stateCollector.collect(),
        )
        supabase.client().functions.invoke("register-device", body = json.encodeToJsonElement(request))
        Unit
    }

    /** Called at logout: the server deletes the push token and cancels pending work for this device. */
    suspend fun unregister(): Outcome<Unit> = outcomeOf {
        val request = RegisterDeviceRequest(action = "unregister", installationId = prefs.installationId())
        supabase.client().functions.invoke("register-device", body = json.encodeToJsonElement(request))
        Unit
    }

    suspend fun reportState(): Outcome<Unit> = outcomeOf {
        val request = RegisterDeviceRequest(
            action = "state",
            installationId = prefs.installationId(),
            state = stateCollector.collect(),
        )
        supabase.client().functions.invoke("update-device-state", body = json.encodeToJsonElement(request))
        Unit
    }

    /**
     * Asks the server to send a high-priority test push to this phone in about
     * 20 seconds, so the person can swipe the app away and lock the screen.
     */
    suspend fun startDeliveryTest(): Outcome<StartedDeliveryTest> = outcomeOf {
        val installationId = prefs.installationId()
        val before = System.currentTimeMillis()
        val response = supabase.client().functions.invoke(
            "test-push",
            body = json.encodeToJsonElement(mapOf("installation_id" to installationId)),
        )
        val after = System.currentTimeMillis()
        val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val testId = body["test_id"]!!.jsonPrimitive.content
        val sendAt = body["send_at_ms"]!!.jsonPrimitive.long
        val serverNow = body["server_now_ms"]!!.jsonPrimitive.long
        // Estimate server-minus-device clock offset at the midpoint of the request.
        val offset = serverNow - (before + after) / 2
        prefs.startDeliveryTest(testId, before, offset)
        StartedDeliveryTest(testId, sendAt)
    }

    /** Tells the server when the test push arrived and how long it took. */
    suspend fun reportDeliveryArrival(testId: String, latencyMs: Long): Outcome<Unit> = outcomeOf {
        supabase.client().postgrest.rpc(
            "report_delivery_test",
            buildJsonObject {
                put("p_test_id", testId)
                put("p_latency_ms", latencyMs)
            },
        )
        Unit
    }

    private fun appVersion(): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    }.getOrNull()
}
