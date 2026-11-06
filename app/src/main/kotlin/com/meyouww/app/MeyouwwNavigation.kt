package com.meyouww.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meyouww.core.notifications.push.AndroidPushNotifications
import com.meyouww.feature.home.HomeScreen
import com.meyouww.feature.onboarding.AgeGateMode
import com.meyouww.feature.onboarding.AgeGateScreen
import com.meyouww.feature.onboarding.ProfileSetupScreen
import com.meyouww.feature.onboarding.SignInScreen
import com.meyouww.feature.onboarding.WelcomeScreen
import com.meyouww.feature.settings.DeliverySetupScreen
import com.meyouww.feature.settings.LegalLinks
import com.meyouww.feature.settings.LicensesScreen
import com.meyouww.feature.settings.SettingsScreen
import com.meyouww.feature.settings.SystemSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable private data object WelcomeRoute
@Serializable private data object AgeGateRoute
@Serializable private data object SignInRoute
@Serializable private data object HomeRoute
@Serializable private data object SettingsRoute
@Serializable private data object DeliverySetupRoute
@Serializable private data object LicensesRoute

/** Top-level switch between the signed-out flow, onboarding steps and the main app. */
@Composable
fun MeyouwwApp(
    viewModel: AppViewModel,
    pendingDestination: MutableStateFlow<String?>,
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (phase) {
            AppPhase.Loading -> Unit // the splash screen stays up meanwhile
            AppPhase.SignedOut -> SignedOutNav()
            AppPhase.NeedsAgeGate -> LegalAware { terms, privacy ->
                AgeGateScreen(AgeGateMode.AfterSignIn, onDone = viewModel::recheck, onOpenTerms = terms, onOpenPrivacy = privacy)
            }
            AppPhase.NeedsProfile -> ProfileSetupScreen(onDone = viewModel::recheck)
            AppPhase.Ready -> MainNav(pendingDestination)
        }
    }
}

@Composable
private fun LegalAware(content: @Composable (terms: () -> Unit, privacy: () -> Unit) -> Unit) {
    val context = LocalContext.current
    content({ SystemSettings.web(context, LegalLinks.TERMS) }, { SystemSettings.web(context, LegalLinks.PRIVACY) })
}

@Composable
private fun SignedOutNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = WelcomeRoute) {
        composable<WelcomeRoute> {
            WelcomeScreen(onGetStarted = { nav.navigate(AgeGateRoute) })
        }
        composable<AgeGateRoute> {
            LegalAware { terms, privacy ->
                AgeGateScreen(
                    mode = AgeGateMode.BeforeSignIn,
                    onDone = { nav.navigate(SignInRoute) },
                    onOpenTerms = terms,
                    onOpenPrivacy = privacy,
                )
            }
        }
        composable<SignInRoute> {
            // When the code is verified the session changes and MeyouwwApp moves on by itself.
            SignInScreen(onSignedIn = {})
        }
    }
}

@Composable
private fun MainNav(pendingDestination: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val destination by pendingDestination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        if (destination == AndroidPushNotifications.DESTINATION_DELIVERY_SETUP) {
            nav.navigate(DeliverySetupRoute) { launchSingleTop = true }
        }
        pendingDestination.value = null
    }
    NavHost(navController = nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onOpenDeliverySetup = { nav.navigate(DeliverySetupRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenDeliverySetup = { nav.navigate(DeliverySetupRoute) },
                onOpenLicenses = { nav.navigate(LicensesRoute) },
            )
        }
        composable<DeliverySetupRoute> { DeliverySetupScreen(onBack = { nav.popBackStack() }) }
        composable<LicensesRoute> { LicensesScreen(onBack = { nav.popBackStack() }) }
    }
}
