package com.meyouww.core.notifications.push

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.meyouww.core.notifications.R
import com.meyouww.core.notifications.channels.MeyouwwChannels
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Builds and posts notifications from local data only. */
@Singleton
class AndroidPushNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PushNotifications {

    override fun showDeliveryTestArrived(testId: String) {
        post(
            tag = TAG_DELIVERY_TEST,
            notification = deliveryTestBuilder(context.getString(R.string.delivery_test_arrived_text)).build(),
        )
    }

    /** Updates the self-test notification in place once the latency is known. */
    fun showDeliveryTestLatency(latencyMs: Long) {
        val seconds = String.format(Locale.getDefault(), "%.1f", latencyMs / 1000.0)
        post(
            tag = TAG_DELIVERY_TEST,
            notification = deliveryTestBuilder(context.getString(R.string.delivery_test_latency_text, seconds))
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    /** Low-importance notification shown only when Android 11 or older runs expedited work as a service. */
    fun backgroundWorkNotification(): Notification =
        NotificationCompat.Builder(context, MeyouwwChannels.BACKGROUND_WORK)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.background_work_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun deliveryTestBuilder(text: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, MeyouwwChannels.DELIVERY_STATUS)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.delivery_test_arrived_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(openApp(DESTINATION_DELIVERY_SETUP))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(MeyouwwChannels.DELIVERY_STATUS))

    private fun publicVersion(channel: String): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_meyouww)
            .setContentTitle(context.getString(R.string.notification_public_version))
            .build()

    private fun openApp(destination: String): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_DESTINATION, destination)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        } ?: return null
        return PendingIntent.getActivity(
            context,
            destination.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun post(tag: String, notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return
        // Tag + fixed id deduplicates: a repeated event updates the same notification.
        NotificationManagerCompat.from(context).notify(tag, NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_DESTINATION = "com.meyouww.extra.DESTINATION"
        const val DESTINATION_DELIVERY_SETUP = "delivery_setup"
        private const val TAG_DELIVERY_TEST = "delivery_test"
        private const val NOTIFICATION_ID = 1
    }
}
