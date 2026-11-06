package com.meyouww.core.model

/**
 * meyouww is for adults only (see docs/DECISIONS.md). We store just the birth
 * year and the fact that the person confirmed they are 18 or older.
 *
 * Someone whose birth year is exactly 18 years ago may still be 17, which is
 * why the explicit "I am 18 or older" confirmation is required as well.
 */
object AgeGate {
    const val MINIMUM_AGE = 18
    private const val MAX_AGE = 120

    fun evaluate(birthYear: Int?, confirmedAdult: Boolean, currentYear: Int): Result = when {
        birthYear == null || birthYear > currentYear || birthYear < currentYear - MAX_AGE -> Result.InvalidYear
        currentYear - birthYear < MINIMUM_AGE -> Result.Underage
        !confirmedAdult -> Result.NotConfirmed
        else -> Result.Eligible
    }

    /** The latest birth year that can pass the gate this year. */
    fun latestEligibleBirthYear(currentYear: Int): Int = currentYear - MINIMUM_AGE

    sealed interface Result {
        data object Eligible : Result
        data object Underage : Result
        data object NotConfirmed : Result
        data object InvalidYear : Result
    }
}
