package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeliveryHealthTest {

    private val healthy = DeviceStateReport(
        notificationsEnabled = true,
        channels = ChannelIds.essential.associateWith { 4 } + (ChannelIds.BACKGROUND_WORK to 2),
        exactAlarmsAllowed = true,
        fullScreenAllowed = true,
        backgroundRestricted = false,
        dataSaver = "off",
        playServices = "ok",
        standbyBucket = 10,
    )

    private fun status(results: List<DeliveryCheckResult>, check: DeliveryCheck) =
        results.first { it.check == check }.status

    @Test
    fun `stock phone with everything allowed is ready`() {
        val r = DeliveryHealth.evaluate(healthy, "Google", phoneMakerStepConfirmed = false, pushConfigured = true)
        assertThat(status(r, DeliveryCheck.PhoneMaker)).isEqualTo(CheckStatus.NotApplicable)
        assertThat(DeliveryHealth.isReady(r)).isTrue()
    }

    @Test
    fun `xiaomi needs the autostart confirmation`() {
        val r = DeliveryHealth.evaluate(healthy, "Xiaomi", false, true)
        assertThat(status(r, DeliveryCheck.PhoneMaker)).isEqualTo(CheckStatus.NeedsConfirmation)
        assertThat(DeliveryHealth.isReady(r)).isFalse()
        val confirmed = DeliveryHealth.evaluate(healthy, " xiaomi ", true, true)
        assertThat(DeliveryHealth.isReady(confirmed)).isTrue()
    }

    @Test
    fun `notifications off or an essential channel blocked needs a fix`() {
        val off = DeliveryHealth.evaluate(healthy.copy(notificationsEnabled = false), "Google", false, true)
        assertThat(status(off, DeliveryCheck.Notifications)).isEqualTo(CheckStatus.NeedsFix)
        val channelOff = healthy.copy(channels = healthy.channels + (ChannelIds.MESSAGES to 0))
        assertThat(status(DeliveryHealth.evaluate(channelOff, "Google", false, true), DeliveryCheck.Notifications))
            .isEqualTo(CheckStatus.NeedsFix)
        // A non-essential channel turned off does not block delivery.
        val bgOff = healthy.copy(channels = healthy.channels + (ChannelIds.BACKGROUND_WORK to 0))
        assertThat(DeliveryHealth.isReady(DeliveryHealth.evaluate(bgOff, "Google", false, true))).isTrue()
    }

    @Test
    fun `restricted battery, data saver and missing play services each need a fix`() {
        val cases = listOf(
            healthy.copy(backgroundRestricted = true) to DeliveryCheck.Battery,
            healthy.copy(dataSaver = "on") to DeliveryCheck.BackgroundData,
            healthy.copy(playServices = "missing") to DeliveryCheck.PlayServices,
        )
        for ((state, check) in cases) {
            val r = DeliveryHealth.evaluate(state, "Google", false, true)
            assertThat(status(r, check)).isEqualTo(CheckStatus.NeedsFix)
            assertThat(DeliveryHealth.isReady(r)).isFalse()
        }
        // Data Saver with meyouww exempted is fine.
        assertThat(DeliveryHealth.isReady(DeliveryHealth.evaluate(healthy.copy(dataSaver = "allowed"), "Google", false, true))).isTrue()
    }

    @Test
    fun `exact alarms are reported but do not block push delivery`() {
        val r = DeliveryHealth.evaluate(healthy.copy(exactAlarmsAllowed = false), "Google", false, true)
        assertThat(status(r, DeliveryCheck.ExactAlarms)).isEqualTo(CheckStatus.NeedsFix)
        assertThat(DeliveryHealth.isReady(r)).isTrue()
    }

    @Test
    fun `a build without push settings is never ready`() {
        val r = DeliveryHealth.evaluate(healthy, "Google", false, pushConfigured = false)
        assertThat(DeliveryHealth.isReady(r)).isFalse()
    }
}
