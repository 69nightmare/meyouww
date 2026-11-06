package com.meyouww.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceRepository
import com.meyouww.core.data.device.PushTokenProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Uploads this phone's push token and delivery state. Runs after sign-in,
 * whenever FCM issues a new token, after a lost-push notice, and every 12 hours
 * (which also keeps the token fresh so FCM never treats it as stale).
 */
@HiltWorker
class DeviceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val devices: DeviceRepository,
    private val tokens: PushTokenProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success() // signed out: nothing to sync
        val token = tokens.currentToken()
        return when (val outcome = devices.register(token)) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> when {
                outcome.error == AppError.NotConfigured || outcome.error == AppError.SignedOut -> Result.success()
                runAttemptCount >= MAX_ATTEMPTS -> Result.failure()
                else -> Result.retry()
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val NOW = "device-sync-now"
        private const val PERIODIC = "device-sync-periodic"

        private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Syncs as soon as there is a network (regular work: minutes are fine for sync). */
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DeviceSyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<DeviceSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
