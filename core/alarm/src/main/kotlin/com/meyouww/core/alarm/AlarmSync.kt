package com.meyouww.core.alarm

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meyouww.core.data.alarm.AlarmRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.model.AlarmInfo
import com.meyouww.core.model.parseInstant
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Server alarm → what this phone stores. Only accepted alarms for me are rung. */
object AlarmPlanner {
    fun toLocal(info: AlarmInfo): LocalAlarm? {
        if (!info.accepted || !info.toMe) return null
        val first = parseInstant(info.ringAt) ?: return null
        return LocalAlarm(
            id = info.id,
            title = info.title,
            message = info.message,
            fromName = if (info.fromMe) null else info.sender.name.ifBlank { null },
            firstRingMs = first.toEpochMilli(),
            localTime = info.localTime,
            repeatDays = info.repeatDays,
            snoozeMinutes = info.snoozeMinutes,
            vibrate = info.vibrate,
            dismiss = info.dismiss,
        )
    }
}

/** Brings this phone's alarms in line with the server, then arms them. */
@HiltWorker
class AlarmSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val alarms: AlarmRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val scheduler = AlarmScheduler(applicationContext)
        if (!auth.ensureFreshSession()) {
            scheduler.rescheduleAll()
            return Result.success()
        }
        return when (val outcome = alarms.mine()) {
            is Outcome.Success -> {
                scheduler.replaceAll(outcome.value.mapNotNull(AlarmPlanner::toLocal))
                Result.success()
            }
            is Outcome.Failure -> {
                scheduler.rescheduleAll()
                if (outcome.error == AppError.Network && runAttemptCount < 5) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AlarmSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("alarm-sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** Entry point for the rest of the app. */
@Singleton
class AlarmSync @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val scheduler: AlarmScheduler by lazy { AlarmScheduler(context) }

    fun syncNow() = AlarmSyncWorker.enqueue(context)

    /** On sign-out: this phone stops ringing the account's alarms. */
    fun clear() = scheduler.clearAll()
}
