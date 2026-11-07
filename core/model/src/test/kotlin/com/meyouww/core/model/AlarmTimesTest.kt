package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmTimesTest {

    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(text: String, zone: ZoneId): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    @Test
    fun `a one-off alarm rings once, at its instant`() {
        val ring = at("2026-11-08T07:00", kolkata)
        assertThat(AlarmTimes.nextRing(ring, LocalTime.of(7, 0), 0, kolkata, at("2026-11-07T22:00", kolkata))).isEqualTo(ring)
        assertThat(AlarmTimes.nextRing(ring, LocalTime.of(7, 0), 0, kolkata, at("2026-11-08T07:01", kolkata))).isNull()
    }

    @Test
    fun `weekday alarms skip the weekend`() {
        // 2026-11-06 is a Friday.
        val first = at("2026-11-06T07:00", kolkata)
        val afterFriday = at("2026-11-06T08:00", kolkata)
        val next = AlarmTimes.nextRing(first, LocalTime.of(7, 0), AlarmTimes.WEEKDAYS, kolkata, afterFriday)
        assertThat(next).isEqualTo(at("2026-11-09T07:00", kolkata)) // Monday
        assertThat(next!!.atZone(kolkata).dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
    }

    @Test
    fun `repeating alarms never start before the first ring chosen`() {
        val first = at("2026-11-10T07:00", kolkata) // a Tuesday
        val next = AlarmTimes.nextRing(first, LocalTime.of(7, 0), AlarmTimes.EVERY_DAY, kolkata, at("2026-11-07T09:00", kolkata))
        assertThat(next).isEqualTo(first)
    }

    @Test
    fun `seven in the morning stays seven across a daylight-saving change`() {
        // New York leaves daylight saving on 2026-11-01.
        val first = at("2026-10-30T07:00", newYork)
        val next = AlarmTimes.nextRing(first, LocalTime.of(7, 0), AlarmTimes.EVERY_DAY, newYork, at("2026-11-01T01:00", newYork))
        assertThat(next!!.atZone(newYork).toLocalTime()).isEqualTo(LocalTime.of(7, 0))
        assertThat(next.atZone(newYork).toLocalDate().toString()).isEqualTo("2026-11-01")
    }

    @Test
    fun `days map to bits from Monday`() {
        assertThat(AlarmTimes.bit(DayOfWeek.MONDAY)).isEqualTo(1)
        assertThat(AlarmTimes.bit(DayOfWeek.SUNDAY)).isEqualTo(64)
        assertThat(AlarmTimes.repeatsOn(AlarmTimes.WEEKDAYS, DayOfWeek.SATURDAY)).isFalse()
    }

    @Test
    fun `send outcomes parse, unknown ones fail safe`() {
        assertThat(AlarmSendOutcome.fromWire("added")).isEqualTo(AlarmSendOutcome.Added)
        assertThat(AlarmSendOutcome.fromWire("weird")).isEqualTo(AlarmSendOutcome.Failed)
    }
}
