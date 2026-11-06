package com.meyouww.core.data.auth

import android.content.Context
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceRepository
import com.meyouww.core.data.device.PushTokenProvider
import com.meyouww.core.data.prefs.AppPreferences
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
) {
    /**
     * Right after the code is verified: record the age-gate answers given
     * before sign-in, then register this phone for push.
     */
    suspend fun afterSignIn(): Outcome<AgeGateRecord?> {
        DeviceSyncWorker.enqueueNow(context)
        DeviceSyncWorker.schedulePeriodic(context)
        val pending = prefs.pendingAgeGate() ?: return Outcome.Success(null)
        val result = profiles.recordAgeGate(pending.birthYear, pending.confirmedAdult)
        if (result is Outcome.Success && result.value == AgeGateRecord.Ok) prefs.clearPendingAgeGate()
        return result
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
        prefs.clearAccountData()
    }
}
