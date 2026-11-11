package com.meyouww.core.designsystem.pet

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Whity's little life on this phone: how happy she is, the treats left in her jar today and
 * how often she has been petted. Kept only on this phone; nothing goes to the server.
 */
data class WhityPetState(
    /** 0..100. Drifts down slowly; pets and treats lift it. */
    val mood: Int = START_MOOD,
    /** When [mood] was last brought up to date (epoch ms); 0 before the first visit. */
    val moodAt: Long = 0L,
    /** Treats left in the jar today. */
    val treats: Int = DAILY_TREATS,
    /** The day (epoch day) that [treats] and [petsToday] belong to. */
    val day: Long = -1L,
    val petsToday: Int = 0,
    val totalPets: Int = 0,
    val totalTreats: Int = 0,
    /** How many times the "hold me for my corner" hint has shown. */
    val hints: Int = 0,
    /** False when the owner hid her from Home, People and Chat. */
    val shown: Boolean = true,
) {
    val level: WhityMood get() = WhityMood.of(mood)

    companion object {
        const val START_MOOD = 70
        const val MAX_MOOD = 100
        const val DAILY_TREATS = 3

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

/** The rules of Whity's day, kept free of Android so they are easy to test. */
object WhityRules {
    private const val HOUR_MS = 60L * 60L * 1000L

    /**
     * Brings [state] up to [nowMs]: her mood drifts down for each full hour away (never below
     * the floor), and a new day refills the treat jar and starts today's pets from zero.
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
        if (s.day != today) s = s.copy(day = today, treats = WhityPetState.DAILY_TREATS, petsToday = 0)
        return s
    }

    /** A pet (a stroke, or a tap with [lift] 1). Only the day's first few lift her mood. */
    fun pet(state: WhityPetState, lift: Int = WhityPetState.PET_LIFT): WhityPetState {
        val lifting = state.petsToday < WhityPetState.LIFTING_PETS_PER_DAY
        return state.copy(
            petsToday = state.petsToday + 1,
            totalPets = state.totalPets + 1,
            mood = if (lifting) (state.mood + lift).coerceAtMost(WhityPetState.MAX_MOOD) else state.mood,
        )
    }

    /** Gives her a treat from the jar; null when the jar is empty today. */
    fun feed(state: WhityPetState): WhityPetState? {
        if (state.treats <= 0) return null
        return state.copy(
            treats = state.treats - 1,
            totalTreats = state.totalTreats + 1,
            mood = (state.mood + WhityPetState.TREAT_LIFT).coerceAtMost(WhityPetState.MAX_MOOD),
        )
    }
}

/** Where Whity's state lives: one copy for the whole app, saved in this phone's preferences. */
object WhityPetStore {
    private const val FILE = "whity_pet"
    private val lock = Any()

    @Volatile
    private var holder: MutableStateFlow<WhityPetState>? = null

    fun state(context: Context): StateFlow<WhityPetState> = flow(context).asStateFlow()

    /** Brings her mood and treat jar up to now, applies [change], saves and returns the result. */
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

    /** Gives her a treat; false when the jar is empty today. */
    fun feed(context: Context): Boolean {
        var fed = false
        update(context) { state -> WhityRules.feed(state)?.also { fed = true } ?: state }
        return fed
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
            .apply()
    }
}
