package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import com.meyouww.core.model.UserIdCheck.Reason
import org.junit.Test

class UserIdRulesTest {

    private fun reasonOf(raw: String) = (UserIdRules.check(raw) as UserIdCheck.Invalid).reason

    @Test
    fun `accepts the documented examples`() {
        for (id in listOf("littlecloud482", "doodle.heart", "peachybun21", "a_b", "abc")) {
            assertThat(UserIdRules.check(id)).isEqualTo(UserIdCheck.Valid(id))
        }
    }

    @Test
    fun `normalizes case, whitespace and a leading at-sign`() {
        assertThat(UserIdRules.check("  @Doodle.Heart ")).isEqualTo(UserIdCheck.Valid("doodle.heart"))
    }

    @Test
    fun `enforces length limits`() {
        assertThat(reasonOf("ab")).isEqualTo(Reason.TOO_SHORT)
        assertThat(UserIdRules.check("a".repeat(20))).isInstanceOf(UserIdCheck.Valid::class.java)
        assertThat(reasonOf("a".repeat(21))).isEqualTo(Reason.TOO_LONG)
    }

    @Test
    fun `must start with a letter`() {
        assertThat(reasonOf("1abc")).isEqualTo(Reason.MUST_START_WITH_LETTER)
        assertThat(reasonOf("_abc")).isEqualTo(Reason.MUST_START_WITH_LETTER)
        assertThat(reasonOf(".abc")).isEqualTo(Reason.MUST_START_WITH_LETTER)
    }

    @Test
    fun `rejects characters outside a-z 0-9 dot underscore`() {
        assertThat(reasonOf("ab-c")).isEqualTo(Reason.INVALID_CHARACTER)
        assertThat(reasonOf("ab c")).isEqualTo(Reason.INVALID_CHARACTER)
        assertThat(reasonOf("abç")).isEqualTo(Reason.INVALID_CHARACTER)
        assertThat(reasonOf("ab💗")).isEqualTo(Reason.INVALID_CHARACTER)
    }

    @Test
    fun `rejects consecutive and trailing dots`() {
        assertThat(reasonOf("ab..c")).isEqualTo(Reason.CONSECUTIVE_DOTS)
        assertThat(reasonOf("abc.")).isEqualTo(Reason.TRAILING_DOT)
        assertThat(UserIdRules.check("a.b.c")).isInstanceOf(UserIdCheck.Valid::class.java)
    }

    @Test
    fun `blocks reserved words regardless of case`() {
        assertThat(reasonOf("Admin")).isEqualTo(Reason.RESERVED)
        assertThat(reasonOf("meyouww")).isEqualTo(Reason.RESERVED)
        assertThat(reasonOf("whity")).isEqualTo(Reason.RESERVED)
    }
}
