package com.meyouww.core.model

import java.time.LocalDate

/** A public holiday or festival shown on the calendar. */
data class Holiday(
    val date: LocalDate,
    val name: String,
    /** A gazetted (everyone's day off) holiday; otherwise a festival many people keep. */
    val gazetted: Boolean,
)

/**
 * India's holidays, from the central government's own lists (DoPT, F.No. 12/2/2023-JCA):
 * the gazetted holidays for 2026 (order of 3 July 2025) and the gazetted and restricted
 * holidays for 2027 (order of 16 July 2026, Delhi / New Delhi offices).
 *
 * Add a year by copying that year's list here when the government publishes it (each July).
 * Days that follow the moon (the Eids, Muharram) can move by a day when it is sighted.
 */
object IndianHolidays {
    private fun day(year: Int, month: Int, day: Int, name: String, gazetted: Boolean = true) =
        Holiday(LocalDate.of(year, month, day), name, gazetted)

    private val all: List<Holiday> = listOf(
        // 2026, gazetted
        day(2026, 1, 26, "Republic Day"),
        day(2026, 3, 4, "Holi"),
        day(2026, 3, 21, "Eid al-Fitr"),
        day(2026, 3, 26, "Ram Navami"),
        day(2026, 3, 31, "Mahavir Jayanti"),
        day(2026, 4, 3, "Good Friday"),
        day(2026, 5, 1, "Buddha Purnima"),
        day(2026, 5, 27, "Eid al-Adha (Bakrid)"),
        day(2026, 6, 26, "Muharram"),
        day(2026, 8, 15, "Independence Day"),
        day(2026, 8, 26, "Eid-e-Milad"),
        day(2026, 9, 4, "Janmashtami"),
        day(2026, 10, 2, "Gandhi Jayanti"),
        day(2026, 10, 20, "Dussehra"),
        day(2026, 11, 8, "Diwali"),
        day(2026, 11, 24, "Guru Nanak Jayanti"),
        day(2026, 12, 25, "Christmas"),
        // 2026, the same date every year
        day(2026, 12, 24, "Christmas Eve", gazetted = false),

        // 2027, gazetted
        day(2027, 1, 26, "Republic Day"),
        day(2027, 3, 10, "Eid al-Fitr"),
        day(2027, 3, 23, "Holi"),
        day(2027, 3, 26, "Good Friday"),
        day(2027, 4, 15, "Ram Navami"),
        day(2027, 4, 19, "Mahavir Jayanti"),
        day(2027, 5, 17, "Eid al-Adha (Bakrid)"),
        day(2027, 5, 20, "Buddha Purnima"),
        day(2027, 6, 16, "Muharram"),
        day(2027, 8, 15, "Independence Day"),
        day(2027, 8, 15, "Eid-e-Milad"),
        day(2027, 8, 25, "Janmashtami"),
        day(2027, 10, 2, "Gandhi Jayanti"),
        day(2027, 10, 9, "Dussehra"),
        day(2027, 10, 29, "Diwali"),
        day(2027, 11, 14, "Guru Nanak Jayanti"),
        day(2027, 12, 25, "Christmas"),
        // 2027, restricted (festivals)
        day(2027, 1, 1, "New Year's Day", gazetted = false),
        day(2027, 1, 14, "Makar Sankranti", gazetted = false),
        day(2027, 1, 15, "Pongal, Guru Gobind Singh Jayanti", gazetted = false),
        day(2027, 2, 11, "Basant Panchami", gazetted = false),
        day(2027, 2, 19, "Shivaji Jayanti", gazetted = false),
        day(2027, 2, 20, "Guru Ravidas Jayanti", gazetted = false),
        day(2027, 3, 2, "Swami Dayananda Saraswati Jayanti", gazetted = false),
        day(2027, 3, 5, "Jamat-ul-Vida", gazetted = false),
        day(2027, 3, 6, "Maha Shivratri", gazetted = false),
        day(2027, 3, 22, "Holika Dahan", gazetted = false),
        day(2027, 3, 28, "Easter Sunday", gazetted = false),
        day(2027, 4, 7, "Gudi Padwa, Ugadi, first Navratri", gazetted = false),
        day(2027, 4, 14, "Vaisakhi, Vishu, Tamil New Year", gazetted = false),
        day(2027, 4, 15, "Bengali New Year, Bohag Bihu", gazetted = false),
        day(2027, 5, 9, "Rabindranath Tagore Jayanti", gazetted = false),
        day(2027, 7, 5, "Rath Yatra", gazetted = false),
        day(2027, 8, 15, "Parsi New Year (Navroz)", gazetted = false),
        day(2027, 8, 17, "Raksha Bandhan", gazetted = false),
        day(2027, 9, 4, "Ganesh Chaturthi", gazetted = false),
        day(2027, 9, 12, "Onam", gazetted = false),
        day(2027, 10, 6, "Durga Puja (Saptami)", gazetted = false),
        day(2027, 10, 7, "Durga Puja (Ashtami)", gazetted = false),
        day(2027, 10, 8, "Durga Puja (Navami)", gazetted = false),
        day(2027, 10, 15, "Valmiki Jayanti", gazetted = false),
        day(2027, 10, 18, "Karva Chauth", gazetted = false),
        day(2027, 10, 28, "Naraka Chaturdashi (Chhoti Diwali)", gazetted = false),
        day(2027, 10, 30, "Govardhan Puja", gazetted = false),
        day(2027, 10, 31, "Bhai Dooj", gazetted = false),
        day(2027, 11, 4, "Chhath Puja", gazetted = false),
        day(2027, 11, 24, "Guru Tegh Bahadur Martyrdom Day", gazetted = false),
        day(2027, 12, 12, "Hazrat Ali Jayanti", gazetted = false),
        day(2027, 12, 24, "Christmas Eve", gazetted = false),
    )

    private val byDate: Map<LocalDate, List<Holiday>> = all.groupBy { it.date }

    /** The holidays on [date], gazetted ones first (usually none or one). */
    fun on(date: LocalDate): List<Holiday> = byDate[date].orEmpty().sortedByDescending { it.gazetted }

    /** Every holiday from [from] to [to], both included, in date order. */
    fun between(from: LocalDate, to: LocalDate): List<Holiday> = all.filter { it.date in from..to }.sortedBy { it.date }
}
