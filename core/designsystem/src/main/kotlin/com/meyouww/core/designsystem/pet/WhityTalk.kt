package com.meyouww.core.designsystem.pet

import com.meyouww.core.model.IndianHolidays
import java.time.LocalDate

/** Something Whity says in her speech bubble; the screen puts it into words. */
sealed interface WhityLine {
    /** Thanks for what earned her a treat. */
    data class Earned(val kind: WhityEarn) : WhityLine

    /** [days] in a row unlocked [outfit]. */
    data class Unlocked(val days: Int, val outfit: WhityOutfit) : WhityLine

    /** A festival wish, already in words. */
    data class Festival(val greeting: String) : WhityLine

    /** What's coming up, already in words (from the screen she's on). */
    data class Next(val text: String) : WhityLine

    data class Streak(val days: Int) : WhityLine

    data class Doodles(val count: Int) : WhityLine

    data class Messages(val count: Int) : WhityLine

    data class Reminders(val count: Int) : WhityLine

    /** There are treats in the jar and her mood is low-ish. */
    data object Hungry : WhityLine

    data object Morning : WhityLine

    data object Evening : WhityLine

    data object Night : WhityLine

    /** One of her silly facts (taking turns, so [index] keeps counting up). */
    data class Fact(val index: Int) : WhityLine

    /** "Hold me for my corner", for the first few taps. */
    data object HoldHint : WhityLine
}

/** When Whity talks and what she says, kept free of Android so it is easy to test. */
object WhityTalk {
    /** She chats by herself at most this often... */
    const val TALK_GAP_MS = 30L * 60L * 1000L

    /** ...and this many times a day. */
    const val TALKS_PER_DAY = 8

    /** News (a treat earned, a new outfit, a festival) can't wait half an hour: just this long. */
    const val NEWS_GAP_MS = 60L * 1000L

    /** Whether she may say something by herself now (tapping her always works). */
    fun mayTalk(state: WhityPetState, nowMs: Long, date: LocalDate): Boolean {
        if (!state.shown || !state.roams) return false
        val gap = nowMs - state.talkedAt
        if (gap < 0) return true // the clock went back
        val urgent = state.news != null || (WhityFestivals.on(date) != null && state.greetedDay != date.toEpochDay())
        return if (urgent) gap >= NEWS_GAP_MS else gap >= TALK_GAP_MS && state.talksToday < TALKS_PER_DAY
    }

    /**
     * What she says now and her state after saying it. News comes first, then a festival wish
     * (once a day), then, taking turns, what's [next] on the screen she's on, something about
     * you (your streak, your week, the time of day) and her silly facts.
     */
    fun pick(state: WhityPetState, nowMs: Long, date: LocalDate, hour: Int, next: String?): Pair<WhityLine, WhityPetState> {
        val said = state.copy(talkedAt = nowMs, talksToday = state.talksToday + 1)
        when (state.news) {
            WhityNews.EarnedMessage -> return WhityLine.Earned(WhityEarn.Message) to said.copy(news = null)
            WhityNews.EarnedDoodle -> return WhityLine.Earned(WhityEarn.Doodle) to said.copy(news = null)
            WhityNews.EarnedReminder -> return WhityLine.Earned(WhityEarn.Reminder) to said.copy(news = null)
            WhityNews.Unlocked -> {
                val newest = state.unlocked.maxByOrNull { it.streak }
                if (newest != null) return WhityLine.Unlocked(newest.streak, newest) to said.copy(news = null)
            }
            null -> Unit
        }
        val today = date.toEpochDay()
        val festival = WhityFestivals.on(date)
        if (festival != null && state.greetedDay != today) {
            return WhityLine.Festival(festival.greeting) to said.copy(news = null, greetedDay = today)
        }
        // Turns: with something coming up, every other line is about it. The rest alternate
        // between something about you and a silly fact.
        val turn = state.talksToday
        if (next != null && turn % 2 == 0) return WhityLine.Next(next) to said.copy(news = null)
        val slot = if (next != null) turn / 2 else turn
        val personal = buildList {
            if (state.treats > 0 && state.mood < 50) add(WhityLine.Hungry)
            if (state.streak >= 2) add(WhityLine.Streak(state.streak))
            if (state.weekDoodles >= 2) add(WhityLine.Doodles(state.weekDoodles))
            if (state.weekMessages >= 10) add(WhityLine.Messages(state.weekMessages))
            if (state.weekReminders >= 2) add(WhityLine.Reminders(state.weekReminders))
            when (hour) {
                in 5..10 -> add(WhityLine.Morning)
                in 17..20 -> add(WhityLine.Evening)
                22, 23, 0, 1, 2, 3, 4 -> add(WhityLine.Night)
            }
        }
        if (personal.isNotEmpty() && slot % 2 == 0) {
            return personal[(slot / 2) % personal.size] to said.copy(news = null)
        }
        return WhityLine.Fact(state.factIndex) to said.copy(news = null, factIndex = state.factIndex + 1)
    }
}

/** A festival Whity knows: how she wishes you, and what she wears and munches that day. */
data class WhityFestival(
    /** The holiday's name in [IndianHolidays]. */
    val name: String,
    val greeting: String,
    val outfit: WhityOutfit? = null,
    val treat: WhityTreat? = null,
)

/**
 * The days Whity celebrates, matched to India's holiday list by name. Days of mourning or
 * remembrance (Muharram, Good Friday, martyrdom days) get no wish.
 */
object WhityFestivals {
    internal val known: Map<String, WhityFestival> = listOf(
        WhityFestival("New Year's Day", "Happy New Year! 🎉", WhityOutfit.PartyHat, WhityTreat.Cupcake),
        WhityFestival("Makar Sankranti", "Happy Makar Sankranti! 🪁", WhityOutfit.Kite, WhityTreat.Laddoo),
        WhityFestival("Pongal, Guru Gobind Singh Jayanti", "Happy Pongal and Guru Gobind Singh Jayanti! 🌾"),
        WhityFestival("Republic Day", "Happy Republic Day! 🇮🇳"),
        WhityFestival("Basant Panchami", "Happy Basant Panchami! 🪁", WhityOutfit.Kite),
        WhityFestival("Maha Shivratri", "Happy Maha Shivratri! 🙏"),
        WhityFestival("Holika Dahan", "Happy Holika Dahan! 🔥", treat = WhityTreat.Gujiya),
        WhityFestival("Holi", "Happy Holi! 🌈", WhityOutfit.Holi, WhityTreat.Gujiya),
        WhityFestival("Eid al-Fitr", "Eid Mubarak! 🌙"),
        WhityFestival("Ram Navami", "Happy Ram Navami! 🙏"),
        WhityFestival("Mahavir Jayanti", "Happy Mahavir Jayanti! 🙏"),
        WhityFestival("Easter Sunday", "Happy Easter! 🐣"),
        WhityFestival("Gudi Padwa, Ugadi, first Navratri", "Happy Gudi Padwa and Ugadi! 🌸"),
        WhityFestival("Vaisakhi, Vishu, Tamil New Year", "Happy Vaisakhi, Vishu and Puthandu! 🌾"),
        WhityFestival("Bengali New Year, Bohag Bihu", "Shubho Noboborsho and happy Bihu! 🌸"),
        WhityFestival("Buddha Purnima", "Happy Buddha Purnima! 🪷"),
        WhityFestival("Eid al-Adha (Bakrid)", "Eid Mubarak! 🌙"),
        WhityFestival("Rath Yatra", "Happy Rath Yatra! 🙏"),
        WhityFestival("Independence Day", "Happy Independence Day! 🇮🇳", WhityOutfit.Kite),
        WhityFestival("Parsi New Year (Navroz)", "Navroz Mubarak! 🌸"),
        WhityFestival("Raksha Bandhan", "Happy Raksha Bandhan! 🎀", treat = WhityTreat.Laddoo),
        WhityFestival("Janmashtami", "Happy Janmashtami! 🦚"),
        WhityFestival("Ganesh Chaturthi", "Ganpati Bappa Morya! 🐘", treat = WhityTreat.Laddoo),
        WhityFestival("Onam", "Happy Onam! 🌼"),
        WhityFestival("Gandhi Jayanti", "Happy Gandhi Jayanti! 🕊️"),
        WhityFestival("Durga Puja (Saptami)", "Happy Durga Puja! 🌺"),
        WhityFestival("Durga Puja (Ashtami)", "Happy Durga Puja! 🌺"),
        WhityFestival("Durga Puja (Navami)", "Happy Durga Puja! 🌺"),
        WhityFestival("Dussehra", "Happy Dussehra! 🏹", treat = WhityTreat.Laddoo),
        WhityFestival("Karva Chauth", "Happy Karva Chauth! 🌕"),
        WhityFestival("Naraka Chaturdashi (Chhoti Diwali)", "Happy Chhoti Diwali! 🪔", WhityOutfit.Diyas, WhityTreat.Laddoo),
        WhityFestival("Diwali", "Happy Diwali! 🪔", WhityOutfit.Diyas, WhityTreat.Laddoo),
        WhityFestival("Govardhan Puja", "Happy Govardhan Puja! 🙏", treat = WhityTreat.Laddoo),
        WhityFestival("Bhai Dooj", "Happy Bhai Dooj! 💛", treat = WhityTreat.Laddoo),
        WhityFestival("Chhath Puja", "Happy Chhath Puja! 🌅"),
        WhityFestival("Guru Nanak Jayanti", "Happy Gurpurab! 🙏"),
        WhityFestival("Christmas Eve", "It's Christmas Eve! 🎄", WhityOutfit.SantaHat, WhityTreat.Cupcake),
        WhityFestival("Christmas", "Merry Christmas! 🎄", WhityOutfit.SantaHat, WhityTreat.Cupcake),
    ).associateBy { it.name }

    /** The festival Whity celebrates on [date], if any. */
    fun on(date: LocalDate): WhityFestival? = IndianHolidays.on(date).firstNotNullOfOrNull { known[it.name] }
}
