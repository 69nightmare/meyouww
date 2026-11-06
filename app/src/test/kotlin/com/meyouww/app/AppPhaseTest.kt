package com.meyouww.app

import com.google.common.truth.Truth.assertThat
import com.meyouww.core.model.Profile
import org.junit.Test

class AppPhaseTest {
    private val adult = Profile(id = "u", isAdult = true, birthYear = 1998)

    @Test
    fun `missing age gate goes to the age gate`() {
        assertThat(phaseFor(Profile(id = "u"), loadFailed = false)).isEqualTo(AppPhase.NeedsAgeGate)
    }

    @Test
    fun `adult without username goes to profile setup`() {
        assertThat(phaseFor(adult, loadFailed = false)).isEqualTo(AppPhase.NeedsProfile)
    }

    @Test
    fun `complete profile is ready`() {
        assertThat(phaseFor(adult.copy(username = "riya", displayName = "Riya"), loadFailed = false)).isEqualTo(AppPhase.Ready)
    }

    @Test
    fun `offline with no cached profile still opens the app`() {
        assertThat(phaseFor(null, loadFailed = true)).isEqualTo(AppPhase.Ready)
    }

    @Test
    fun `offline with a cached incomplete profile keeps onboarding`() {
        assertThat(phaseFor(adult, loadFailed = true)).isEqualTo(AppPhase.NeedsProfile)
    }
}
