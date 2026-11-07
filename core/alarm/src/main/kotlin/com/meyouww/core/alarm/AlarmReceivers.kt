package com.meyouww.core.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * An alarm's time has come. Exact alarms (setAlarmClock) may start a foreground
 * service from the background, which plays the sound through the alarm stream.
 * Window alarms may not, so they ring through an insistent notification on the
 * alarms channel, whose sound the system plays.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RING) return
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val scheduler = AlarmScheduler(context)
        val alarm = scheduler.get(id) ?: return
        if (alarm.exact) {
            val started = runCatching {
                ContextCompat.startForegroundService(context, AlarmService.ringIntent(context, id))
            }.isSuccess
            if (started) return
        }
        AlarmNotifier(context).showFallbackRing(alarm)
        // No service will finish this ring: a repeating alarm moves on now. A one-off stays
        // until it's dismissed or snoozed from the alarm screen.
        if (alarm.repeatDays != 0) scheduler.finished(id)
    }

    companion object {
        const val ACTION_RING = "com.meyouww.action.ALARM_RING"
    }
}

/**
 * Re-arms every alarm after a reboot (even before the first unlock, from
 * device-protected storage), a clock or time-zone change, an app update, or when
 * the exact-alarm permission changes. Only re-arms: Android 15+ forbids starting
 * media foreground services from BOOT_COMPLETED.
 */
class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                AlarmScheduler(context).rescheduleAll()
                EventReminders(context).rearm()
            }
        }
    }
}
