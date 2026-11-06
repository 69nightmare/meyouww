package com.meyouww.core.notifications.channels

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.meyouww.core.model.ChannelIds
import com.meyouww.core.notifications.R

/**
 * Every notification channel the app uses. IDs carry a version suffix because
 * Android does not let an app change a channel's sound or importance after
 * creation; a behaviour change means a new ID.
 */
object MeyouwwChannels {
    const val MESSAGES = ChannelIds.MESSAGES
    const val WALLPAPERS = ChannelIds.WALLPAPERS
    const val WALLPAPER_REQUESTS = ChannelIds.WALLPAPER_REQUESTS
    const val ALARMS = ChannelIds.ALARMS
    const val REMINDERS = ChannelIds.REMINDERS
    const val FRIENDS = ChannelIds.FRIENDS
    const val SECURITY = ChannelIds.SECURITY
    const val DELIVERY_STATUS = ChannelIds.DELIVERY_STATUS
    const val BACKGROUND_WORK = ChannelIds.BACKGROUND_WORK

    private data class Spec(val id: String, val name: Int, val description: Int, val importance: Int)

    private val specs = listOf(
        Spec(MESSAGES, R.string.channel_messages, R.string.channel_messages_desc, NotificationManager.IMPORTANCE_HIGH),
        Spec(WALLPAPERS, R.string.channel_wallpapers, R.string.channel_wallpapers_desc, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(WALLPAPER_REQUESTS, R.string.channel_wallpaper_requests, R.string.channel_wallpaper_requests_desc, NotificationManager.IMPORTANCE_HIGH),
        Spec(ALARMS, R.string.channel_alarms, R.string.channel_alarms_desc, NotificationManager.IMPORTANCE_HIGH),
        Spec(REMINDERS, R.string.channel_reminders, R.string.channel_reminders_desc, NotificationManager.IMPORTANCE_HIGH),
        Spec(FRIENDS, R.string.channel_friends, R.string.channel_friends_desc, NotificationManager.IMPORTANCE_DEFAULT),
        Spec(SECURITY, R.string.channel_security, R.string.channel_security_desc, NotificationManager.IMPORTANCE_HIGH),
        Spec(DELIVERY_STATUS, R.string.channel_delivery_status, R.string.channel_delivery_status_desc, NotificationManager.IMPORTANCE_LOW),
        Spec(BACKGROUND_WORK, R.string.channel_background_work, R.string.channel_background_work_desc, NotificationManager.IMPORTANCE_LOW),
    )

    /** Creates or updates every channel. Cheap and idempotent: call on every app start. */
    fun createAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = specs.map { spec ->
            NotificationChannel(spec.id, context.getString(spec.name), spec.importance).apply {
                description = context.getString(spec.description)
                when (spec.id) {
                    ALARMS -> {
                        // Alarm sound plays through the alarm stream and respects the alarm volume.
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(true)
                    }
                    MESSAGES, WALLPAPER_REQUESTS, REMINDERS -> enableVibration(true)
                    DELIVERY_STATUS, BACKGROUND_WORK -> setShowBadge(false)
                }
            }
        }
        manager.createNotificationChannels(channels)
    }
}
