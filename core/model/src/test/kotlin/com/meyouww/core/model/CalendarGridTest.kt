package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarGridTest {

    @Test
    fun `November 2026 starts on a Sunday`() {
        val mondayFirst = CalendarGrid.cells(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertThat(mondayFirst).hasSize(42)
        assertThat(mondayFirst.indexOf(LocalDate.of(2026, 11, 1))).isEqualTo(6)
        val sundayFirst = CalendarGrid.cells(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertThat(sundayFirst.first()).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(sundayFirst.filterNotNull()).hasSize(30)
    }

    @Test
    fun `weekday header follows the first day of the week`() {
        assertThat(CalendarGrid.weekdays(DayOfWeek.SUNDAY).first()).isEqualTo(DayOfWeek.SUNDAY)
        assertThat(CalendarGrid.weekdays(DayOfWeek.SUNDAY).last()).isEqualTo(DayOfWeek.SATURDAY)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private fun event(id: String, start: String, allDay: Boolean, localDate: String, status: String = "going") =
        json.decodeFromString(
            EventInfo.serializer(),
            """{"id":"$id","owner":{"id":"o","display_name":"Riya","avatar":"o/a.jpg"},"title":"T","starts_at":"$start",
               "all_day":$allDay,"local_date":"$localDate","my_status":"$status",
               "participants":[{"person":{"id":"s","display_name":"Sam"},"status":"going"},
                               {"person":{"id":"t","display_name":"Tara"},"status":"invited"}]}""",
        )

    @Test
    fun `timed events move with the clock, all-day events keep their date`() {
        val call = event("1", "2026-12-05T18:30:00+00:00", allDay = false, localDate = "2026-12-06")
        assertThat(call.dateIn(ZoneId.of("Asia/Tokyo"))).isEqualTo(LocalDate.of(2026, 12, 6))
        assertThat(call.dateIn(ZoneId.of("America/New_York"))).isEqualTo(LocalDate.of(2026, 12, 5))
        val birthday = event("2", "2026-12-09T18:30:00+00:00", allDay = true, localDate = "2026-12-10")
        assertThat(birthday.dateIn(ZoneId.of("America/New_York"))).isEqualTo(LocalDate.of(2026, 12, 10))
    }

    @Test
    fun `the faces under a date are the owner and who is going`() {
        val e = event("1", "2026-12-05T18:30:00+00:00", allDay = false, localDate = "2026-12-06")
        assertThat(e.people.map { it.name }).containsExactly("Riya", "Sam").inOrder()
        assertThat(e.owner.avatar).isEqualTo("o/a.jpg")
    }

    @Test
    fun `declined events don't show`() {
        val zone = ZoneId.of("UTC")
        val byDate = CalendarGrid.byDate(
            listOf(
                event("1", "2026-12-05T10:00:00+00:00", false, "2026-12-05"),
                event("2", "2026-12-05T11:00:00+00:00", false, "2026-12-05", status = "declined"),
            ),
            zone,
        )
        assertThat(byDate[LocalDate.of(2026, 12, 5)]!!.map { it.id }).containsExactly("1")
    }
}
