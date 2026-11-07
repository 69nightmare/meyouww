package com.meyouww.core.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.meyouww.core.model.ChannelIds
import java.text.DateFormat
import java.util.Date

/**
 * Rings one alarm: a foreground service (type mediaPlayback) that plays the alarm
 * sound on the ALARM stream at the person's own alarm volume, vibrates, and shows a
 * full-screen alarm over the lock screen where Android allows it. It stops by itself
 * after 10 minutes and says the alarm was missed.
 */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringingId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { ringingId?.let { stop(it, missed = true) } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)
        when (intent?.action) {
            ACTION_RING -> if (id != null) ring(id) else stopSelf()
            ACTION_SNOOZE -> if (id != null) {
                AlarmScheduler(this).snooze(id)
                stop(id, missed = false, finished = false)
            }
            ACTION_DISMISS -> if (id != null) stop(id, missed = false)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: String) {
        val alarm = AlarmScheduler(this).get(id)
        if (alarm == null) {
            stopSelf()
            return
        }
        // Another alarm is already ringing: that one counts as handled.
        ringingId?.takeIf { it != id }?.let { AlarmScheduler(this).finished(it) }
        ringingId = id
        val notifier = AlarmNotifier(this)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notifier.ringing(alarm),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        startSound()
        if (alarm.vibrate) startVibration()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_FOR_MS)
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(AlarmScheduler.EXTRA_ALARM_ID, id).putExtra(EXTRA_RINGING, true))
    }

    private fun startSound() {
        stopSound()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        } ?: return
        vibrator = v
        val pattern = VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700, 1200), 0)
        @Suppress("DEPRECATION")
        v.vibrate(
            pattern,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
    }

    private fun stopSound() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun stop(id: String, missed: Boolean, finished: Boolean = true) {
        handler.removeCallbacks(timeout)
        stopSound()
        val alarm = AlarmScheduler(this).get(id)
        if (finished) AlarmScheduler(this).finished(id)
        if (missed && alarm != null) AlarmNotifier(this).showMissed(alarm)
        ringingId = null
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra(AlarmScheduler.EXTRA_ALARM_ID, id).putExtra(EXTRA_RINGING, false))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopSound()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.meyouww.action.ALARM_SERVICE_RING"
        const val ACTION_SNOOZE = "com.meyouww.action.ALARM_SNOOZE"
        const val ACTION_DISMISS = "com.meyouww.action.ALARM_DISMISS"

        /** Broadcast inside the app when an alarm starts or stops ringing (closes the alarm screen). */
        const val ACTION_STATE = "com.meyouww.action.ALARM_STATE"
        const val EXTRA_RINGING = "ringing"
        const val NOTIFICATION_ID = 7300
        private const val RING_FOR_MS = 10 * 60_000L

        fun ringIntent(context: Context, id: String): Intent =
            Intent(context, AlarmService::class.java).setAction(ACTION_RING).putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)

        fun actionIntent(context: Context, action: String, id: String): Intent =
            Intent(context, AlarmService::class.java).setAction(action).putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
    }
}

/** The alarm's notifications: ringing (full-screen), fallback ring, and missed. */
class AlarmNotifier(private val context: Context) {

    fun ringing(alarm: LocalAlarm): Notification {
        ensureRingingChannel()
        return base(alarm, RINGING_CHANNEL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(screenIntent(alarm.id), true)
            .addAction(0, context.getString(R.string.alarm_snooze), serviceAction(AlarmService.ACTION_SNOOZE, alarm.id))
            .addAction(0, context.getString(R.string.alarm_dismiss), serviceAction(AlarmService.ACTION_DISMISS, alarm.id))
            .build()
    }

    /** No foreground service allowed: the alarms channel's own sound rings, repeating until handled. */
    fun showFallbackRing(alarm: LocalAlarm) {
        val notification = base(alarm, ChannelIds.ALARMS)
            .setContentText(context.getString(R.string.alarm_may_be_late) + " · " + subtitle(alarm))
            .setOngoing(false)
            .setAutoCancel(true)
            .setFullScreenIntent(screenIntent(alarm.id), true)
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        post(alarm.id, notification)
    }

    fun showMissed(alarm: LocalAlarm) {
        post(
            alarm.id,
            base(alarm, ChannelIds.REMINDERS)
                .setContentTitle(context.getString(R.string.alarm_missed_title, alarm.title))
                .setContentText(context.getString(R.string.alarm_missed_text, time(System.currentTimeMillis())))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setSilent(true)
                .build(),
        )
    }

    fun cancel(id: String) = NotificationManagerCompat.from(context).cancel(TAG_PREFIX + id, 0)

    private fun base(alarm: LocalAlarm, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("⏰ " + alarm.title)
            .setContentText(subtitle(alarm))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(screenIntent(alarm.id))

    private fun subtitle(alarm: LocalAlarm): String = listOfNotNull(
        alarm.fromName?.let { context.getString(R.string.alarm_from, it) },
        alarm.message,
    ).joinToString(" · ").ifEmpty { time(System.currentTimeMillis()) }

    private fun time(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    private fun screenIntent(id: String): PendingIntent = PendingIntent.getActivity(
        context,
        id.hashCode(),
        Intent(context, AlarmActivity::class.java)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(action: String, id: String): PendingIntent = PendingIntent.getService(
        context,
        (action + id).hashCode(),
        AlarmService.actionIntent(context, action, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(id: String, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) NotificationManagerCompat.from(context).notify(TAG_PREFIX + id, 0, notification)
    }

    /** Silent channel for the ringing notification: the service plays the sound itself. */
    private fun ensureRingingChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(RINGING_CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(RINGING_CHANNEL, context.getString(R.string.alarm_channel_ringing), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.alarm_channel_ringing_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    companion object {
        const val RINGING_CHANNEL = "alarm_ringing_v1"
        private const val TAG_PREFIX = "alarm:"
    }
}
