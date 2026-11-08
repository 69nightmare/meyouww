package com.meyouww.core.notifications.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.wallpaper.WallpaperApplier
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.database.WallpaperDao
import com.meyouww.core.model.SendOutcome
import com.meyouww.core.model.WallpaperTarget
import com.meyouww.core.notifications.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

/**
 * The fallback for [WallpaperDelivery]: when the push couldn't finish the job in its
 * few seconds (no network yet, a slow download), this retries with backoff.
 */
@HiltWorker
class WallpaperWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val delivery: WallpaperDelivery,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val approved = inputData.getBoolean(KEY_APPROVED, false)
        // The owner may choose another screen than the sender suggested.
        val target = inputData.getString(KEY_TARGET)?.let { WallpaperTarget.fromWire(it) }
        return when (delivery.process(id, approved, target, canRetry = runAttemptCount < MAX_ATTEMPTS)) {
            WallpaperDelivery.Step.Done -> Result.success()
            WallpaperDelivery.Step.Retry -> Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_ID = "command_id"
        private const val KEY_APPROVED = "approved"
        private const val KEY_TARGET = "target"
        private const val MAX_ATTEMPTS = 6
        private const val FOREGROUND_ID = 7010

        fun enqueue(context: Context, commandId: String, approved: Boolean, target: WallpaperTarget? = null) {
            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(workDataOf(KEY_ID to commandId, KEY_APPROVED to approved, KEY_TARGET to target?.wire))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            // KEEP: a duplicate push never starts a second run. An approval replaces a finished "ask" run.
            val policy = if (approved) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            WorkManager.getInstance(context).enqueueUniqueWork("wallpaper-$commandId", policy, request)
        }
    }
}

/** Undo and Pause from the "X decorated your phone" notification, with the app closed. */
class WallpaperActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_UNDO && action != ACTION_PAUSE) return
        WallpaperActionWorker.enqueue(
            context,
            action,
            intent.getStringExtra(EXTRA_SENDER_ID).orEmpty(),
            intent.getStringExtra(EXTRA_TARGET) ?: WallpaperTarget.Lock.wire,
        )
    }

    companion object {
        const val ACTION_UNDO = "com.meyouww.action.WALLPAPER_UNDO"
        const val ACTION_PAUSE = "com.meyouww.action.WALLPAPER_PAUSE"
        const val ACTION_RESTORE_HOME_BASE = "com.meyouww.action.WALLPAPER_HOME_BASE"
        const val EXTRA_COMMAND_ID = "command_id"
        const val EXTRA_SENDER_ID = "sender_id"
        const val EXTRA_TARGET = "target"
    }
}

@HiltWorker
class WallpaperActionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val friends: FriendsRepository,
    private val dao: WallpaperDao,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val target = WallpaperTarget.fromWire(inputData.getString(KEY_TARGET))
        return when (action) {
            WallpaperActionReceiver.ACTION_UNDO -> {
                notifications.showUndoResult(undo(context, dao, target))
                Result.success()
            }
            WallpaperActionReceiver.ACTION_RESTORE_HOME_BASE -> {
                notifications.showUndoResult(restoreHomeBase(context, WallpaperTarget.Both))
                Result.success()
            }
            WallpaperActionReceiver.ACTION_PAUSE -> {
                val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()
                if (!auth.ensureFreshSession()) return Result.success()
                val until = Instant.now().plusSeconds(TimeUnit.DAYS.toSeconds(1)).toString()
                val outcome = friends.updatePermissions(
                    sender,
                    buildJsonObject {
                        put("wallpaper_mode", "paused")
                        put("paused_until", until)
                    },
                    null,
                )
                when (outcome) {
                    is Outcome.Success -> {
                        val name = friends.friends.value?.firstOrNull { it.id == sender }?.card?.name
                            ?: context.getString(R.string.activity_someone)
                        notifications.showPaused(name)
                        Result.success()
                    }
                    is Outcome.Failure -> if (runAttemptCount < 5) Result.retry() else Result.success()
                }
            }
            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_ACTION = "action"
        private const val KEY_SENDER = "sender"
        private const val KEY_TARGET = "target"
        private const val FOREGROUND_ID = 7011

        fun enqueue(context: Context, action: String, senderId: String, target: String) {
            val builder = OneTimeWorkRequestBuilder<WallpaperActionWorker>()
                .setInputData(workDataOf(KEY_ACTION to action, KEY_SENDER to senderId, KEY_TARGET to target))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            if (action == WallpaperActionReceiver.ACTION_PAUSE) {
                builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
            }
            WorkManager.getInstance(context).enqueueUniqueWork("wallpaper-action-$action", ExistingWorkPolicy.REPLACE, builder.build())
        }

        /**
         * Goes back one meyouww wallpaper for [target]. Falls back to the owner's home
         * base. Returns false when there is nothing meyouww is able to restore.
         */
        suspend fun undo(context: Context, dao: WallpaperDao, target: WallpaperTarget): Boolean {
            val history = dao.history(target.wire)
            val current = history.firstOrNull()
            val previous = history.drop(1).firstOrNull { File(WallpaperApplier.historyDir(context), it.file).isFile }
            if (previous != null) {
                val ok = runCatching {
                    WallpaperApplier.apply(context, File(WallpaperApplier.historyDir(context), previous.file).readBytes(), target)
                }.isSuccess
                if (ok && current != null) {
                    File(WallpaperApplier.historyDir(context), current.file).delete()
                    dao.deleteApplied(current.id)
                }
                return ok
            }
            return restoreHomeBase(context, target)
        }

        fun restoreHomeBase(context: Context, target: WallpaperTarget): Boolean {
            val file = WallpaperApplier.homeBase(context)
            if (!file.isFile) return false
            return runCatching { WallpaperApplier.apply(context, file.readBytes(), target) }.isSuccess
        }
    }
}

/**
 * Sends a finished wallpaper in the background: the sender can close the app
 * as soon as they tap Send. The image was saved to [KEY_FILE] first.
 */
@HiltWorker
class SendWallpaperWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val wallpapers: WallpaperRepository,
    private val gallery: WallpaperGallery,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_FILE) ?: return Result.failure()
        val recipient = inputData.getString(KEY_RECIPIENT) ?: return Result.failure()
        val name = inputData.getString(KEY_NAME).orEmpty()
        val key = File(path).nameWithoutExtension
        val file = File(path)
        if (!file.isFile) return Result.failure()
        if (!auth.ensureFreshSession()) return Result.failure()
        notifications.showSending(key, name)

        val applyLocal = inputData.getString(KEY_APPLY_LOCAL)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val outcome = wallpapers.send(
            recipientId = recipient,
            target = WallpaperTarget.fromWire(inputData.getString(KEY_TARGET)),
            bytes = file.readBytes(),
            mime = inputData.getString(KEY_MIME) ?: "image/png",
            width = inputData.getInt(KEY_WIDTH, 1080),
            height = inputData.getInt(KEY_HEIGHT, 2400),
            isDoodle = inputData.getBoolean(KEY_DOODLE, true),
            applyLocal = applyLocal,
        )
        return when (outcome) {
            is Outcome.Success -> {
                val result = outcome.value
                val commandId = result.commandId
                if (commandId != null && result.outcome != SendOutcome.Unavailable && result.outcome != SendOutcome.Invalid &&
                    result.outcome != SendOutcome.RateLimited
                ) {
                    // The sender's copy, for the chat (the server copy goes once it is delivered).
                    runCatching {
                        gallery.keep(
                            commandId, recipient, fromMe = true, bytes = file.readBytes(), extension = file.extension.ifBlank { "webp" },
                            target = WallpaperTarget.fromWire(inputData.getString(KEY_TARGET)),
                            status = if (result.outcome == SendOutcome.Sent) "sent" else "scheduled",
                        )
                    }
                }
                when (result.outcome) {
                    SendOutcome.Sent -> notifications.showSendResult(
                        key, name, R.string.wp_sent_title,
                        if (inputData.getBoolean(KEY_EXPECT_AUTO, false)) R.string.wp_sent_auto_text else R.string.wp_sent_ask_text,
                    )
                    SendOutcome.Scheduled, SendOutcome.Held -> notifications.showSendResult(
                        key,
                        applyLocal?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)) ?: name,
                        R.string.wp_scheduled_title,
                        R.string.wp_scheduled_text,
                    )
                    SendOutcome.Unavailable, SendOutcome.RateLimited ->
                        notifications.showSendResult(key, name, R.string.wp_unavailable_title, R.string.wp_unavailable_text)
                    SendOutcome.Invalid -> notifications.showSendResult(key, name, R.string.wp_send_failed_title, R.string.wp_send_failed_text)
                }
                file.delete()
                Result.success()
            }
            is Outcome.Failure -> if (outcome.error == AppError.Network && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                notifications.showSendResult(key, name, R.string.wp_send_failed_title, R.string.wp_send_failed_text)
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val KEY_FILE = "file"
        private const val KEY_RECIPIENT = "recipient"
        private const val KEY_NAME = "name"
        private const val KEY_TARGET = "target"
        private const val KEY_MIME = "mime"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DOODLE = "doodle"
        private const val KEY_APPLY_LOCAL = "apply_local"
        private const val KEY_EXPECT_AUTO = "expect_auto"
        private const val MAX_ATTEMPTS = 8
        private const val FOREGROUND_ID = 7012

        @Suppress("LongParameterList")
        fun enqueue(
            context: Context,
            file: File,
            recipientId: String,
            recipientName: String,
            target: WallpaperTarget,
            mime: String,
            width: Int,
            height: Int,
            isDoodle: Boolean,
            applyLocal: LocalDateTime?,
            expectAuto: Boolean,
        ) {
            val request = OneTimeWorkRequestBuilder<SendWallpaperWorker>()
                .setInputData(
                    workDataOf(
                        KEY_FILE to file.path,
                        KEY_RECIPIENT to recipientId,
                        KEY_NAME to recipientName,
                        KEY_TARGET to target.wire,
                        KEY_MIME to mime,
                        KEY_WIDTH to width,
                        KEY_HEIGHT to height,
                        KEY_DOODLE to isDoodle,
                        KEY_APPLY_LOCAL to applyLocal?.toString(),
                        KEY_EXPECT_AUTO to expectAuto,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("send-${file.nameWithoutExtension}", ExistingWorkPolicy.KEEP, request)
        }
    }
}
