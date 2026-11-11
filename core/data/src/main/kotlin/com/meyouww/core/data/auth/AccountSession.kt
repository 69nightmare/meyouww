package com.meyouww.core.data.auth

import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.friends.FriendsRepository
import android.content.Context
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceRepository
import com.meyouww.core.data.device.PushTokenProvider
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.data.prefs.NicknameBook
import com.meyouww.core.data.profile.AgeGateRecord
import com.meyouww.core.data.profile.ProfileRepository
import com.meyouww.core.data.work.DeviceSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Everything that has to happen together when someone signs in or out. */
@Singleton
class AccountSession @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val profiles: ProfileRepository,
    private val devices: DeviceRepository,
    private val tokens: PushTokenProvider,
    private val prefs: AppPreferences,
    private val friends: FriendsRepository,
    private val activities: ActivityRepository,
) {
    /**
     * Right after the code is verified: record the age-gate answers given
     * before sign-in, then register this phone for push.
     */
    suspend fun afterSignIn(): Outcome<AgeGateRecord?> {
        if (prefs.claimDevicePending()) claimThisPhone()
        DeviceSyncWorker.enqueueNow(context)
        DeviceSyncWorker.schedulePeriodic(context)
        // Gender from Google, if they shared it (best effort; kept for next time on failure).
        prefs.pendingGender()?.let { gender ->
            if ((profiles.setGender(gender) as? Outcome.Success)?.value == true) prefs.clearPendingGender()
        }
        val pending = prefs.pendingAgeGate() ?: return Outcome.Success(null)
        val result = profiles.recordAgeGate(pending.birthYear, pending.confirmedAdult)
        if (result is Outcome.Success && result.value == AgeGateRecord.Ok) prefs.clearPendingAgeGate()
        return result
    }

    /**
     * One phone at a time: register this phone now, retire the account's other phones and
     * end their sessions. Kept pending (tried again on the next start) if offline.
     */
    private suspend fun claimThisPhone() {
        devices.register(runCatching { tokens.currentToken() }.getOrNull())
        val claimed = (devices.claimOnly() as? Outcome.Success)?.value == true
        if (claimed) {
            auth.signOutOthers()
            prefs.setClaimDevicePending(false)
        }
    }

    /** False when the account signed in on another phone since (true when unsure, e.g. offline). */
    suspend fun stillThisPhone(): Boolean {
        if (prefs.claimDevicePending()) return true
        return (devices.stillThisPhone() as? Outcome.Success)?.value ?: true
    }

    /** Another phone took over: sign out here only, and say why on the sign-in screen. */
    suspend fun signOutReplaced() {
        prefs.setReplacedElsewhere(true)
        runCatching { tokens.deleteToken() }
        DeviceSyncWorker.cancelAll(context)
        auth.signOutHere()
        profiles.clear()
        friends.clear()
        activities.clear()
        prefs.clearAccountData()
        NicknameBook.clear(context)
    }

    /**
     * Unregisters this phone (best effort), deletes the push token, signs out
     * and clears account data. Local sign-out happens even when offline.
     */
    suspend fun signOut() {
        devices.unregister()
        runCatching { tokens.deleteToken() }
        DeviceSyncWorker.cancelAll(context)
        auth.signOut()
        profiles.clear()
        friends.clear()
        activities.clear()
        prefs.clearAccountData()
        NicknameBook.clear(context)
    }
}
