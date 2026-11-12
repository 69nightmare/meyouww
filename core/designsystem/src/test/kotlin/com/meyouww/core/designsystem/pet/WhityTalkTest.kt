package com.meyouww.core.designsystem.pet

import com.google.common.truth.Truth.assertThat
import com.meyouww.core.model.IndianHolidays
import org.junit.Test
import java.time.LocalDate

class WhityTalkTest {
    private val minute = 60_000L
    private val now = 1_800_000_000_000L
    private val ordinary = LocalDate.of(2026, 11, 12)
    private val diwali = LocalDate.of(2027, 10, 29)

    @Test
    fun `she chats by herself at most every half hour and a few times a day`() {
        val s = WhityPetState(talkedAt = now - 29 * minute)
        assertThat(WhityTalk.mayTalk(s, now, ordinary)).isFalse()
        assertThat(WhityTalk.mayTalk(s.copy(talkedAt = now - 30 * minute), now, ordinary)).isTrue()
        assertThat(WhityTalk.mayTalk(s.copy(talkedAt = 0L, talksToday = WhityTalk.TALKS_PER_DAY), now, ordinary)).isFalse()
        // A clock that went back doesn't keep her quiet.
        assertThat(WhityTalk.mayTalk(s.copy(talkedAt = now + 60 * minute), now, ordinary)).isTrue()
    }

    @Test
    fun `news and festivals only wait a minute`() {
        val s = WhityPetState(talkedAt = now - 2 * minute, talksToday = 20)
        assertThat(WhityTalk.mayTalk(s, now, ordinary)).isFalse()
        assertThat(WhityTalk.mayTalk(s.copy(news = WhityNews.EarnedDoodle), now, ordinary)).isTrue()
        assertThat(WhityTalk.mayTalk(s, now, diwali)).isTrue()
        assertThat(WhityTalk.mayTalk(s.copy(greetedDay = diwali.toEpochDay()), now, diwali)).isFalse()
        assertThat(WhityTalk.mayTalk(s.copy(news = WhityNews.EarnedDoodle, talkedAt = now - 30_000L), now, ordinary)).isFalse()
    }

    @Test
    fun `she stays quiet when hidden or told not to pop in`() {
        val s = WhityPetState(news = WhityNews.EarnedMessage)
        assertThat(WhityTalk.mayTalk(s.copy(shown = false), now, ordinary)).isFalse()
        assertThat(WhityTalk.mayTalk(s.copy(roams = false), now, ordinary)).isFalse()
    }

    @Test
    fun `news comes first and is told once`() {
        val (line, after) = WhityTalk.pick(WhityPetState(news = WhityNews.EarnedDoodle), now, diwali, 12, next = "x")
        assertThat(line).isEqualTo(WhityLine.Earned(WhityEarn.Doodle))
        assertThat(after.news).isNull()
        assertThat(after.talkedAt).isEqualTo(now)
        assertThat(after.talksToday).isEqualTo(1)
        // Then the festival wish, once that day.
        val (wish, wished) = WhityTalk.pick(after, now, diwali, 12, next = null)
        assertThat(wish).isEqualTo(WhityLine.Festival("Happy Diwali! 🪔"))
        assertThat(wished.greetedDay).isEqualTo(diwali.toEpochDay())
        assertThat(WhityTalk.pick(wished, now, diwali, 12, next = null).first).isNotInstanceOf(WhityLine.Festival::class.java)
    }

    @Test
    fun `a new outfit is announced with its streak`() {
        val s = WhityPetState(bestStreak = 7, streak = 7, news = WhityNews.Unlocked)
        assertThat(WhityTalk.pick(s, now, ordinary, 12, null).first).isEqualTo(WhityLine.Unlocked(7, WhityOutfit.FlowerCrown))
    }

    @Test
    fun `what is coming up takes every other turn and facts take turns too`() {
        var s = WhityPetState(talksToday = 0)
        val lines = mutableListOf<WhityLine>()
        repeat(6) {
            val (line, after) = WhityTalk.pick(s, now, ordinary, 13, next = "🍿 Movie night is tomorrow!")
            lines += line
            s = after
        }
        assertThat(lines[0]).isEqualTo(WhityLine.Next("🍿 Movie night is tomorrow!"))
        assertThat(lines[2]).isEqualTo(WhityLine.Next("🍿 Movie night is tomorrow!"))
        assertThat(lines[4]).isEqualTo(WhityLine.Next("🍿 Movie night is tomorrow!"))
        // Nothing about you yet at 1 pm on a first day: the odd turns are facts, each a new one.
        assertThat(lines[1]).isEqualTo(WhityLine.Fact(0))
        assertThat(lines[3]).isEqualTo(WhityLine.Fact(1))
        assertThat(s.factIndex).isEqualTo(3)
    }

    @Test
    fun `without anything coming up she mixes things about you with facts`() {
        var s = WhityPetState(streak = 5, weekDoodles = 3)
        val lines = mutableListOf<WhityLine>()
        repeat(4) {
            val (line, after) = WhityTalk.pick(s, now, ordinary, 8, next = null)
            lines += line
            s = after
        }
        assertThat(lines).containsExactly(WhityLine.Streak(5), WhityLine.Fact(0), WhityLine.Doodles(3), WhityLine.Fact(1)).inOrder()
    }

    @Test
    fun `a low mood with treats in the jar makes her hint at a snack`() {
        val (line, _) = WhityTalk.pick(WhityPetState(mood = 40, treats = 2), now, ordinary, 13, null)
        assertThat(line).isEqualTo(WhityLine.Hungry)
    }

    @Test
    fun `every festival she knows is on India's holiday list`() {
        val names = IndianHolidays.between(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31)).map { it.name }.toSet()
        assertThat(names).containsAtLeastElementsIn(WhityFestivals.known.keys)
    }

    @Test
    fun `days of mourning get no wish`() {
        assertThat(WhityFestivals.on(LocalDate.of(2026, 6, 26))).isNull() // Muharram
        assertThat(WhityFestivals.on(LocalDate.of(2026, 4, 3))).isNull() // Good Friday
        assertThat(WhityFestivals.on(LocalDate.of(2027, 11, 24))).isNull() // Guru Tegh Bahadur Martyrdom Day
    }

    @Test
    fun `a shared day picks the gazetted festival`() {
        // 15 August 2027 is Independence Day, Eid-e-Milad and Navroz.
        assertThat(WhityFestivals.on(LocalDate.of(2027, 8, 15))?.name).isEqualTo("Independence Day")
    }
}
