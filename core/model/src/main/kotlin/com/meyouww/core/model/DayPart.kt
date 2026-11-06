package com.meyouww.core.model

/** Time-of-day bucket that drives greetings and decorations. */
enum class DayPart {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    companion object {
        fun ofHour(hour: Int): DayPart {
            require(hour in 0..23) { "hour must be 0..23, was $hour" }
            return when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..20 -> EVENING
                else -> NIGHT
            }
        }
    }
}
