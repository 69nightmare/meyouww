package com.meyouww.core.notifications.chat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
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
import com.meyouww.core.chat.ChatRepository
import com.meyouww.core.chat.ChatScheduler
import com.meyouww.core.chat.ChatVisibility
import com.meyouww.core.chat.DeliveryResult
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.AppError
import com.meyouww.core.data.common.Outcome
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
import kotlinx.coroutines.SupervisorJob
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (inbox.fetch()) {
        ChatInbox.Step.Done -> Result.success()
        ChatInbox.Step.Retry -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
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
    }
}

@Singleton
class WorkManagerChatScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatScheduler {
    override fun send() = ChatSendWorker.enqueue(context)

    override fun fetch() = ChatFetchWorker.enqueue(context)
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
) {
    enum class Step { Done, Retry }

    private val lock = Mutex()

    suspend fun fetch(): Step = lock.withLock {
        if (!auth.ensureFreshSession()) return@withLock Step.Done
        when (val outcome = chat.receive()) {
            is Outcome.Success -> {
                outcome.value.map { it.conversationId }.distinct().forEach { id ->
                    val conversation = chat.conversation(id) ?: return@forEach
                    when {
                        visibility.isOpen(id) -> {
                            chat.markRead(id)
                            chatNotifications.cancel(id)
                        }
                        conversation.muted -> chatNotifications.cancel(id)
                        else -> chatNotifications.showConversation(conversation, chat.unseen(id))
                    }
                }
                // Receipts out now (a worker picks up anything that doesn't make it).
                runCatching { chat.deliverOutbox() }
                Step.Done
            }
            is Outcome.Failure -> if (outcome.error == AppError.Network) Step.Retry else Step.Done
        }
    }
}
