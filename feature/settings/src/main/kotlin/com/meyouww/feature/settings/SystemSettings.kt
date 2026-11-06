package com.meyouww.feature.settings

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes

/**
 * Opens system and phone-maker settings screens. Every phone-maker screen is
 * tried inside try/catch, because those screens move between OS versions,
 * and we fall back to App info. No QUERY_ALL_PACKAGES needed.
 */
object SystemSettings {

    fun appNotifications(context: Context) = open(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )

    fun appInfo(context: Context) = open(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun backgroundData(context: Context) = open(
        context,
        Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )

    fun exactAlarms(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            open(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        } else {
            appInfo(context)
        }
    }

    fun playServices(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms"))
        if (!tryStart(context, market)) {
            open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.gms")))
        }
    }

    fun web(context: Context, url: String) = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /** Tries the known autostart / background screens for this phone maker, then App info. */
    fun phoneMaker(context: Context, manufacturer: String) {
        val candidates = makerComponents[brandOf(manufacturer)].orEmpty()
        for ((pkg, cls) in candidates) {
            if (tryStart(context, Intent().setComponent(ComponentName(pkg, cls)))) return
        }
        appInfo(context)
    }

    @StringRes
    fun makerSteps(manufacturer: String): Int = when (brandOf(manufacturer)) {
        Brand.Xiaomi -> R.string.maker_steps_xiaomi
        Brand.Oppo -> R.string.maker_steps_oppo
        Brand.Vivo -> R.string.maker_steps_vivo
        Brand.Huawei -> R.string.maker_steps_huawei
        Brand.Samsung -> R.string.maker_steps_samsung
        Brand.Transsion -> R.string.maker_steps_transsion
        Brand.Other -> R.string.maker_steps_generic
    }

    private enum class Brand { Xiaomi, Oppo, Vivo, Huawei, Samsung, Transsion, Other }

    private fun brandOf(manufacturer: String): Brand = when (manufacturer.trim().lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.Xiaomi
        "oppo", "realme", "oneplus" -> Brand.Oppo
        "vivo", "iqoo" -> Brand.Vivo
        "huawei", "honor" -> Brand.Huawei
        "samsung" -> Brand.Samsung
        "tecno", "infinix", "itel" -> Brand.Transsion
        else -> Brand.Other
    }

    private val makerComponents: Map<Brand, List<Pair<String, String>>> = mapOf(
        Brand.Xiaomi to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        Brand.Oppo to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        ),
        Brand.Vivo to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        ),
        Brand.Huawei to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
        Brand.Samsung to listOf(
            "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
            "com.samsung.android.sm" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        ),
        Brand.Transsion to listOf(
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        ),
    )

    private fun open(context: Context, intent: Intent) {
        if (!tryStart(context, intent)) tryStart(context, Intent(Settings.ACTION_SETTINGS))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
