package com.meyouww.core.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meyouww.core.data.auth.AuthRepository
import com.meyouww.core.data.calendar.CalendarRepository
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.model.ChannelIds
import com.meyouww.core.model.EventInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.util.Date

/** A reminder for a calendar event, kept in device-protected storage like alarms. */
@Serializable
data class LocalReminder(
    val id: String,
    val title: String,
    val emoji: String,
    val startMs: Long,
    val allDay: Boolean,
    val atMs: Long,
)

/**
 * Event reminders: a gentle notification (reminders channel) some minutes before an
 * event I'm going to. Inexact is fine here, so no exact-alarm permission is needed.
 */
class EventReminders(private val context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("meyouww_reminders", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LocalReminder.serializer())
    private val manager = context.getSystemService(AlarmManager::class.java)

    @Synchronized
    fun all(): List<LocalReminder> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Replaces the reminders with those of [events] (mine or going, with a reminder, still ahead). */
    @Synchronized
    fun update(events: List<EventInfo>, now: Instant = Instant.now()) {
        val planned = events.mapNotNull { e ->
            val minutes = e.remindMinutes ?: return@mapNotNull null
            if (e.cancelled || !(e.mine || e.going)) return@mapNotNull null
            val start = e.start ?: return@mapNotNull null
            val at = start.minusSeconds(minutes * 60L)
            if (!at.isAfter(now)) return@mapNotNull null
            LocalReminder(e.id, e.title, e.emoji, start.toEpochMilli(), e.allDay, at.toEpochMilli())
        }
        all().filter { old -> planned.none { it.id == old.id } }.forEach { manager.cancel(intent(it.id)) }
        prefs.edit().putString(KEY, json.encodeToString(serializer, planned)).commit()
        planned.forEach { arm(it) }
    }

    /** After a reboot or clock change. */
    @Synchronized
    fun rearm(now: Instant = Instant.now()) {
        val keep = all().filter { it.atMs > now.toEpochMilli() }
        prefs.edit().putString(KEY, json.encodeToString(serializer, keep)).commit()
        keep.forEach { arm(it) }
    }

    fun clear() {
        all().forEach { manager.cancel(intent(it.id)) }
        prefs.edit().clear().commit()
    }

    fun get(id: String): LocalReminder? = all().firstOrNull { it.id == id }

    private fun arm(reminder: LocalReminder) =
        manager.setWindow(AlarmManager.RTC_WAKEUP, reminder.atMs, WINDOW_MS, intent(reminder.id))

    private fun intent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        ("reminder" + id).hashCode(),
        Intent(context, EventReminderReceiver::class.java).setAction(EventReminderReceiver.ACTION).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val EXTRA_ID = "com.meyouww.extra.REMINDER_ID"
        private const val KEY = "reminders"
        private const val WINDOW_MS = 5 * 60_000L
    }
}

/** Shows the reminder. */
class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val reminder = intent.getStringExtra(EventReminders.EXTRA_ID)?.let { EventReminders(context).get(it) } ?: return
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        val start = Date(reminder.startMs)
        val text = when {
            reminder.allDay -> context.getString(R.string.reminder_all_day)
            LocalDate.now().toEpochDay() == Instant.ofEpochMilli(reminder.startMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay() ->
                context.getString(R.string.reminder_today_at, DateFormat.getTimeInstance(DateFormat.SHORT).format(start))
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(start)
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0xCA1, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        NotificationManagerCompat.from(context).notify(
            "reminder:" + reminder.id,
            0,
            NotificationCompat.Builder(context, ChannelIds.REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(reminder.emoji + " " + reminder.title)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    companion object {
        const val ACTION = "com.meyouww.action.EVENT_REMINDER"
    }
}

/** Fetches the next 60 days of my calendar and plans their reminders. */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val calendar: CalendarRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!auth.ensureFreshSession()) return Result.success()
        val today = LocalDate.now()
        return when (val outcome = calendar.list(today.minusDays(1), today.plusDays(60))) {
            is Outcome.Success -> {
                EventReminders(applicationContext).update(outcome.value)
                Result.success()
            }
            is Outcome.Failure -> if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("calendar-sync", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
