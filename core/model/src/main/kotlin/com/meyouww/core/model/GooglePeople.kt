package com.meyouww.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalDate
import java.time.Period

/** What Google shares about the person, with their consent, at sign-in. */
data class GoogleDetails(
    /** Full date when Google has it; otherwise only [birthYear] may be known. */
    val birthday: LocalDate? = null,
    val birthYear: Int? = null,
    /** female, male or other; null when not shared. */
    val gender: String? = null,
)

/** Reads Google's People API reply (`people/me?personFields=birthdays,genders`). */
object GooglePeople {
    const val SCOPE_BIRTHDAY = "https://www.googleapis.com/auth/user.birthday.read"
    const val SCOPE_GENDER = "https://www.googleapis.com/auth/user.gender.read"
    const val ME_URL = "https://people.googleapis.com/v1/people/me?personFields=birthdays,genders"

    /** Adult, child, or unknown (only a year that could go either way, or nothing). */
    enum class Age { Adult, Underage, Unknown }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): GoogleDetails {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return GoogleDetails()
        val birthdays = (root["birthdays"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        // The account birthday (with a year) is the one Google checks; others may be partial.
        val dated = birthdays.mapNotNull { b ->
            val date = b["date"] as? JsonObject ?: return@mapNotNull null
            val year = (date["year"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1900..2100 } ?: return@mapNotNull null
            val month = (date["month"] as? JsonPrimitive)?.intOrNull
            val day = (date["day"] as? JsonPrimitive)?.intOrNull
            val full = if (month != null && day != null) runCatching { LocalDate.of(year, month, day) }.getOrNull() else null
            val fromAccount = ((b["metadata"] as? JsonObject)?.get("source") as? JsonObject)?.get("type")
                ?.let { (it as? JsonPrimitive)?.content } == "ACCOUNT"
            Triple(year, full, fromAccount)
        }
        val best = dated.sortedWith(compareByDescending<Triple<Int, LocalDate?, Boolean>> { it.third }.thenByDescending { it.second != null })
            .firstOrNull()
        val genderValue = (root["genders"] as? JsonArray).orEmpty()
            .mapNotNull { ((it as? JsonObject)?.get("value") as? JsonPrimitive)?.content }
            .firstOrNull()
        return GoogleDetails(birthday = best?.second, birthYear = best?.first, gender = genderOf(genderValue))
    }

    fun genderOf(value: String?): String? = when (value?.trim()?.lowercase()) {
        null, "", "unspecified" -> null
        "female" -> "female"
        "male" -> "male"
        else -> "other"
    }

    fun age(details: GoogleDetails, today: LocalDate): Age {
        details.birthday?.let { b ->
            if (b.isAfter(today)) return Age.Unknown
            return if (Period.between(b, today).years >= AgeGate.MINIMUM_AGE) Age.Adult else Age.Underage
        }
        val year = details.birthYear ?: return Age.Unknown
        val diff = today.year - year
        return when {
            diff > AgeGate.MINIMUM_AGE -> Age.Adult
            diff < AgeGate.MINIMUM_AGE -> Age.Underage
            else -> Age.Unknown // turns 18 this year: the person confirms by hand
        }
    }
}
