package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgeGateTest {
    private val year = 2026

    @Test
    fun `adult who confirmed is eligible`() {
        assertThat(AgeGate.evaluate(1999, confirmedAdult = true, currentYear = year)).isEqualTo(AgeGate.Result.Eligible)
    }

    @Test
    fun `boundary year needs the explicit confirmation`() {
        val boundary = AgeGate.latestEligibleBirthYear(year)
        assertThat(boundary).isEqualTo(2008)
        assertThat(AgeGate.evaluate(boundary, confirmedAdult = true, currentYear = year)).isEqualTo(AgeGate.Result.Eligible)
        assertThat(AgeGate.evaluate(boundary, confirmedAdult = false, currentYear = year)).isEqualTo(AgeGate.Result.NotConfirmed)
    }

    @Test
    fun `under 18 is rejected even when confirmed`() {
        assertThat(AgeGate.evaluate(2009, confirmedAdult = true, currentYear = year)).isEqualTo(AgeGate.Result.Underage)
        assertThat(AgeGate.evaluate(2015, confirmedAdult = true, currentYear = year)).isEqualTo(AgeGate.Result.Underage)
    }

    @Test
    fun `missing or impossible years are invalid`() {
        assertThat(AgeGate.evaluate(null, true, year)).isEqualTo(AgeGate.Result.InvalidYear)
        assertThat(AgeGate.evaluate(2027, true, year)).isEqualTo(AgeGate.Result.InvalidYear)
        assertThat(AgeGate.evaluate(1890, true, year)).isEqualTo(AgeGate.Result.InvalidYear)
    }
}
