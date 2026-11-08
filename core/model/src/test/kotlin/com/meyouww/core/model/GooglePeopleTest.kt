package com.meyouww.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class GooglePeopleTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun readsTheAccountBirthdayAndGender() {
        val body = """
            {"resourceName":"people/1",
             "birthdays":[{"metadata":{"source":{"type":"PROFILE"}},"date":{"month":3,"day":9}},
                          {"metadata":{"primary":true,"source":{"type":"ACCOUNT"}},"date":{"year":1998,"month":3,"day":9}}],
             "genders":[{"value":"female","formattedValue":"Female"}]}
        """.trimIndent()
        val d = GooglePeople.parse(body)
        assertThat(d.birthday).isEqualTo(LocalDate.of(1998, 3, 9))
        assertThat(d.gender).isEqualTo("female")
        assertThat(GooglePeople.age(d, today)).isEqualTo(GooglePeople.Age.Adult)
    }

    @Test
    fun customAndHiddenGenders() {
        assertThat(GooglePeople.genderOf("male")).isEqualTo("male")
        assertThat(GooglePeople.genderOf("unspecified")).isNull()
        assertThat(GooglePeople.genderOf("non-binary")).isEqualTo("other")
        assertThat(GooglePeople.genderOf(null)).isNull()
    }

    @Test
    fun exactBirthdayDecidesTheEighteenthYear() {
        assertThat(GooglePeople.age(GoogleDetails(LocalDate.of(2008, 11, 8), 2008), today)).isEqualTo(GooglePeople.Age.Adult)
        assertThat(GooglePeople.age(GoogleDetails(LocalDate.of(2008, 11, 9), 2008), today)).isEqualTo(GooglePeople.Age.Underage)
    }

    @Test
    fun yearOnlyIsUnknownOnTheBoundary() {
        assertThat(GooglePeople.age(GoogleDetails(birthYear = 2008), today)).isEqualTo(GooglePeople.Age.Unknown)
        assertThat(GooglePeople.age(GoogleDetails(birthYear = 2007), today)).isEqualTo(GooglePeople.Age.Adult)
        assertThat(GooglePeople.age(GoogleDetails(birthYear = 2012), today)).isEqualTo(GooglePeople.Age.Underage)
    }

    @Test
    fun nothingSharedOrBrokenReply() {
        assertThat(GooglePeople.parse("{}")).isEqualTo(GoogleDetails())
        assertThat(GooglePeople.parse("not json")).isEqualTo(GoogleDetails())
        assertThat(GooglePeople.age(GoogleDetails(), today)).isEqualTo(GooglePeople.Age.Unknown)
    }
}
