package com.meyouww.core.model

/**
 * Rules for public user IDs such as `@littlecloud482`.
 *
 * The database enforces the same rules (see `supabase/migrations`); this copy
 * gives instant feedback while typing. The server is always authoritative.
 */
object UserIdRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /** IDs nobody may claim. Mirrors `public.reserved_usernames` in the database. */
    val RESERVED: Set<String> = setOf(
        "admin", "administrator", "support", "help", "official", "meyouww", "meyou",
        "whity", "staff", "team", "moderator", "mod", "system", "root", "security",
        "privacy", "null", "undefined", "api", "app", "settings", "account", "login",
        "signup", "everyone", "anonymous",
    )

    private val allowed = Regex("^[a-z0-9._]+$")

    /** Lowercases, trims and drops a leading `@`. */
    fun normalize(raw: String): String = raw.trim().removePrefix("@").lowercase()

    fun check(raw: String): UserIdCheck {
        val id = normalize(raw)
        val reason = when {
            id.length < MIN_LENGTH -> UserIdCheck.Reason.TOO_SHORT
            id.length > MAX_LENGTH -> UserIdCheck.Reason.TOO_LONG
            !allowed.matches(id) -> UserIdCheck.Reason.INVALID_CHARACTER
            !id.first().isLetter() -> UserIdCheck.Reason.MUST_START_WITH_LETTER
            ".." in id -> UserIdCheck.Reason.CONSECUTIVE_DOTS
            id.endsWith('.') -> UserIdCheck.Reason.TRAILING_DOT
            id in RESERVED -> UserIdCheck.Reason.RESERVED
            else -> null
        }
        return if (reason == null) UserIdCheck.Valid(id) else UserIdCheck.Invalid(reason)
    }
}

sealed interface UserIdCheck {
    data class Valid(val normalized: String) : UserIdCheck
    data class Invalid(val reason: Reason) : UserIdCheck

    enum class Reason {
        TOO_SHORT,
        TOO_LONG,
        INVALID_CHARACTER,
        MUST_START_WITH_LETTER,
        CONSECUTIVE_DOTS,
        TRAILING_DOT,
        RESERVED,
    }
}
