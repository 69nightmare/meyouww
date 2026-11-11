package com.meyouww.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.meyouww.core.model.AlarmTimes
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** An alarm as this phone keeps it: enough to ring with no network and before unlock. */
@Serializable
data class LocalAlarm(
    val id: String,
    val title: String,
    val message: String? = null,
    /** The friend who set it; null for my own alarms. */
    val fromName: String? = null,
    /** Their user ID, to show the nickname I gave them instead. */
    val fromId: String? = null,
    val firstRingMs: Long,
    /** "HH:MM" wall-clock time, for repeating alarms. */
    val localTime: String,
    val repeatDays: Int = 0,
    val snoozeMinutes: Int = 5,
    val vibrate: Boolean = true,
    /** tap or hold */
    val dismiss: String = "tap",
    val nextAtMs: Long = 0,
    val snoozeUntilMs: Long? = null,
    /** Whether it was armed as an exact alarm (setAlarmClock) or a window (may be late). */
    val exact: Boolean = true,
)

/**
 * The alarm list lives in DEVICE-PROTECTED storage, so alarms can be re-armed and ring
 * after a reboot before the phone is unlocked (Direct Boot).
 */
class AlarmStore(context: Context) {
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("meyouww_alarms", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LocalAlarm.serializer())

    @Synchronized
    fun load(): List<LocalAlarm> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    @Synchronized
    fun save(alarms: List<LocalAlarm>) {
        prefs.edit().putString(KEY, json.encodeToString(serializer, alarms)).commit()
    }

    fun get(id: String): LocalAlarm? = load().firstOrNull { it.id == id }

    @Synchronized
    fun put(alarm: LocalAlarm) = save(load().filterNot { it.id == alarm.id } + alarm)

    @Synchronized
    fun remove(id: String) = save(load().filterNot { it.id == id })

    fun clear() = save(emptyList())

    private companion object {
        const val KEY = "alarms"
    }
}

/**
 * Arms alarms with AlarmManager. Exact alarms use setAlarmClock(), which shows the
 * system alarm icon and wakes the phone even in Doze. Without the exact-alarm
 * permission they fall back to a 10-minute window and are labelled "may be late".
 * Safe to use from a direct-boot receiver: it touches only device-protected storage.
 */
class AlarmScheduler(private val context: Context) {
    private val store = AlarmStore(context)
    private val manager = context.getSystemService(AlarmManager::class.java)

    fun canExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    fun all(): List<LocalAlarm> = store.load().sortedBy { it.nextAtMs }

    fun get(id: String): LocalAlarm? = store.get(id)

    /** Makes this phone's alarms exactly [alarms] (from the server), keeping snoozes. */
    fun replaceAll(alarms: List<LocalAlarm>) {
        val old = store.load().associateBy { it.id }
        old.keys.filter { id -> alarms.none { it.id == id } }.forEach { disarm(it) }
        store.save(alarms.map { it.copy(snoozeUntilMs = old[it.id]?.snoozeUntilMs) })
        rescheduleAll()
    }

    /** Recomputes every next ring and arms it. Called on boot, time and zone changes, updates. */
    fun rescheduleAll(now: Instant = Instant.now()) {
        val exact = canExact()
        val planned = store.load().mapNotNull { alarm ->
            val snoozed = alarm.snoozeUntilMs?.takeIf { it > now.toEpochMilli() }
            val next = snoozed ?: nextRing(alarm, now)?.toEpochMilli() ?: return@mapNotNull null
            alarm.copy(nextAtMs = next, snoozeUntilMs = snoozed, exact = exact)
        }
        // Finished one-off alarms drop out; disarm them in case they were still pending.
        store.load().filter { a -> planned.none { it.id == a.id } }.forEach { disarm(it.id) }
        store.save(planned)
        planned.forEach { arm(it) }
    }

    fun snooze(id: String, now: Instant = Instant.now()) {
        val alarm = store.get(id) ?: return
        val until = now.toEpochMilli() + alarm.snoozeMinutes * 60_000L
        val updated = alarm.copy(snoozeUntilMs = until, nextAtMs = until, exact = canExact())
        store.put(updated)
        arm(updated)
    }

    /** After a ring is dismissed or timed out: repeating alarms move on, one-offs are done. */
    fun finished(id: String, now: Instant = Instant.now()) {
        val alarm = store.get(id) ?: return
        val next = nextRing(alarm.copy(snoozeUntilMs = null), now.plusSeconds(1))
        if (next == null) {
            store.remove(id)
            disarm(id)
        } else {
            val updated = alarm.copy(snoozeUntilMs = null, nextAtMs = next.toEpochMilli(), exact = canExact())
            store.put(updated)
            arm(updated)
        }
    }

    fun remove(id: String) {
        disarm(id)
        store.remove(id)
    }

    fun clearAll() {
        store.load().forEach { disarm(it.id) }
        store.clear()
    }

    private fun nextRing(alarm: LocalAlarm, now: Instant): Instant? {
        val time = AlarmTimes.parseLocalTime(alarm.localTime) ?: return null
        return AlarmTimes.nextRing(Instant.ofEpochMilli(alarm.firstRingMs), time, alarm.repeatDays, ZoneId.systemDefault(), now)
    }

    private fun arm(alarm: LocalAlarm) {
        val operation = ringIntent(alarm.id)
        if (alarm.exact && canExact()) {
            try {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.nextAtMs, showIntent()), operation)
                return
            } catch (e: SecurityException) {
                // The exact-alarm permission was taken away a moment ago: use the window below.
            }
        }
        manager.setWindow(AlarmManager.RTC_WAKEUP, alarm.nextAtMs, WINDOW_MS, operation)
    }

    private fun disarm(id: String) = manager.cancel(ringIntent(id))

    private fun ringIntent(id: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RING).putExtra(EXTRA_ALARM_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Tapping the system alarm icon opens meyouww's alarm list. */
    private fun showIntent(): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_DESTINATION, DESTINATION_ALARMS)
            ?: Intent()
        return PendingIntent.getActivity(context, 0xA1A, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val EXTRA_ALARM_ID = "com.meyouww.extra.ALARM_ID"

        /** Same extra the notifications use to open a screen. */
        const val EXTRA_DESTINATION = "com.meyouww.extra.DESTINATION"
        const val DESTINATION_ALARMS = "alarms"
        private const val WINDOW_MS = 10 * 60_000L
    }
}
