package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A marked day on the shared calendar. */
@Serializable
data class EventInfo(
    val id: String,
    val mine: Boolean = false,
    val owner: PersonCard,
    val title: String,
    val note: String? = null,
    val emoji: String = "📅",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("local_date") val localDate: String,
    @SerialName("local_time") val localTime: String? = null,
    @SerialName("time_zone") val timeZone: String = "UTC",
    @SerialName("remind_minutes") val remindMinutes: Int? = null,
    val cancelled: Boolean = false,
    /** going, invited or declined (owners are always going). */
    @SerialName("my_status") val myStatus: String? = null,
    val participants: List<EventParticipant> = emptyList(),
) {
    val invited: Boolean get() = myStatus == STATUS_INVITED
    val going: Boolean get() = myStatus == STATUS_GOING

    val start: Instant? get() = parseInstant(startsAt)

    /**
     * The day it shows on in [zone]. All-day events keep their own date wherever you are;
     * timed events move with the clock (a 23:30 call in Delhi is the next morning in Tokyo).
     */
    fun dateIn(zone: ZoneId): LocalDate? =
        if (allDay) runCatching { LocalDate.parse(localDate) }.getOrNull() else start?.atZone(zone)?.toLocalDate()

    /** Everyone who marked or joined it, owner first: the little faces under the date. */
    val people: List<PersonCard>
        get() = listOf(owner) + participants.filter { it.status == STATUS_GOING }.map { it.person }.filter { it.id != owner.id }

    companion object {
        const val STATUS_GOING = "going"
        const val STATUS_INVITED = "invited"
    }
}

@Serializable
data class EventParticipant(
    val person: PersonCard,
    val status: String,
)

/** What came back from marking a day. */
data class EventCreated(val id: String?, val invited: Int, val skipped: Int, val ok: Boolean)

/** The month grid: always 6 rows of 7, with null for days outside the month. */
object CalendarGrid {
    fun cells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
        val first = month.atDay(1)
        val lead = Math.floorMod(first.dayOfWeek.value - firstDayOfWeek.value, 7)
        return List(42) { index ->
            val day = index - lead + 1
            if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
        }
    }

    /** The weekday order for the header, starting at [firstDayOfWeek]. */
    fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> = List(7) { firstDayOfWeek.plus(it.toLong()) }

    /** Events grouped by the day they show on in [zone]; cancelled and declined ones are left out. */
    fun byDate(events: List<EventInfo>, zone: ZoneId): Map<LocalDate, List<EventInfo>> =
        events.filter { !it.cancelled && it.myStatus != "declined" }
            .mapNotNull { e -> e.dateIn(zone)?.let { it to e } }
            .groupBy({ it.first }, { it.second })
}
