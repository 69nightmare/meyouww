package com.meyouww.core.designsystem.pet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Whity's little life on this phone: how happy she is, the treats in her jar, how often she has
 * been petted, the days in a row you've visited her, what she wears and what she last said.
 * Kept only on this phone; nothing goes to the server.
 */
data class WhityPetState(
    /** 0..100. Drifts down slowly; pets and treats lift it. */
    val mood: Int = START_MOOD,
    /** When [mood] was last brought up to date (epoch ms); 0 before the first visit. */
    val moodAt: Long = 0L,
    /** Treats in the jar: topped up to [DAILY_TREATS] each morning, more earned by using the app. */
    val treats: Int = DAILY_TREATS,
    /** The last day (epoch day) you visited her; [treats], [petsToday] and [earnedToday] belong to it. */
    val day: Long = -1L,
    val petsToday: Int = 0,
    val totalPets: Int = 0,
    val totalTreats: Int = 0,
    /** How many times the "hold me for my corner" hint has shown. */
    val hints: Int = 0,
    /** False when the owner hid her from Home, People and Chat. */
    val shown: Boolean = true,
    /** Days visited in a row; one missed day is forgiven. */
    val streak: Int = 0,
    /** The longest [streak] so far: the outfits it unlocked stay unlocked. */
    val bestStreak: Int = 0,
    /** The kinds of treat already earned today, as [WhityEarn.bit]s. */
    val earnedToday: Int = 0,
    /** The Monday (epoch day) that the week's counts below belong to. */
    val week: Long = -1L,
    val weekMessages: Int = 0,
    val weekDoodles: Int = 0,
    val weekReminders: Int = 0,
    /** Something she wants to tell you the next time she talks. */
    val news: WhityNews? = null,
    /** The outfit she wears on ordinary days, or null for none. */
    val outfit: WhityOutfit? = null,
    /** False when the owner asked her not to pop in or chat. */
    val roams: Boolean = true,
    /** When she last said something (epoch ms), and how many times she has talked today. */
    val talkedAt: Long = 0L,
    val talksToday: Int = 0,
    /** The day she last wished you a happy festival. */
    val greetedDay: Long = -1L,
    /** Which of her silly facts she tells next. */
    val factIndex: Int = 0,
) {
    val level: WhityMood get() = WhityMood.of(mood)

    /** The outfits the best streak so far has unlocked. */
    val unlocked: List<WhityOutfit> get() = WhityOutfit.entries.filter { it.streak in 1..bestStreak }

    companion object {
        const val START_MOOD = 70
        const val MAX_MOOD = 100
        const val DAILY_TREATS = 3

        /** The most treats the jar holds. */
        const val JAR_SIZE = 6

        /** Mood points she loses per hour. */
        const val DRIFT_PER_HOUR = 2

        /** She never drops below this: when her mood is low she just naps. */
        const val FLOOR = 10
        const val PET_LIFT = 3
        const val TAP_LIFT = 1
        const val TREAT_LIFT = 18

        /** Pets that lift her mood each day. More are still welcome, and counted. */
        const val LIFTING_PETS_PER_DAY = 20
        const val HINT_TIMES = 3

        /** Days apart that still count as "in a row": missing one day is forgiven. */
        const val STREAK_GAP_DAYS = 2
    }
}

/** How Whity feels, from her mood. She is never sad: low just means sleepy. */
enum class WhityMood {
    Sleepy,
    Cosy,
    Happy,
    OverTheMoon,
    ;

    companion object {
        fun of(mood: Int): WhityMood = when {
            mood >= 85 -> OverTheMoon
            mood >= 60 -> Happy
            mood >= 35 -> Cosy
            else -> Sleepy
        }
    }
}

/** The things you do in the app that earn her a treat: one of each a day. */
enum class WhityEarn {
    Message,
    Doodle,
    Reminder,
    ;

    val bit: Int get() = 1 shl ordinal
}

/** What she wants to tell you next. */
enum class WhityNews {
    EarnedMessage,
    EarnedDoodle,
    EarnedReminder,

    /** A longer streak unlocked a new outfit. */
    Unlocked,
}

/**
 * What she can wear. Streak outfits unlock after [streak] days in a row and stay unlocked;
 * festival outfits ([streak] 0) she wears by herself on their days.
 */
enum class WhityOutfit(val streak: Int) {
    Bow(2),
    PartyHat(4),
    FlowerCrown(7),
    CoolShades(14),
    Beanie(30),
    SantaHat(0),
    Diyas(0),
    Holi(0),
    Kite(0),
    ;

    companion object {
        /** The outfits you can unlock with streaks, in the order they unlock. */
        val forStreaks: List<WhityOutfit> = entries.filter { it.streak > 0 }.sortedBy { it.streak }
    }
}

/** Her treats. Ordinary days take turns; festivals bring their own. */
enum class WhityTreat {
    Cookie,
    Momo,
    IceCream,
    Bone,
    Laddoo,
    Gujiya,
    Cupcake,
    ;

    companion object {
        private val everyday = listOf(Cookie, Momo, IceCream, Bone)

        /** The treat in her jar on [date]: the festival's own, or the day's turn. */
        fun on(date: LocalDate): WhityTreat = WhityFestivals.on(date)?.treat ?: everyday[Math.floorMod(date.toEpochDay(), everyday.size)]
    }
}

/** The rules of Whity's day, kept free of Android so they are easy to test. */
object WhityRules {
    private const val HOUR_MS = 60L * 60L * 1000L

    /**
     * Brings [state] up to [nowMs]: her mood drifts down for each full hour away (never below
     * the floor). A new day tops the treat jar up, starts today's pets and earned treats from
     * zero and moves the streak on (or starts it again after more than a day away).
     */
    fun settle(state: WhityPetState, nowMs: Long, today: Long): WhityPetState {
        var s = state
        s = when {
            s.moodAt == 0L || nowMs < s.moodAt -> s.copy(moodAt = nowMs) // first visit, or the clock went back
            else -> {
                val hours = (nowMs - s.moodAt) / HOUR_MS
                if (hours == 0L) {
                    s
                } else {
                    val drift = (hours * WhityPetState.DRIFT_PER_HOUR).coerceAtMost(WhityPetState.MAX_MOOD.toLong()).toInt()
                    val floor = minOf(s.mood, WhityPetState.FLOOR)
                    // Keep the leftover minutes, so short visits still add up to an hour.
                    s.copy(mood = (s.mood - drift).coerceAtLeast(floor), moodAt = s.moodAt + hours * HOUR_MS)
                }
            }
        }
        if (s.day != today) {
            val gap = today - s.day
            val streak = when {
                s.day < 0 -> 1
                gap < 0 -> s.streak // the clock went back a day or more
                gap <= WhityPetState.STREAK_GAP_DAYS -> s.streak + 1
                else -> 1
            }
            val best = maxOf(s.bestStreak, streak)
            val newOutfit = WhityOutfit.forStreaks.any { it.streak in (s.bestStreak + 1)..best }
            s = s.copy(
                day = today,
                treats = maxOf(s.treats, WhityPetState.DAILY_TREATS),
                petsToday = 0,
                earnedToday = 0,
                talksToday = 0,
                streak = streak,
                bestStreak = best,
                news = if (newOutfit) WhityNews.Unlocked else s.news,
            )
        }
        val monday = weekStart(today)
        if (s.week != monday) s = s.copy(week = monday, weekMessages = 0, weekDoodles = 0, weekReminders = 0)
        return s
    }

    /** The Monday (epoch day) of the week [day] falls in. */
    fun weekStart(day: Long): Long = Math.floorDiv(day + 3, 7L) * 7L - 3L

    /** A pet (a stroke, or a tap with [lift] 1). Only the day's first few lift her mood. */
    fun pet(state: WhityPetState, lift: Int = WhityPetState.PET_LIFT): WhityPetState {
        val lifting = state.petsToday < WhityPetState.LIFTING_PETS_PER_DAY
        return state.copy(
            petsToday = state.petsToday + 1,
            totalPets = state.totalPets + 1,
            mood = if (lifting) (state.mood + lift).coerceAtMost(WhityPetState.MAX_MOOD) else state.mood,
        )
    }

    /** Gives her a treat from the jar; null when the jar is empty. */
    fun feed(state: WhityPetState): WhityPetState? {
        if (state.treats <= 0) return null
        return state.copy(
            treats = state.treats - 1,
            totalTreats = state.totalTreats + 1,
            mood = (state.mood + WhityPetState.TREAT_LIFT).coerceAtMost(WhityPetState.MAX_MOOD),
        )
    }

    /**
     * You did something she loves ([kind]): it is counted for the week, and the first of each
     * kind a day puts a treat in the jar (unless the jar is full) for her to thank you for.
     */
    fun earn(state: WhityPetState, kind: WhityEarn): WhityPetState {
        val s = when (kind) {
            WhityEarn.Message -> state.copy(weekMessages = state.weekMessages + 1)
            WhityEarn.Doodle -> state.copy(weekDoodles = state.weekDoodles + 1)
            WhityEarn.Reminder -> state.copy(weekReminders = state.weekReminders + 1)
        }
        if ((s.earnedToday and kind.bit) != 0 || s.treats >= WhityPetState.JAR_SIZE) return s
        val news = when (kind) {
            WhityEarn.Message -> WhityNews.EarnedMessage
            WhityEarn.Doodle -> WhityNews.EarnedDoodle
            WhityEarn.Reminder -> WhityNews.EarnedReminder
        }
        return s.copy(
            treats = s.treats + 1,
            earnedToday = s.earnedToday or kind.bit,
            // A new outfit is bigger news than a treat: keep that one for her to tell.
            news = if (s.news == WhityNews.Unlocked) s.news else news,
        )
    }

    /** Puts on [outfit] (an unlocked one), or takes it off when she's already wearing it. */
    fun wear(state: WhityPetState, outfit: WhityOutfit): WhityPetState = when {
        state.outfit == outfit -> state.copy(outfit = null)
        outfit in state.unlocked -> state.copy(outfit = outfit)
        else -> state
    }

    /** What she wears on [date]: the festival's outfit, else the one you chose (if it's still unlocked). */
    fun outfitOn(state: WhityPetState, date: LocalDate): WhityOutfit? =
        WhityFestivals.on(date)?.outfit ?: state.outfit?.takeIf { it in state.unlocked }
}

/** Where Whity's state lives: one copy for the whole app, saved in this phone's preferences. */
object WhityPetStore {
    private const val FILE = "whity_pet"
    private val lock = Any()

    @Volatile
    private var holder: MutableStateFlow<WhityPetState>? = null

    fun state(context: Context): StateFlow<WhityPetState> = flow(context).asStateFlow()

    /** Brings her mood, treat jar and streak up to now, applies [change], saves and returns the result. */
    fun update(context: Context, change: (WhityPetState) -> WhityPetState = { it }): WhityPetState {
        val flow = flow(context)
        synchronized(lock) {
            val settled = WhityRules.settle(flow.value, System.currentTimeMillis(), LocalDate.now().toEpochDay())
            val next = change(settled)
            if (next != flow.value) {
                flow.value = next
                save(context, next)
            }
            return next
        }
    }

    /** Gives her a treat; false when the jar is empty. */
    fun feed(context: Context): Boolean {
        var fed = false
        update(context) { state -> WhityRules.feed(state)?.also { fed = true } ?: state }
        return fed
    }

    /** You did something that earns her a treat (see [WhityRules.earn]). */
    fun earn(context: Context, kind: WhityEarn) {
        update(context) { WhityRules.earn(it, kind) }
    }

    /**
     * What she says now, or null when she shouldn't talk yet ([force] for when you tapped her).
     * [next] is what's coming up, from the screen she's on.
     */
    fun talk(context: Context, next: String? = null, force: Boolean = false): WhityLine? {
        var line: WhityLine? = null
        update(context) { state ->
            val now = System.currentTimeMillis()
            val date = LocalDate.now()
            if (!force && !WhityTalk.mayTalk(state, now, date)) {
                state
            } else {
                val (said, after) = WhityTalk.pick(state, now, date, java.time.LocalTime.now().hour, next)
                line = said
                after
            }
        }
        return line
    }

    private fun flow(context: Context): MutableStateFlow<WhityPetState> =
        holder ?: synchronized(lock) { holder ?: MutableStateFlow(load(context)).also { holder = it } }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun load(context: Context): WhityPetState {
        val p = prefs(context)
        val fresh = WhityPetState()
        return WhityPetState(
            mood = p.getInt("mood", fresh.mood),
            moodAt = p.getLong("mood_at", fresh.moodAt),
            treats = p.getInt("treats", fresh.treats),
            day = p.getLong("day", fresh.day),
            petsToday = p.getInt("pets_today", fresh.petsToday),
            totalPets = p.getInt("total_pets", fresh.totalPets),
            totalTreats = p.getInt("total_treats", fresh.totalTreats),
            hints = p.getInt("hints", fresh.hints),
            shown = p.getBoolean("shown", fresh.shown),
            streak = p.getInt("streak", fresh.streak),
            bestStreak = p.getInt("best_streak", fresh.bestStreak),
            earnedToday = p.getInt("earned_today", fresh.earnedToday),
            week = p.getLong("week", fresh.week),
            weekMessages = p.getInt("week_messages", fresh.weekMessages),
            weekDoodles = p.getInt("week_doodles", fresh.weekDoodles),
            weekReminders = p.getInt("week_reminders", fresh.weekReminders),
            news = p.getString("news", null)?.let { name -> WhityNews.entries.firstOrNull { it.name == name } },
            outfit = p.getString("outfit", null)?.let { name -> WhityOutfit.entries.firstOrNull { it.name == name } },
            roams = p.getBoolean("roams", fresh.roams),
            talkedAt = p.getLong("talked_at", fresh.talkedAt),
            talksToday = p.getInt("talks_today", fresh.talksToday),
            greetedDay = p.getLong("greeted_day", fresh.greetedDay),
            factIndex = p.getInt("fact_index", fresh.factIndex),
        )
    }

    private fun save(context: Context, s: WhityPetState) {
        prefs(context).edit()
            .putInt("mood", s.mood)
            .putLong("mood_at", s.moodAt)
            .putInt("treats", s.treats)
            .putLong("day", s.day)
            .putInt("pets_today", s.petsToday)
            .putInt("total_pets", s.totalPets)
            .putInt("total_treats", s.totalTreats)
            .putInt("hints", s.hints)
            .putBoolean("shown", s.shown)
            .putInt("streak", s.streak)
            .putInt("best_streak", s.bestStreak)
            .putInt("earned_today", s.earnedToday)
            .putLong("week", s.week)
            .putInt("week_messages", s.weekMessages)
            .putInt("week_doodles", s.weekDoodles)
            .putInt("week_reminders", s.weekReminders)
            .putString("news", s.news?.name)
            .putString("outfit", s.outfit?.name)
            .putBoolean("roams", s.roams)
            .putLong("talked_at", s.talkedAt)
            .putInt("talks_today", s.talksToday)
            .putLong("greeted_day", s.greetedDay)
            .putInt("fact_index", s.factIndex)
            .apply()
    }
}
