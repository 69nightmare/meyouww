package com.meyouww.core.notifications.push

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.app.NotificationManagerCompat

/**
 * The phone's ring / vibrate / mute switch, changed by a girlfriend / boyfriend
 * (the server only lets a partner send it, and only if the owner allows it).
 * Alarms still ring: Android keeps the alarm stream out of ringer mode.
 */
object RingerControl {
    fun current(context: Context): String = when (audio(context)?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> "mute"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        else -> "ring"
    }

    /** False when Android didn't allow it (mute and leaving Do Not Disturb need DND access). */
    fun apply(context: Context, mode: String): Boolean {
        val audio = audio(context) ?: return false
        val target = when (mode) {
            "mute" -> AudioManager.RINGER_MODE_SILENT
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return runCatching {
            audio.ringerMode = target
            audio.ringerMode == target
        }.getOrDefault(false)
    }

    /** Do Not Disturb access, needed to mute (granted once in the first-launch "Allow all"). */
    fun hasDndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    private fun audio(context: Context): AudioManager? = context.getSystemService(AudioManager::class.java)
}

/** "Undo" on the "Riya switched your phone to vibrate" notification. */
class RingerUndoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_UNDO) return
        val previous = intent.getStringExtra(EXTRA_PREVIOUS) ?: return
        RingerControl.apply(context, previous)
        NotificationManagerCompat.from(context).cancel(TAG, AndroidPushNotifications.NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_UNDO = "com.meyouww.action.RINGER_UNDO"
        const val EXTRA_PREVIOUS = "previous"
        const val TAG = "ringer"
    }
}
