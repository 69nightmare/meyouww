package com.meyouww.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context

/** The app's notifications in the phone's shade. */
object NotificationShade {
    /**
     * Opening meyouww clears its notifications: everything is visible in the app now.
     * Ongoing ones stay (a ringing alarm, work in progress), since they control something.
     */
    fun clearOnOpen(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching {
            manager.activeNotifications
                .filter { it.notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) == 0 }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }
}
