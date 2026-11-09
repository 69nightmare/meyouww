package com.meyouww.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefs by preferencesDataStore(name = "meyouww_prefs")

/** Answers given on the age-gate screen before the account exists. */
data class PendingAgeGate(val birthYear: Int, val confirmedAdult: Boolean, val acceptedTermsAtMs: Long)

/** The most recent closed-app delivery self-test. */
data class DeliveryTestRecord(
    val testId: String,
    val requestedAtMs: Long,
    val arrivedAtMs: Long?,
    val latencyMs: Long?,
)

/** Small, non-secret app settings. */
@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val installationId = stringPreferencesKey("installation_id")
        val onboardingSeen = booleanPreferencesKey("onboarding_seen")
        val pendingBirthYear = intPreferencesKey("pending_birth_year")
        val pendingAdult = booleanPreferencesKey("pending_confirmed_adult")
        val pendingTermsAt = longPreferencesKey("pending_terms_at")
        val pendingGender = stringPreferencesKey("pending_gender")
        // v2: Do Not Disturb access was added, so everyone sees "Allow all" once more.
        val permissionsAsked = booleanPreferencesKey("permissions_asked_v2")
        val clockOffset = longPreferencesKey("server_clock_offset_ms")
        val testId = stringPreferencesKey("delivery_test_id")
        val testRequestedAt = longPreferencesKey("delivery_test_requested_at")
        val testArrivedAt = longPreferencesKey("delivery_test_arrived_at")
        val testLatency = longPreferencesKey("delivery_test_latency_ms")
        val autostartConfirmed = booleanPreferencesKey("autostart_confirmed")
        val notificationExplainerShown = booleanPreferencesKey("notification_explainer_shown")
        val autoApplyWarningAccepted = booleanPreferencesKey("auto_apply_warning_accepted")
        val shareModel = booleanPreferencesKey("share_phone_model")
        val usesLiveWallpaper = booleanPreferencesKey("uses_live_wallpaper")
        val lockRotates = booleanPreferencesKey("lock_screen_rotates")
        val profileHash = stringPreferencesKey("profile_hash")
        val profileUploadedAt = longPreferencesKey("profile_uploaded_at")
        val disappearingChats = booleanPreferencesKey("disappearing_chats")
        val claimDevice = booleanPreferencesKey("claim_device_pending")
        val replacedElsewhere = booleanPreferencesKey("signed_out_elsewhere")
    }

    private val data: Flow<Preferences> get() = context.appPrefs.data

    /** A random ID for this installation. Not derived from any hardware identifier. */
    suspend fun installationId(): String {
        data.first()[Keys.installationId]?.let { return it }
        val fresh = UUID.randomUUID().toString()
        var result = fresh
        context.appPrefs.edit { prefs ->
            val existing = prefs[Keys.installationId]
            if (existing == null) prefs[Keys.installationId] = fresh else result = existing
        }
        return result
    }

    val onboardingSeen: Flow<Boolean> = data.map { it[Keys.onboardingSeen] ?: false }

    suspend fun setOnboardingSeen() {
        context.appPrefs.edit { it[Keys.onboardingSeen] = true }
    }

    suspend fun savePendingAgeGate(answers: PendingAgeGate) {
        context.appPrefs.edit {
            it[Keys.pendingBirthYear] = answers.birthYear
            it[Keys.pendingAdult] = answers.confirmedAdult
            it[Keys.pendingTermsAt] = answers.acceptedTermsAtMs
        }
    }

    suspend fun pendingAgeGate(): PendingAgeGate? {
        val prefs = data.first()
        val year = prefs[Keys.pendingBirthYear] ?: return null
        return PendingAgeGate(year, prefs[Keys.pendingAdult] ?: false, prefs[Keys.pendingTermsAt] ?: 0L)
    }

    /** Gender Google shared before the account existed; sent right after sign-in. */
    suspend fun savePendingGender(gender: String) {
        context.appPrefs.edit { it[Keys.pendingGender] = gender }
    }

    suspend fun pendingGender(): String? = data.first()[Keys.pendingGender]

    suspend fun clearPendingGender() {
        context.appPrefs.edit { it.remove(Keys.pendingGender) }
    }

    /** Set by a fresh sign-in: this phone becomes the only one (the others are signed out). */
    suspend fun claimDevicePending(): Boolean = data.first()[Keys.claimDevice] ?: false

    suspend fun setClaimDevicePending(pending: Boolean) {
        context.appPrefs.edit { if (pending) it[Keys.claimDevice] = true else it.remove(Keys.claimDevice) }
    }

    /** This phone was signed out because the account signed in on another phone. */
    val replacedElsewhere: Flow<Boolean> get() = data.map { it[Keys.replacedElsewhere] ?: false }

    suspend fun setReplacedElsewhere(replaced: Boolean) {
        context.appPrefs.edit { if (replaced) it[Keys.replacedElsewhere] = true else it.remove(Keys.replacedElsewhere) }
    }

    /** True once the first-launch "allow everything" step has been shown on this phone. */
    val permissionsAsked: Flow<Boolean> get() = data.map { it[Keys.permissionsAsked] ?: false }

    suspend fun setPermissionsAsked() {
        context.appPrefs.edit { it[Keys.permissionsAsked] = true }
    }

    suspend fun clearPendingAgeGate() {
        context.appPrefs.edit {
            it.remove(Keys.pendingBirthYear)
            it.remove(Keys.pendingAdult)
            it.remove(Keys.pendingTermsAt)
            it.remove(Keys.pendingGender)
            it.remove(Keys.profileHash)
            it.remove(Keys.profileUploadedAt)
        }
    }

    /** serverTime - deviceTime, measured when the last self-test started. */
    suspend fun clockOffsetMs(): Long = data.first()[Keys.clockOffset] ?: 0L

    suspend fun startDeliveryTest(testId: String, requestedAtMs: Long, clockOffsetMs: Long) {
        context.appPrefs.edit {
            it[Keys.testId] = testId
            it[Keys.testRequestedAt] = requestedAtMs
            it[Keys.clockOffset] = clockOffsetMs
            it.remove(Keys.testArrivedAt)
            it.remove(Keys.testLatency)
        }
    }

    suspend fun recordDeliveryArrival(testId: String, arrivedAtMs: Long, latencyMs: Long) {
        context.appPrefs.edit {
            if (it[Keys.testId] == testId) {
                it[Keys.testArrivedAt] = arrivedAtMs
                it[Keys.testLatency] = latencyMs
            }
        }
    }

    val lastDeliveryTest: Flow<DeliveryTestRecord?> = data.map { prefs ->
        val id = prefs[Keys.testId] ?: return@map null
        DeliveryTestRecord(
            testId = id,
            requestedAtMs = prefs[Keys.testRequestedAt] ?: 0L,
            arrivedAtMs = prefs[Keys.testArrivedAt],
            latencyMs = prefs[Keys.testLatency],
        )
    }

    val autostartConfirmed: Flow<Boolean> = data.map { it[Keys.autostartConfirmed] ?: false }

    suspend fun setAutostartConfirmed(confirmed: Boolean) {
        context.appPrefs.edit { it[Keys.autostartConfirmed] = confirmed }
    }

    /** Whity's "why notifications" explainer is shown once, after the first friend. */
    val notificationExplainerShown: Flow<Boolean> = data.map { it[Keys.notificationExplainerShown] ?: false }

    suspend fun setNotificationExplainerShown() {
        context.appPrefs.edit { it[Keys.notificationExplainerShown] = true }
    }

    /** The "automatic wallpapers" explanation (live wallpapers, undo) was read and accepted. */
    val autoApplyWarningAccepted: Flow<Boolean> = data.map { it[Keys.autoApplyWarningAccepted] ?: false }

    suspend fun setAutoApplyWarningAccepted() {
        context.appPrefs.edit { it[Keys.autoApplyWarningAccepted] = true }
    }

    /** Share maker and model with friends who can see my screen specs (off by default). */
    /**
     * Off (the default since v0.13): chats stay on this phone until deleted. On: seen
     * messages vanish when the chat is left, unless saved (the v0.9 Snapchat style).
     */
    val disappearingChats: Flow<Boolean> = data.map { it[Keys.disappearingChats] ?: false }

    suspend fun setDisappearingChats(on: Boolean) {
        context.appPrefs.edit { it[Keys.disappearingChats] = on }
    }

    val shareModel: Flow<Boolean> = data.map { it[Keys.shareModel] ?: false }

    /** Self-reported: a live wallpaper is in use (a picture would replace it). */
    val usesLiveWallpaper: Flow<Boolean> = data.map { it[Keys.usesLiveWallpaper] ?: false }

    /** Self-reported: the phone maker's lock screen rotates its own pictures. */
    val lockRotates: Flow<Boolean> = data.map { it[Keys.lockRotates] ?: false }

    suspend fun setPhoneAnswers(shareModel: Boolean? = null, liveWallpaper: Boolean? = null, lockRotates: Boolean? = null) {
        context.appPrefs.edit {
            shareModel?.let { v -> it[Keys.shareModel] = v }
            liveWallpaper?.let { v -> it[Keys.usesLiveWallpaper] = v }
            lockRotates?.let { v -> it[Keys.lockRotates] = v }
        }
    }

    val profileHash: Flow<String?> = data.map { it[Keys.profileHash] }
    val profileUploadedAt: Flow<Long> = data.map { it[Keys.profileUploadedAt] ?: 0L }

    suspend fun setProfileUploaded(hash: String, at: Long) {
        context.appPrefs.edit {
            it[Keys.profileHash] = hash
            it[Keys.profileUploadedAt] = at
        }
    }

    /** Wipes everything tied to the signed-in account (keeps the installation ID). */
    suspend fun clearAccountData() {
        context.appPrefs.edit {
            it.remove(Keys.testId)
            it.remove(Keys.testRequestedAt)
            it.remove(Keys.testArrivedAt)
            it.remove(Keys.testLatency)
            it.remove(Keys.pendingBirthYear)
            it.remove(Keys.pendingAdult)
            it.remove(Keys.pendingTermsAt)
            it.remove(Keys.profileHash)
            it.remove(Keys.profileUploadedAt)
        }
    }
}
