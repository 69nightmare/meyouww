package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Notification channel IDs. Versioned because Android freezes a channel's sound and importance. */
object ChannelIds {
    const val MESSAGES = "messages_v1"
    const val WALLPAPERS = "wallpapers_v1"
    const val WALLPAPER_REQUESTS = "wallpaper_requests_v1"
    const val ALARMS = "alarms_v1"
    const val REMINDERS = "reminders_v1"
    const val FRIENDS = "friends_v1"
    const val SECURITY = "security_v1"
    const val DELIVERY_STATUS = "delivery_status_v1"
    const val BACKGROUND_WORK = "background_work_v1"

    /** Channels that must stay on for closed-app delivery to be visible. */
    val essential = setOf(MESSAGES, WALLPAPERS, WALLPAPER_REQUESTS, REMINDERS, ALARMS, DELIVERY_STATUS)
}

/**
 * What the server needs to know to choose push priority and to show useful
 * diagnostics. Nothing else is collected: no location, contacts, screen
 * contents, battery level or hardware identifiers.
 */
@Serializable
data class DeviceStateReport(
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean,
    /** Channel ID to importance (0 = turned off by the user). */
    val channels: Map<String, Int>,
    @SerialName("exact_alarms_allowed") val exactAlarmsAllowed: Boolean,
    @SerialName("full_screen_allowed") val fullScreenAllowed: Boolean,
    @SerialName("background_restricted") val backgroundRestricted: Boolean,
    /** "off", "allowed" (meyouww exempted) or "on". */
    @SerialName("data_saver") val dataSaver: String,
    /** "ok", "update_required", "missing" or "error". */
    @SerialName("play_services") val playServices: String,
    /** UsageStatsManager bucket (10 active ... 45 restricted), or null below Android 9. */
    @SerialName("standby_bucket") val standbyBucket: Int? = null,
)

enum class DeliveryCheck { PushConfigured, Notifications, Battery, BackgroundData, PhoneMaker, PlayServices, ExactAlarms }

enum class CheckStatus { Ok, NeedsFix, NeedsConfirmation, NotApplicable }

data class DeliveryCheckResult(val check: DeliveryCheck, val status: CheckStatus)

/**
 * Decides which "work like WhatsApp" checklist items pass. Pure logic, unit
 * tested; the Android side only collects [DeviceStateReport].
 */
object DeliveryHealth {

    /** Phone makers whose battery managers can stop closed-app delivery until the user allows it. */
    private val aggressiveMakers = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo",
        "huawei", "honor", "tecno", "infinix", "itel", "samsung",
    )

    fun needsPhoneMakerStep(manufacturer: String): Boolean = manufacturer.trim().lowercase() in aggressiveMakers

    fun evaluate(
        state: DeviceStateReport,
        manufacturer: String,
        phoneMakerStepConfirmed: Boolean,
        pushConfigured: Boolean,
    ): List<DeliveryCheckResult> {
        val essentialChannelsOn = state.channels
            .filterKeys { it in ChannelIds.essential }
            .values
            .none { it == 0 }
        return listOf(
            DeliveryCheckResult(DeliveryCheck.PushConfigured, if (pushConfigured) CheckStatus.Ok else CheckStatus.NeedsFix),
            DeliveryCheckResult(
                DeliveryCheck.Notifications,
                if (state.notificationsEnabled && essentialChannelsOn) CheckStatus.Ok else CheckStatus.NeedsFix,
            ),
            DeliveryCheckResult(DeliveryCheck.Battery, if (state.backgroundRestricted) CheckStatus.NeedsFix else CheckStatus.Ok),
            DeliveryCheckResult(DeliveryCheck.BackgroundData, if (state.dataSaver == "on") CheckStatus.NeedsFix else CheckStatus.Ok),
            DeliveryCheckResult(
                DeliveryCheck.PhoneMaker,
                when {
                    !needsPhoneMakerStep(manufacturer) -> CheckStatus.NotApplicable
                    phoneMakerStepConfirmed -> CheckStatus.Ok
                    else -> CheckStatus.NeedsConfirmation
                },
            ),
            DeliveryCheckResult(DeliveryCheck.PlayServices, if (state.playServices == "ok") CheckStatus.Ok else CheckStatus.NeedsFix),
            DeliveryCheckResult(DeliveryCheck.ExactAlarms, if (state.exactAlarmsAllowed) CheckStatus.Ok else CheckStatus.NeedsFix),
        )
    }

    /**
     * Ready for closed-app delivery of pushes. Exact alarms are listed but do
     * not block delivery (they matter for alarms and reminders only).
     */
    fun isReady(results: List<DeliveryCheckResult>): Boolean = results
        .filter { it.check != DeliveryCheck.ExactAlarms }
        .all { it.status == CheckStatus.Ok || it.status == CheckStatus.NotApplicable }
}
