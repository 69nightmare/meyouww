package com.meyouww.core.notifications.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
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
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.model.ActivityKind
import com.meyouww.core.model.ChangeResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Runs right after an inbox push: fetches who it is from and swaps the generic
 * notification for a named one with Accept / Not now buttons where they apply.
 */
@HiltWorker
class ActivityWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val activities: ActivityRepository,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ACTIVITY_ID) ?: return Result.failure()
        if (!auth.ensureFreshSession()) return Result.success()
        return when (val outcome = activities.get(id)) {
            is Outcome.Success -> {
                val item = outcome.value
                // Gone means it was taken back (a cancelled request) or dismissed elsewhere.
                if (item == null) notifications.cancelActivity(id) else notifications.showActivityDetails(item)
                activities.refreshUnread()
                Result.success()
            }
            is Outcome.Failure -> if (outcome.error == AppError.Network && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_ACTIVITY_ID = "activity_id"
        private const val MAX_ATTEMPTS = 5
        private const val FOREGROUND_ID = 7002

        fun enqueue(context: Context, activityId: String) {
            val request = OneTimeWorkRequestBuilder<ActivityWorker>()
                .setInputData(workDataOf(KEY_ACTIVITY_ID to activityId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("activity-$activityId", ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Accept / Not now pressed on a notification: answered in the background, app stays closed. */
class ActivityActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activityId = intent.getStringExtra(EXTRA_ACTIVITY_ID) ?: return
        val subjectId = intent.getStringExtra(EXTRA_SUBJECT_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val accept = when (intent.action) {
            ACTION_ACCEPT -> true
            ACTION_DECLINE -> false
            else -> return
        }
        // The button disappears straight away; the answer is sent by WorkManager.
        NotificationManagerCompat.from(context).cancel("activity:$activityId", 1)
        ActivityActionWorker.enqueue(context, activityId, subjectId, kind, accept)
    }

    companion object {
        const val ACTION_ACCEPT = "com.meyouww.action.ACTIVITY_ACCEPT"
        const val ACTION_DECLINE = "com.meyouww.action.ACTIVITY_DECLINE"
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_SUBJECT_ID = "subject_id"
        const val EXTRA_KIND = "kind"
    }
}

@HiltWorker
class ActivityActionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val friends: FriendsRepository,
    private val activities: ActivityRepository,
    private val wallpapers: WallpaperRepository,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val activityId = inputData.getString(KEY_ACTIVITY_ID) ?: return Result.failure()
        if (ActivityKind.fromWire(inputData.getString(KEY_KIND)) == ActivityKind.WallpaperRequest) {
            return answerWallpaper(activityId)
        }
        val subjectId = inputData.getString(KEY_SUBJECT_ID) ?: return Result.failure()
        val accept = inputData.getBoolean(KEY_ACCEPT, false)
        if (!auth.ensureFreshSession()) return Result.success()
        val outcome = when (ActivityKind.fromWire(inputData.getString(KEY_KIND))) {
            ActivityKind.FriendRequest -> friends.respondRequest(subjectId, accept)
            ActivityKind.RelationshipInvite -> friends.respondInvite(subjectId, accept)
            else -> return Result.failure()
        }
        return when (outcome) {
            is Outcome.Success -> {
                val resolution = when (outcome.value) {
                    ChangeResult.Accepted -> "accepted"
                    ChangeResult.Declined -> "declined"
                    else -> "expired"
                }
                activities.markResolved(subjectId, resolution)
                activities.markRead(activityId)
                Result.success()
            }
            is Outcome.Failure -> if (outcome.error == AppError.Network && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifications.showActionFailed(activityId)
                Result.success()
            }
        }
    }

    /** Apply → the wallpaper worker takes over; Not now → tell the server (and the sender, if receipts are on). */
    private suspend fun answerWallpaper(activityId: String): Result {
        val commandId = inputData.getString(KEY_SUBJECT_ID) ?: return Result.failure()
        if (inputData.getBoolean(KEY_ACCEPT, false)) {
            WallpaperWorker.enqueue(applicationContext, commandId, approved = true)
            activities.markRead(activityId)
            return Result.success()
        }
        if (!auth.ensureFreshSession()) return Result.success()
        return when (wallpapers.report(commandId, "declined")) {
            is Outcome.Success -> {
                activities.markResolved(commandId, "declined")
                activities.markRead(activityId)
                Result.success()
            }
            is Outcome.Failure -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_ACTIVITY_ID = "activity_id"
        private const val KEY_SUBJECT_ID = "subject_id"
        private const val KEY_KIND = "kind"
        private const val KEY_ACCEPT = "accept"
        private const val MAX_ATTEMPTS = 6
        private const val FOREGROUND_ID = 7003

        fun enqueue(context: Context, activityId: String, subjectId: String, kind: String, accept: Boolean) {
            val request = OneTimeWorkRequestBuilder<ActivityActionWorker>()
                .setInputData(
                    workDataOf(KEY_ACTIVITY_ID to activityId, KEY_SUBJECT_ID to subjectId, KEY_KIND to kind, KEY_ACCEPT to accept),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("activity-answer-$subjectId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
