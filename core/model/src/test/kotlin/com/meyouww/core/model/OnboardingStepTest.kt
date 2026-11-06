package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OnboardingStepTest {
    private val adult = Profile(id = "u1", isAdult = true, birthYear = 1999)

    @Test
    fun `signed out starts at welcome`() {
        assertThat(OnboardingStep.from(signedIn = false, profile = adult)).isEqualTo(OnboardingStep.WELCOME)
    }

    @Test
    fun `signed in without age gate goes to age gate`() {
        assertThat(OnboardingStep.from(true, null)).isEqualTo(OnboardingStep.AGE_GATE)
        assertThat(OnboardingStep.from(true, Profile(id = "u1"))).isEqualTo(OnboardingStep.AGE_GATE)
    }

    @Test
    fun `adult without username goes to profile setup`() {
        assertThat(OnboardingStep.from(true, adult)).isEqualTo(OnboardingStep.PROFILE)
        assertThat(OnboardingStep.from(true, adult.copy(username = "riya"))).isEqualTo(OnboardingStep.PROFILE)
    }

    @Test
    fun `complete profile is done`() {
        val done = adult.copy(username = "riya", displayName = "Riya")
        assertThat(OnboardingStep.from(true, done)).isEqualTo(OnboardingStep.DONE)
        assertThat(done.greetingName).isEqualTo("Riya")
        assertThat(done.copy(nickname = "Riyu").greetingName).isEqualTo("Riyu")
    }

    @Test
    fun `day parts cover every hour`() {
        assertThat(DayPart.ofHour(4)).isEqualTo(DayPart.NIGHT)
        assertThat(DayPart.ofHour(5)).isEqualTo(DayPart.MORNING)
        assertThat(DayPart.ofHour(12)).isEqualTo(DayPart.AFTERNOON)
        assertThat(DayPart.ofHour(17)).isEqualTo(DayPart.EVENING)
        assertThat(DayPart.ofHour(21)).isEqualTo(DayPart.NIGHT)
        assertThat((0..23).map(DayPart::ofHour).toSet()).hasSize(4)
    }
}
