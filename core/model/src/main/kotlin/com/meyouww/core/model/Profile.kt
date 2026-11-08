package com.meyouww.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The signed-in person's own profile row (`public.profiles`). */
@Serializable
data class Profile(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val nickname: String? = null,
    val bio: String? = null,
    @SerialName("is_adult") val isAdult: Boolean = false,
    @SerialName("birth_year") val birthYear: Int? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    /** female, male or other; only the person sees it. */
    val gender: String? = null,
) {
    /** True once the age gate has been recorded on the server. */
    val passedAgeGate: Boolean get() = isAdult && birthYear != null

    /** True once a username and display name exist. */
    val isComplete: Boolean get() = passedAgeGate && !username.isNullOrBlank() && !displayName.isNullOrBlank()

    val greetingName: String get() = nickname?.takeIf { it.isNotBlank() } ?: displayName ?: username ?: ""
}

/** Where the app should send someone after launch. */
enum class OnboardingStep {
    WELCOME,
    SIGN_IN,
    AGE_GATE,
    PROFILE,
    DONE;

    companion object {
        fun from(signedIn: Boolean, profile: Profile?): OnboardingStep = when {
            !signedIn -> WELCOME
            profile == null || !profile.passedAgeGate -> AGE_GATE
            !profile.isComplete -> PROFILE
            else -> DONE
        }
    }
}
