package com.meyouww.core.designsystem.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WhityRulesTest {
    private val hour = 60L * 60L * 1000L
    private val start = 1_800_000_000_000L
    private val today = 20_000L

    @Test
    fun `first visit starts the clock and fills the jar`() {
        val s = WhityRules.settle(WhityPetState(), start, today)
        assertThat(s.moodAt).isEqualTo(start)
        assertThat(s.mood).isEqualTo(WhityPetState.START_MOOD)
        assertThat(s.treats).isEqualTo(WhityPetState.DAILY_TREATS)
        assertThat(s.day).isEqualTo(today)
    }

    @Test
    fun `mood drifts down by the hour and keeps the leftover minutes`() {
        val s = WhityPetState(mood = 70, moodAt = start, day = today)
        val later = WhityRules.settle(s, start + 3 * hour + 20 * 60_000L, today)
        assertThat(later.mood).isEqualTo(70 - 3 * WhityPetState.DRIFT_PER_HOUR)
        assertThat(later.moodAt).isEqualTo(start + 3 * hour)
        // Under an hour changes nothing.
        assertThat(WhityRules.settle(s, start + 59 * 60_000L, today)).isEqualTo(s)
    }

    @Test
    fun `mood never drops below the floor`() {
        val s = WhityPetState(mood = 40, moodAt = start, day = today)
        assertThat(WhityRules.settle(s, start + 1_000 * hour, today).mood).isEqualTo(WhityPetState.FLOOR)
    }

    @Test
    fun `a clock that went back just restarts the timer`() {
        val s = WhityPetState(mood = 50, moodAt = start, day = today)
        val back = WhityRules.settle(s, start - 5 * hour, today)
        assertThat(back.mood).isEqualTo(50)
        assertThat(back.moodAt).isEqualTo(start - 5 * hour)
    }

    @Test
    fun `a new day refills the jar and starts the pets again`() {
        val s = WhityPetState(moodAt = start, day = today, treats = 0, petsToday = 12, totalPets = 40)
        val next = WhityRules.settle(s, start, today + 1)
        assertThat(next.treats).isEqualTo(WhityPetState.DAILY_TREATS)
        assertThat(next.petsToday).isEqualTo(0)
        assertThat(next.totalPets).isEqualTo(40)
    }

    @Test
    fun `pets lift her mood up to a daily limit and are always counted`() {
        var s = WhityPetState(mood = 20, day = today)
        s = WhityRules.pet(s)
        assertThat(s.mood).isEqualTo(20 + WhityPetState.PET_LIFT)
        assertThat(s.petsToday).isEqualTo(1)
        s = WhityRules.pet(s, WhityPetState.TAP_LIFT)
        assertThat(s.mood).isEqualTo(20 + WhityPetState.PET_LIFT + WhityPetState.TAP_LIFT)

        val tired = WhityPetState(mood = 50, petsToday = WhityPetState.LIFTING_PETS_PER_DAY, totalPets = 99)
        val after = WhityRules.pet(tired)
        assertThat(after.mood).isEqualTo(50)
        assertThat(after.petsToday).isEqualTo(WhityPetState.LIFTING_PETS_PER_DAY + 1)
        assertThat(after.totalPets).isEqualTo(100)

        assertThat(WhityRules.pet(WhityPetState(mood = 99)).mood).isEqualTo(WhityPetState.MAX_MOOD)
    }

    @Test
    fun `treats come from the jar until it is empty`() {
        val s = WhityPetState(mood = 50, treats = 1)
        val fed = WhityRules.feed(s)!!
        assertThat(fed.treats).isEqualTo(0)
        assertThat(fed.totalTreats).isEqualTo(1)
        assertThat(fed.mood).isEqualTo(50 + WhityPetState.TREAT_LIFT)
        assertThat(WhityRules.feed(fed)).isNull()
        assertThat(WhityRules.feed(WhityPetState(mood = 95, treats = 2))!!.mood).isEqualTo(WhityPetState.MAX_MOOD)
    }

    @Test
    fun `mood levels`() {
        assertThat(WhityMood.of(10)).isEqualTo(WhityMood.Sleepy)
        assertThat(WhityMood.of(34)).isEqualTo(WhityMood.Sleepy)
        assertThat(WhityMood.of(35)).isEqualTo(WhityMood.Cosy)
        assertThat(WhityMood.of(60)).isEqualTo(WhityMood.Happy)
        assertThat(WhityMood.of(85)).isEqualTo(WhityMood.OverTheMoon)
        assertThat(WhityMood.of(WhityPetState.START_MOOD)).isEqualTo(WhityMood.Happy)
    }
}
