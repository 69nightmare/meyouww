package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarExtrasTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")

    @Test
    fun `holidays come from the government lists`() {
        assertThat(IndianHolidays.on(LocalDate.of(2026, 11, 24)).map { it.name }).containsExactly("Guru Nanak Jayanti")
        assertThat(IndianHolidays.on(LocalDate.of(2027, 10, 29)).single().name).isEqualTo("Diwali")
        assertThat(IndianHolidays.on(LocalDate.of(2027, 3, 23)).single().gazetted).isTrue()
        assertThat(IndianHolidays.on(LocalDate.of(2027, 8, 17)).single().gazetted).isFalse() // Raksha Bandhan
        assertThat(IndianHolidays.on(LocalDate.of(2027, 11, 10))).isEmpty()
    }

    @Test
    fun `a day with two holidays lists the public one first`() {
        val both = IndianHolidays.on(LocalDate.of(2027, 8, 15))
        assertThat(both.map { it.name }).containsExactly("Independence Day", "Eid-e-Milad", "Parsi New Year (Navroz)").inOrder()
    }

    @Test
    fun `every holiday falls on the weekday the government list gives`() {
        assertThat(LocalDate.of(2027, 1, 26).dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(LocalDate.of(2027, 10, 29).dayOfWeek).isEqualTo(DayOfWeek.FRIDAY)
        assertThat(LocalDate.of(2027, 8, 17).dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(LocalDate.of(2026, 11, 24).dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(IndianHolidays.between(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)).map { it.name })
            .containsExactly("Christmas Eve", "Christmas").inOrder()
    }

    @Test
    fun `a one-off alarm marks only its own day`() {
        val ring = LocalDateTime.parse("2026-11-12T07:00").atZone(kolkata).toInstant()
        assertThat(AlarmTimes.ringsOn(ring, 0, LocalDate.of(2026, 11, 12), kolkata)).isTrue()
        assertThat(AlarmTimes.ringsOn(ring, 0, LocalDate.of(2026, 11, 13), kolkata)).isFalse()
    }

    @Test
    fun `a repeating alarm marks its weekdays from the first ring on`() {
        // First ring Thursday 12 Nov 2026, repeating Monday to Friday.
        val ring = LocalDateTime.parse("2026-11-12T07:00").atZone(kolkata).toInstant()
        assertThat(AlarmTimes.ringsOn(ring, AlarmTimes.WEEKDAYS, LocalDate.of(2026, 11, 11), kolkata)).isFalse() // before it starts
        assertThat(AlarmTimes.ringsOn(ring, AlarmTimes.WEEKDAYS, LocalDate.of(2026, 11, 13), kolkata)).isTrue() // Friday
        assertThat(AlarmTimes.ringsOn(ring, AlarmTimes.WEEKDAYS, LocalDate.of(2026, 11, 14), kolkata)).isFalse() // Saturday
        assertThat(AlarmTimes.ringsOn(ring, AlarmTimes.WEEKDAYS, LocalDate.of(2026, 11, 16), kolkata)).isTrue() // Monday
    }

    @Test
    fun `reminders come 10 hours, 2 hours and 1 hour before, and on time`() {
        assertThat(ReminderPlan.leads(allDay = false, remindMinutes = null)).containsExactly(600, 120, 60, 0).inOrder()
        // The person's own choice is kept, without doubling one that is already there.
        assertThat(ReminderPlan.leads(allDay = false, remindMinutes = 15)).containsExactly(600, 120, 60, 15, 0).inOrder()
        assertThat(ReminderPlan.leads(allDay = false, remindMinutes = 60)).containsExactly(600, 120, 60, 0).inOrder()
        // All-day: the evening before and the morning of.
        assertThat(ReminderPlan.leads(allDay = true, remindMinutes = null)).containsExactly(360, -480).inOrder()
    }
}
