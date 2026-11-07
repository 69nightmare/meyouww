package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** One shared or personal alarm, as the server describes it. */
@Serializable
data class AlarmInfo(
    val id: String,
    @SerialName("from_me") val fromMe: Boolean = false,
    @SerialName("to_me") val toMe: Boolean = false,
    val sender: PersonCard,
    val recipient: PersonCard,
    val title: String,
    val message: String? = null,
    @SerialName("ring_at") val ringAt: String,
    @SerialName("local_time") val localTime: String,
    @SerialName("time_zone") val timeZone: String = "UTC",
    @SerialName("repeat_days") val repeatDays: Int = 0,
    @SerialName("snooze_minutes") val snoozeMinutes: Int = 5,
    val vibrate: Boolean = true,
    val sound: String = "default",
    val dismiss: String = "tap",
    val status: String,
    val auto: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isMine: Boolean get() = fromMe && toMe
    val accepted: Boolean get() = status == STATUS_ACCEPTED
    val pending: Boolean get() = status == STATUS_PENDING

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
    }
}

/** What happened when an alarm was sent. The sender never learns which rule said no. */
enum class AlarmSendOutcome(val wire: String) {
    /** Waiting for the friend's OK in their inbox. */
    Sent("sent"),

    /** On their phone already (auto-accept, or an alarm for myself). */
    Added("added"),
    Unavailable("unavailable"),
    Limit("limit"),
    NotFriends("not_friends"),
    InPast("in_past"),
    Invalid("invalid"),
    RateLimited("rate_limited"),
    Failed("");

    companion object {
        fun fromWire(value: String?): AlarmSendOutcome = entries.firstOrNull { it.wire == value && it != Failed } ?: Failed
    }
}

/**
 * Alarm times. Repeat days are a bit set: bit 0 = Monday … bit 6 = Sunday. Repeating
 * alarms ring at their wall-clock time in the phone's CURRENT zone, so they follow
 * daylight-saving changes and travel; a one-off alarm rings at its stored instant.
 */
object AlarmTimes {
    const val WEEKDAYS = 0b0011111
    const val EVERY_DAY = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun repeatsOn(repeatDays: Int, day: DayOfWeek): Boolean = repeatDays and bit(day) != 0

    /** The next time to ring after [now], or null when a one-off alarm is in the past. */
    fun nextRing(firstRing: Instant, localTime: LocalTime, repeatDays: Int, zone: ZoneId, now: Instant): Instant? {
        if (repeatDays and EVERY_DAY == 0) return firstRing.takeIf { it.isAfter(now) }
        // Never before the first ring the sender chose (minus a minute of clock slack).
        val after = maxOf(now, firstRing.minusSeconds(60))
        var date: LocalDate = after.atZone(zone).toLocalDate()
        repeat(8) {
            if (repeatsOn(repeatDays, date.dayOfWeek)) {
                // ZonedDateTime.of moves a time that falls in a DST gap forward, so it still rings.
                val candidate = ZonedDateTime.of(date, localTime, zone).toInstant()
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** "HH:MM" from the server. */
    fun parseLocalTime(text: String): LocalTime? = runCatching { LocalTime.parse(text) }.getOrNull()
}
