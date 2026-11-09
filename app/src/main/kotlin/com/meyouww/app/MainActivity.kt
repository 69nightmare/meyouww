package com.meyouww.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.meyouww.core.data.profile.AvatarRepository
import com.meyouww.core.designsystem.component.AvatarLoader
import com.meyouww.core.designsystem.component.LocalAvatarLoader
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.InviteLinks
import com.meyouww.core.notifications.NotificationShade
import com.meyouww.core.notifications.push.AndroidPushNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Up to about 24 MB of decoded profile pictures, shared by every screen. */
private val decodedAvatars = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    @Inject lateinit var avatars: AvatarRepository

    /**
     * Profile pictures for every PersonAvatar in the app. Decoded pictures are kept in
     * memory, so scrolling a list back and forth doesn't decode them again (no dropped frames).
     */
    private val avatarLoader = AvatarLoader { path ->
        decodedAvatars.get(path) ?: avatars.bytes(path)?.let { bytes ->
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }?.also { decodedAvatars.put(path, it) }
    }

    /** Where a tapped notification asked to go, consumed by the main navigation. */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // Light only: dark status and navigation bar icons on the sky, even in dark mode.
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        preferFastestRefreshRate()
        splash.setKeepOnScreenCondition { appViewModel.phase.value == AppPhase.Loading }
        splash.setOnExitAnimationListener(::wiggleAway)
        handleIntent(intent)
        setContent {
            MeyouwwTheme {
                CompositionLocalProvider(LocalAvatarLoader provides avatarLoader) {
                    MeyouwwApp(appViewModel, pendingDestination)
                }
            }
        }
    }

    /**
     * Some phones (Xiaomi, Oppo, Vivo...) keep apps at 60 Hz unless they ask for more.
     * Asks for the screen's fastest mode at its current resolution (90 or 120 Hz).
     */
    private fun preferFastestRefreshRate() {
        runCatching {
            @Suppress("DEPRECATION")
            val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay) ?: return
            val current = display.mode
            val fastest = display.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate } ?: return
            if (window.attributes.preferredDisplayModeId != fastest.modeId) {
                window.attributes = window.attributes.also {
                    it.preferredDisplayModeId = fastest.modeId
                    it.preferredRefreshRate = fastest.refreshRate
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Everything is on screen now: clear meyouww's notifications from the shade.
        NotificationShade.clearOnOpen(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(AndroidPushNotifications.EXTRA_DESTINATION)?.let { pendingDestination.value = it }
        // meyouww://add/<id> from a shared invite or a QR code scanned by another app.
        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data?.scheme == InviteLinks.SCHEME && data.host == InviteLinks.HOST) {
            InviteLinks.parse(data.toString())?.let { pendingDestination.value = DESTINATION_ADD_FRIEND + it }
        }
    }

    /** A short Whity wiggle as the splash leaves. Skipped when animations are off. */
    private fun wiggleAway(provider: SplashScreenViewProvider) {
        val animationsOff = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val icon: View? = runCatching { provider.iconView }.getOrNull()
        if (animationsOff || icon == null) {
            provider.remove()
            return
        }
        ObjectAnimator.ofFloat(icon, View.ROTATION, 0f, -10f, 10f, -6f, 0f).apply {
            duration = 420L
            doOnEnd { provider.remove() }
            start()
        }
    }
}
