package com.meyouww.app

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.model.InviteLinks
import com.meyouww.core.notifications.push.AndroidPushNotifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    /** Where a tapped notification asked to go, consumed by the main navigation. */
    private val pendingDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { appViewModel.phase.value == AppPhase.Loading }
        splash.setOnExitAnimationListener(::wiggleAway)
        handleIntent(intent)
        setContent {
            MeyouwwTheme {
                MeyouwwApp(appViewModel, pendingDestination)
            }
        }
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
