package com.meyouww.core.notifications.push

import android.content.Context
import android.content.Intent

/**
 * Asks the home-screen widget (if one is placed) to redraw: new messages arrived, or the
 * app was left after reading some. Only meyouww's own receiver gets it.
 */
object WidgetRefresh {
    const val ACTION = "com.meyouww.action.WIDGET_REFRESH"

    fun request(context: Context) {
        runCatching { context.sendBroadcast(Intent(ACTION).setPackage(context.packageName)) }
    }
}
