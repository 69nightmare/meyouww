package com.meyouww.core.notifications.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meyouww.core.alarm.CalendarSyncWorker
import com.meyouww.core.chat.BackupResult
import com.meyouww.core.chat.ChatBackup
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.ChatScheduler
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.chat.DeliveryResult
import com.meyouww.core.chat.DriveAccess
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.friends.FriendsRepository
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.chat.ChatConversation
import com.meyouww.core.chat.ChatMessage
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.meyouww.core.data.diagnostics.PushDiary
import com.meyouww.core.notifications.push.AndroidPushNotifications
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Sends the encrypted outbox. Chained (APPEND_OR_REPLACE) so nothing waits behind a finished run. */
@HiltWorker
class ChatSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val chat: ChatRepository,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success()
        return when (chat.deliverOutbox()) {
            DeliveryResult.Done -> Result.success()
            DeliveryResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val MAX_ATTEMPTS = 20
        private const val FOREGROUND_ID = 7010

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatSendWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat-send", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/** Fetches and decrypts waiting messages, then shows them. */
@HiltWorker
class ChatFetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inbox: ChatInbox,
    private val notifications: AndroidPushNotifications,
    private val diary: PushDiary,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val step = inbox.fetch()
        diary.note("background job fetch: $step (attempt ${runAttemptCount + 1})")
        return when (step) {
            ChatInbox.Step.Done -> Result.success()
            ChatInbox.Step.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val MAX_ATTEMPTS = 5
        private const val FOREGROUND_ID = 7011

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatFetchWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat-fetch", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /**
         * One more fetch a few seconds from now, even if Android has frozen the app by then:
         * it clears a bare "New message" that the server's copy left after the app was done.
         */
        fun enqueueLater(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatFetchWorker>()
                .setInitialDelay(TIDY_DELAY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat-tidy", ExistingWorkPolicy.REPLACE, request)
        }

        private const val TIDY_DELAY_SECONDS = 8L
    }
}

/** Fetches, checks and decrypts photos, videos and PDFs that arrived; retries on a bad connection. */
@HiltWorker
class ChatMediaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val chat: ChatRepository,
    private val notifications: AndroidPushNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success()
        return when (chat.downloadMedia()) {
            DeliveryResult.Done -> Result.success()
            DeliveryResult.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(FOREGROUND_ID, notifications.backgroundWorkNotification())

    companion object {
        private const val MAX_ATTEMPTS = 8
        private const val FOREGROUND_ID = 7012

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatMediaWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat-media", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}

/**
 * The chat backup to Google Drive: every night (when turned on), on demand, and, after a
 * restore, to bring the photos, videos and documents down. It never asks for anything on
 * screen: if Google wants the person's OK again, it stops and Settings says so.
 */
@HiltWorker
class ChatBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val backup: ChatBackup,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success()
        val token = (DriveAccess.authorize(applicationContext, auth.currentEmail()) as? DriveAccess.Result.Token)?.token
            ?: return Result.success()
        return if (inputData.getBoolean(KEY_RESTORE_MEDIA, false)) {
            // Some may be left when time runs out: carry on in the next run.
            if (backup.restoreMedia(token) > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        } else {
            when (backup.backUp(token)) {
                BackupResult.Offline -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                else -> Result.success()
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val KEY_RESTORE_MEDIA = "restore_media"
        private const val NIGHTLY = "chat-backup-nightly"

        /** Every night at about 2 am; [wifiOnly] waits for Wi-Fi (photos and videos can be big). */
        fun scheduleNightly(context: Context, wifiOnly: Boolean) {
            val now = java.time.LocalDateTime.now()
            var next = now.toLocalDate().atTime(2, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ChatBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(java.time.Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NIGHTLY, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancelNightly(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NIGHTLY)
        }

        /** After a restore: fetch the photos, videos and documents in the background. */
        fun restoreMedia(context: Context) {
            val request = OneTimeWorkRequestBuilder<ChatBackupWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_RESTORE_MEDIA, true).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("chat-restore-media", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

@Singleton
class WorkManagerChatScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatScheduler {
    override fun send() = ChatSendWorker.enqueue(context)

    override fun fetch() = ChatFetchWorker.enqueue(context)

    override fun media() = ChatMediaWorker.enqueue(context)

    override fun calendarChanged() = CalendarSyncWorker.enqueue(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatSchedulerModule {
    @Binds
    abstract fun bindChatScheduler(impl: WorkManagerChatScheduler): ChatScheduler
}

/**
 * Reply and Mark as read from the notification. The typed text goes straight into the
 * encrypted database (never into WorkManager's plain one), then the send worker runs.
 */
@AndroidEntryPoint
class ChatActionReceiver : BroadcastReceiver() {

    @Inject lateinit var chat: ChatRepository

    @Inject lateinit var chatNotifications: ChatNotifications

    override fun onReceive(context: Context, intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: return
        val reply = if (intent.action == ACTION_REPLY) {
            RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return
        } else {
            null
        }
        if (intent.action != ACTION_REPLY && intent.action != ACTION_READ) return
        val pending = goAsync()
        scope.launch {
            try {
                if (reply != null) chat.sendText(conversationId, reply)
                chat.markRead(conversationId)
                chatNotifications.cancel(conversationId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.meyouww.action.CHAT_REPLY"
        const val ACTION_READ = "com.meyouww.action.CHAT_READ"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val KEY_TEXT = "text"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * Fetches and decrypts waiting messages, shows them, and sends the "delivered"
 * receipts straight away, so the sender's light turns yellow without waiting for
 * a background job. Runs from the push itself and, as a fallback, from [ChatFetchWorker].
 */
@Singleton
class ChatInbox @Inject constructor(
    private val auth: AuthRepository,
    private val chat: ChatRepository,
    private val visibility: ChatVisibility,
    private val chatNotifications: ChatNotifications,
    private val diary: PushDiary,
    private val avatars: AvatarRepository,
    private val friends: FriendsRepository,
) {
    enum class Step { Done, Retry }

    private val lock = Mutex()
    private val tidyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** When the last successful fetch began: generic notifications older than this are stale. */
    @Volatile private var lastFetchStartedAt = 0L

    /** A group change couldn't be fetched yet: the next fetch does it. */
    @Volatile private var groupsPending = false

    /** True once my groups (names, members) are up to date. */
    suspend fun refreshGroups(): Boolean {
        if (!auth.ensureFreshSession()) return false
        val ok = chat.refreshGroups() is Outcome.Success
        groupsPending = !ok
        return ok
    }

    private suspend fun showChat(id: String) {
        val conversation = chat.conversation(id) ?: return
        val unseen = chat.unseen(id)
        val names = if (conversation.isGroup) chat.memberNames(id) else emptyMap()
        chatNotifications.showConversation(conversation, unseen, names, photosFor(conversation, unseen))
    }

    /**
     * The profile pictures of whoever wrote these messages, round, by user ID. Pictures are kept
     * on the phone after the first time, so this is usually instant; it never waits long.
     */
    private suspend fun photosFor(conversation: ChatConversation, unseen: List<ChatMessage>): Map<String, Bitmap> =
        withTimeoutOrNull(PHOTO_BUDGET_MS) {
            val paths: Map<String, String?> = if (conversation.isGroup) {
                val wanted = unseen.map { it.senderId }.toSet()
                chat.members(conversation.id).first().filter { it.userId in wanted }.associate { it.userId to it.avatar }
            } else {
                val known = friends.friends.value ?: (friends.refreshFriends() as? Outcome.Success)?.value
                mapOf(conversation.peerId to known?.firstOrNull { it.id == conversation.peerId }?.card?.avatar)
            }
            paths.mapNotNull { (user, path) ->
                val bytes = path?.let { avatars.bytes(it) } ?: return@mapNotNull null
                round(bytes)?.let { user to it }
            }.toMap()
        }.orEmpty()

    private fun round(bytes: ByteArray): Bitmap? = runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val size = minOf(side, PHOTO_SIDE)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(square, null, Rect(0, 0, size, size), paint)
        out
    }.getOrNull()

    /**
     * Sorts out bare "New message" notifications (the app's placeholder, or the copy Android
     * shows straight from the server) that were posted before the last fetch began. Nothing
     * unread there (it was a receipt, reaction or edit), a muted chat or the open chat: gone.
     * Something unread: the real notification with names and text goes back in its place
     * (the server's copy uses the same slot, so a late one would otherwise cover it).
     */
    private suspend fun tidyGenericNotifications() {
        val before = lastFetchStartedAt
        if (before == 0L) return
        chatNotifications.genericChatNotifications(postedBefore = before).forEach { id ->
            val conversation = chat.conversation(id)
            when {
                conversation == null || conversation.muted || visibility.isOpen(id) || chat.unseen(id).isEmpty() ->
                    chatNotifications.cancel(id)
                else -> showChat(id)
            }
        }
    }

    private var tidyJob: Job? = null

    suspend fun fetch(): Step = lock.withLock {
        val step = fetchNow()
        if (step == Step.Done) {
            // The server's copy often lands a moment after this fetch began, so the tidy above
            // never saw it. Look again a few times: a bare notification that turned up since
            // gets one more fetch (a real new message is then shown properly) and is cleared
            // if there is still nothing unread behind it.
            tidyJob?.cancel()
            tidyJob = tidyScope.launch {
                for (wait in TIDY_DELAYS_MS) {
                    delay(wait)
                    if (chatNotifications.genericChatNotifications(postedBefore = Long.MAX_VALUE).isEmpty()) continue
                    lock.withLock { fetchNow() }
                }
            }
        }
        step
    }

    /** One fetch: decrypt, show, tidy, and send the receipts. Call with [lock] held. */
    private suspend fun fetchNow(): Step {
        if (!auth.ensureFreshSession()) {
            diary.note("fetch skipped: no sign-in session")
            return Step.Done
        }
        val startedAt = System.currentTimeMillis()
        if (groupsPending) refreshGroups()
        return when (val outcome = chat.receive()) {
            is Outcome.Success -> {
                lastFetchStartedAt = startedAt
                var shown = 0
                var readNow = 0
                outcome.value.map { it.conversationId }.distinct().forEach { id ->
                    val conversation = chat.conversation(id) ?: return@forEach
                    when {
                        visibility.isOpen(id) -> {
                            chat.markRead(id)
                            chatNotifications.cancel(id)
                            readNow++
                        }
                        conversation.muted -> chatNotifications.cancel(id)
                        else -> {
                            showChat(id)
                            shown++
                        }
                    }
                }
                if (outcome.value.isNotEmpty()) {
                    diary.note("fetched ${outcome.value.size} new: $shown chats notified, $readNow read (chat on screen)")
                }
                // A push that only carried a receipt, reaction or edit leaves a bare "New message"
                // behind (the placeholder, or the server's copy for phones on older versions).
                tidyGenericNotifications()
                // Receipts out now (a worker picks up anything that doesn't make it).
                runCatching { chat.deliverOutbox() }
                Step.Done
            }
            is Outcome.Failure -> {
                diary.note("fetch failed: ${outcome.error} (${chat.lastReceiveError ?: "?"})")
                // Signed out can't be retried. Anything else can: giving up here used to leave a
                // bare "New message" until the app was opened.
                if (outcome.error == AppError.SignedOut || outcome.error == AppError.NotConfigured) Step.Done else Step.Retry
            }
        }
    }

    private companion object {
        /** The server's copy can trail the data message by a few seconds. */
        val TIDY_DELAYS_MS = listOf(2_000L, 4_000L, 9_000L, 20_000L)
        const val PHOTO_BUDGET_MS = 4_000L
        const val PHOTO_SIDE = 192
    }
}
