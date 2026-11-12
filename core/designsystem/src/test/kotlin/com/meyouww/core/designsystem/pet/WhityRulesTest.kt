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
        val s = WhityPetState(mood = 70, moodAt = start, day = today, week = WhityRules.weekStart(today))
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
    fun `the jar keeps earned treats overnight and tops up to three`() {
        val full = WhityRules.settle(WhityPetState(moodAt = start, day = today, treats = 5), start, today + 1)
        assertThat(full.treats).isEqualTo(5)
        val low = WhityRules.settle(WhityPetState(moodAt = start, day = today, treats = 1, earnedToday = 7), start, today + 1)
        assertThat(low.treats).isEqualTo(WhityPetState.DAILY_TREATS)
        assertThat(low.earnedToday).isEqualTo(0)
    }

    @Test
    fun `visits in a row grow the streak and one missed day is forgiven`() {
        var s = WhityRules.settle(WhityPetState(), start, today)
        assertThat(s.streak).isEqualTo(1)
        s = WhityRules.settle(s, start, today + 1)
        assertThat(s.streak).isEqualTo(2)
        // Same day again: nothing moves.
        assertThat(WhityRules.settle(s, start + hour, today + 1).streak).isEqualTo(2)
        // One day missed: still going.
        s = WhityRules.settle(s, start, today + 3)
        assertThat(s.streak).isEqualTo(3)
        // Two days missed: it starts again, but the best is kept.
        s = WhityRules.settle(s, start, today + 6)
        assertThat(s.streak).isEqualTo(1)
        assertThat(s.bestStreak).isEqualTo(3)
    }

    @Test
    fun `a longer streak unlocks outfits for good and she wants to tell you`() {
        val s = WhityRules.settle(WhityPetState(moodAt = start, day = today, streak = 1, bestStreak = 1), start, today + 1)
        assertThat(s.streak).isEqualTo(2)
        assertThat(s.unlocked).containsExactly(WhityOutfit.Bow)
        assertThat(s.news).isEqualTo(WhityNews.Unlocked)
        // Breaking the streak keeps what was unlocked.
        val later = WhityRules.settle(s.copy(news = null), start, today + 10)
        assertThat(later.streak).isEqualTo(1)
        assertThat(later.unlocked).containsExactly(WhityOutfit.Bow)
        assertThat(later.news).isNull()
        // Festival outfits never unlock by streaks.
        assertThat(WhityPetState(bestStreak = 1000).unlocked).containsExactlyElementsIn(WhityOutfit.forStreaks)
    }

    @Test
    fun `doing things earns one treat of each kind a day and counts the week`() {
        var s = WhityPetState(treats = 2)
        s = WhityRules.earn(s, WhityEarn.Doodle)
        assertThat(s.treats).isEqualTo(3)
        assertThat(s.news).isEqualTo(WhityNews.EarnedDoodle)
        s = WhityRules.earn(s.copy(news = null), WhityEarn.Doodle)
        assertThat(s.treats).isEqualTo(3)
        assertThat(s.news).isNull()
        assertThat(s.weekDoodles).isEqualTo(2)
        s = WhityRules.earn(s, WhityEarn.Message)
        s = WhityRules.earn(s, WhityEarn.Reminder)
        assertThat(s.treats).isEqualTo(5)
        assertThat(s.news).isEqualTo(WhityNews.EarnedReminder)
        assertThat(s.weekMessages).isEqualTo(1)
        assertThat(s.weekReminders).isEqualTo(1)
    }

    @Test
    fun `a full jar earns nothing and news of an outfit is kept`() {
        val full = WhityRules.earn(WhityPetState(treats = WhityPetState.JAR_SIZE), WhityEarn.Message)
        assertThat(full.treats).isEqualTo(WhityPetState.JAR_SIZE)
        assertThat(full.earnedToday).isEqualTo(0)
        assertThat(full.news).isNull()
        assertThat(full.weekMessages).isEqualTo(1)
        val outfit = WhityRules.earn(WhityPetState(news = WhityNews.Unlocked), WhityEarn.Message)
        assertThat(outfit.news).isEqualTo(WhityNews.Unlocked)
    }

    @Test
    fun `a new week starts the counts again`() {
        val monday = WhityRules.weekStart(today)
        val s = WhityPetState(moodAt = start, day = monday, week = monday, weekDoodles = 4, weekMessages = 30)
        val sameWeek = WhityRules.settle(s, start, monday + 6)
        assertThat(sameWeek.weekDoodles).isEqualTo(4)
        assertThat(sameWeek.weekMessages).isEqualTo(30)
        val next = WhityRules.settle(s, start, monday + 7)
        assertThat(next.week).isEqualTo(monday + 7)
        assertThat(next.weekDoodles).isEqualTo(0)
        assertThat(next.weekMessages).isEqualTo(0)
    }

    @Test
    fun `weeks start on Monday`() {
        val monday = java.time.LocalDate.of(2026, 11, 9)
        for (i in 0..6) {
            assertThat(WhityRules.weekStart(monday.plusDays(i.toLong()).toEpochDay())).isEqualTo(monday.toEpochDay())
        }
        assertThat(WhityRules.weekStart(monday.plusDays(7).toEpochDay())).isEqualTo(monday.plusDays(7).toEpochDay())
    }

    @Test
    fun `she only wears unlocked outfits and festivals dress her up`() {
        var s = WhityPetState(bestStreak = 4)
        s = WhityRules.wear(s, WhityOutfit.PartyHat)
        assertThat(s.outfit).isEqualTo(WhityOutfit.PartyHat)
        assertThat(WhityRules.wear(s, WhityOutfit.Beanie).outfit).isEqualTo(WhityOutfit.PartyHat)
        assertThat(WhityRules.wear(s, WhityOutfit.PartyHat).outfit).isNull()
        val ordinary = java.time.LocalDate.of(2026, 11, 12)
        assertThat(WhityRules.outfitOn(s, ordinary)).isEqualTo(WhityOutfit.PartyHat)
        assertThat(WhityRules.outfitOn(s, java.time.LocalDate.of(2026, 12, 25))).isEqualTo(WhityOutfit.SantaHat)
        assertThat(WhityRules.outfitOn(WhityPetState(), java.time.LocalDate.of(2027, 3, 23))).isEqualTo(WhityOutfit.Holi)
        assertThat(WhityRules.outfitOn(WhityPetState(), ordinary)).isNull()
    }

    @Test
    fun `treats take turns and festivals bring their own`() {
        val ordinary = java.time.LocalDate.of(2026, 11, 12)
        val week = (0L..3L).map { WhityTreat.on(ordinary.plusDays(it)) }
        assertThat(week).containsExactly(WhityTreat.Cookie, WhityTreat.Momo, WhityTreat.IceCream, WhityTreat.Bone)
        assertThat(WhityTreat.on(java.time.LocalDate.of(2027, 10, 29))).isEqualTo(WhityTreat.Laddoo)
        assertThat(WhityTreat.on(java.time.LocalDate.of(2027, 3, 23))).isEqualTo(WhityTreat.Gujiya)
        assertThat(WhityTreat.on(java.time.LocalDate.of(2027, 1, 1))).isEqualTo(WhityTreat.Cupcake)
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
