package com.meyouww.core.data.device

import android.app.WallpaperManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowInsets
import android.view.WindowManager
import com.meyouww.core.data.common.Outcome
import com.meyouww.core.data.common.RpcJson
import com.meyouww.core.data.common.outcomeOf
import com.meyouww.core.data.common.rpcBody
import com.meyouww.core.data.common.statusOf
import com.meyouww.core.data.di.SupabaseProvider
import com.meyouww.core.data.prefs.AppPreferences
import com.meyouww.core.model.ContactDevice
import com.meyouww.core.model.ScreenProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Reads only screen facts: size, density, bar and cut-out heights, corner radius,
 * and whether wallpapers can be set. Maker and model only if the owner opted in.
 */
@Singleton
class ScreenProfileCollector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun collect(shareModel: Boolean, liveWallpaper: Boolean, lockRotates: Boolean): ScreenProfile {
        val density = context.resources.displayMetrics.densityDpi
        var w: Int
        var h: Int
        var status = (24 * density / 160f).toInt()
        var nav = (48 * density / 160f).toInt()
        var cutout = 0
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.maximumWindowMetrics
            w = metrics.bounds.width()
            h = metrics.bounds.height()
            val insets = metrics.windowInsets
            status = insets.getInsets(WindowInsets.Type.statusBars()).top
            nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            cutout = insets.getInsets(WindowInsets.Type.displayCutout()).top
        } else {
            val real = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(real)
            w = real.widthPixels
            h = real.heightPixels
        }
        // Always describe the phone upright.
        val portraitW = min(w, h)
        val portraitH = max(w, h)
        var corner = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            corner = display?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
        }
        val wallpapers = WallpaperManager.getInstance(context)
        return ScreenProfile(
            widthPx = portraitW,
            heightPx = portraitH,
            densityDpi = density,
            statusBarPx = status.coerceAtLeast(0),
            navBarPx = nav.coerceAtLeast(0),
            cutoutTopPx = cutout.coerceAtLeast(0),
            cornerRadiusPx = corner.coerceAtLeast(0),
            sdkInt = Build.VERSION.SDK_INT,
            maker = Build.MANUFACTURER.takeIf { shareModel }?.take(40),
            model = Build.MODEL.takeIf { shareModel }?.take(60),
            wallpaperSupported = wallpapers.isWallpaperSupported,
            setWallpaperAllowed = wallpapers.isSetWallpaperAllowed,
            separateLock = true,
            liveWallpaper = liveWallpaper,
            lockRotates = lockRotates,
            timezone = ZoneId.systemDefault().id,
        )
    }
}

/** Uploads this phone's profile when it changes, and reads friends' shared profiles. */
@Singleton
class DeviceProfileRepository @Inject constructor(
    private val supabase: SupabaseProvider,
    private val prefs: AppPreferences,
    private val collector: ScreenProfileCollector,
) {
    /** Sends the profile if anything changed, or once a week anyway. */
    suspend fun uploadIfChanged(force: Boolean = false): Outcome<Boolean> = outcomeOf {
        val profile = collector.collect(
            shareModel = prefs.shareModel.first(),
            liveWallpaper = prefs.usesLiveWallpaper.first(),
            lockRotates = prefs.lockRotates.first(),
        )
        val body = RpcJson.encodeToJsonElement(profile)
        val hash = body.toString().hashCode().toString()
        val stale = System.currentTimeMillis() - prefs.profileUploadedAt.first() > TimeUnit.DAYS.toMillis(7)
        if (!force && !stale && hash == prefs.profileHash.first()) return@outcomeOf false
        val reply = supabase.rpcBody(
            "upsert_device_profile",
            buildJsonObject {
                put("p_installation_id", prefs.installationId())
                put("p_profile", body)
            },
        )
        val ok = statusOf(reply) == "ok"
        if (ok) prefs.setProfileUploaded(hash, System.currentTimeMillis())
        ok
    }

    /** A friend's phones; empty unless they share their screen specs with you. */
    suspend fun contactDevices(contactId: String): Outcome<List<ContactDevice>> = outcomeOf {
        RpcJson.decodeFromString<List<ContactDevice>>(
            supabase.rpcBody("list_contact_devices", buildJsonObject { put("p_contact", contactId) }),
        )
    }
}
