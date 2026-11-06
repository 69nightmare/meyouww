package com.meyouww.core.notifications.push

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.device.DeviceRepository
import com.meyouww.core.data.prefs.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Finishes the self-test after the notification is already showing: works out
 * the latency, updates the notification and tells the server.
 */
@HiltWorker
class DeliveryTestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences,
    private val auth: AuthRepository,
    private val devices: DeviceRepository,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val testId = inputData.getString(KEY_TEST_ID) ?: return Result.failure()
        val sentAt = inputData.getLong(KEY_SENT_AT, 0L)
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, 0L)
        val latency = PushHandler.latencyMs(receivedAt, prefs.clockOffsetMs(), sentAt)

        if (runAttemptCount == 0) {
            prefs.recordDeliveryArrival(testId, receivedAt, latency)
            notifications.showDeliveryTestLatency(latency)
        }

        if (!auth.ensureFreshSession()) return Result.success()
        return when (val outcome = devices.reportDeliveryArrival(testId, latency)) {
            is Outcome.Success -> Result.success()
            is Outcome.Failure -> if (outcome.error == AppError.Network && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_TEST_ID = "test_id"
        private const val KEY_SENT_AT = "sent_at"
        private const val KEY_RECEIVED_AT = "received_at"
        private const val MAX_ATTEMPTS = 5
        private const val FOREGROUND_ID = 7001

        fun enqueue(context: Context, testId: String, sentAtServerMs: Long, receivedAtMs: Long) {
            val request = OneTimeWorkRequestBuilder<DeliveryTestWorker>()
                .setInputData(
                    workDataOf(KEY_TEST_ID to testId, KEY_SENT_AT to sentAtServerMs, KEY_RECEIVED_AT to receivedAtMs),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .build()
            // Unique per test: a duplicated push never processes twice.
            WorkManager.getInstance(context).enqueueUniqueWork("delivery-test-$testId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
