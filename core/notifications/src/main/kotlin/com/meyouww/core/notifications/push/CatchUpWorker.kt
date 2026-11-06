package com.meyouww.core.notifications.push

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
import com.meyouww.core.chat.ChatScheduler
import com.meyouww.core.data.activity.ActivityRepository
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.wallpaper.WallpaperRepository
import com.meyouww.core.model.parseInstant
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The safety net for lost pushes. Some phones (Xiaomi, Oppo, Vivo, Realme, ...) stop
 * apps that were swiped away and drop their pushes. This runs when the app opens and
 * about every 15 minutes while it is allowed to: it shows inbox items that never got a
 * notification, and applies automatic wallpapers that are still waiting.
 */
@HiltWorker
class CatchUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val activities: ActivityRepository,
    private val wallpapers: WallpaperRepository,
    private val notifications: AndroidPushNotifications,
    private val notified: NotifiedStore,
    private val chat: ChatScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success()

        val list = (activities.refresh() as? Outcome.Success)?.value ?: return Result.retry()
        val now = Instant.now()
        val fresh = list.filter { item ->
            !item.read && item.id !in notified &&
                parseInstant(item.createdAt)?.let { Duration.between(it, now) < MAX_AGE } == true
        }
        if (notified.initialized) {
            fresh.forEach { notifications.showActivityDetails(it) }
        }
        // The first run only remembers what is already there: the heart shows it, no burst.
        notified.addAll(list.map { it.id })

        // Chat: messages whose push was lost, and anything still in the outbox.
        chat.fetch()
        chat.send()

        (wallpapers.received() as? Outcome.Success)?.value
            ?.filter { it.mode == "auto" && it.status in WAITING }
            ?.forEach { WallpaperWorker.enqueue(applicationContext, it.id, approved = false) }

        return Result.success()
    }

    companion object {
        private val MAX_AGE: Duration = Duration.ofDays(2)
        private val WAITING = setOf("sent", "delivered")
        private const val NOW = "catch-up-now"
        private const val PERIODIC = "catch-up-periodic"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CatchUpWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CatchUpWorker>(15, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

/** Starts the safety net; called whenever a signed-in session opens the app. */
@Singleton
class CatchUp @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun start() {
        CatchUpWorker.schedule(context)
        CatchUpWorker.runNow(context)
    }
}

/**
 * Inbox items that already had a notification, so the safety net never repeats one.
 * SharedPreferences because pushes write to it from onMessageReceived, which must be quick.
 */
@Singleton
class NotifiedStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("meyouww_notified", Context.MODE_PRIVATE)

    val initialized: Boolean get() = prefs.getBoolean(KEY_INIT, false)

    operator fun contains(id: String): Boolean = id in ids()

    fun add(id: String) = addAll(listOf(id))

    @Synchronized
    fun addAll(newIds: Collection<String>) {
        val merged = (ids() + newIds).distinct().takeLast(MAX)
        prefs.edit().putString(KEY_IDS, merged.joinToString(",")).putBoolean(KEY_INIT, true).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun ids(): List<String> = prefs.getString(KEY_IDS, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY_IDS = "ids"
        const val KEY_INIT = "initialized"
        const val MAX = 400
    }
}
