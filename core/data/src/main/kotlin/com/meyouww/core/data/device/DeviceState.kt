package com.meyouww.core.data.device

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import com.meyouww.core.model.DeviceStateReport
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the live state of everything that affects closed-app delivery. */
@Singleton
class DeviceStateCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun collect(): DeviceStateReport {
        val nm = NotificationManagerCompat.from(context)
        val channels = nm.notificationChannelsCompat.associate { it.id to it.importance }
        return DeviceStateReport(
            notificationsEnabled = nm.areNotificationsEnabled(),
            channels = channels,
            exactAlarmsAllowed = exactAlarmsAllowed(),
            fullScreenAllowed = fullScreenAllowed(),
            backgroundRestricted = backgroundRestricted(),
            dataSaver = dataSaver(),
            playServices = playServices(),
            standbyBucket = standbyBucket(),
        )
    }

    fun exactAlarmsAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

    fun fullScreenAllowed(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

    fun backgroundRestricted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted ?: false
        } else {
            false
        }

    fun dataSaver(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "off"
        return when (cm.restrictBackgroundStatus) {
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "on"
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "allowed"
            else -> "off"
        }
    }

    fun playServices(): String = when (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)) {
        ConnectionResult.SUCCESS -> "ok"
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED, ConnectionResult.SERVICE_UPDATING -> "update_required"
        ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_INVALID -> "missing"
        else -> "error"
    }

    private fun standbyBucket(): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
        } else {
            null
        }
}
