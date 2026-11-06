package com.meyouww.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.meyouww.core.notifications.push.AndroidPushNotifications
import com.meyouww.feature.friends.AddFriendScreen
import com.meyouww.feature.friends.BlockedScreen
import com.meyouww.feature.friends.ContactPermissionsScreen
import com.meyouww.feature.friends.FriendDetailScreen
import com.meyouww.feature.friends.FriendsScreen
import com.meyouww.feature.home.HomeScreen
import com.meyouww.feature.inbox.InboxHeart
import com.meyouww.feature.inbox.InboxScreen
import com.meyouww.feature.studio.DraftsScreen
import com.meyouww.feature.studio.PreviewScreen
import com.meyouww.feature.studio.StudioScreen
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
@Serializable private data object FriendsRoute
@Serializable private data object InboxRoute
@Serializable private data class AddFriendRoute(val prefill: String? = null)
@Serializable private data class FriendDetailRoute(val id: String)
@Serializable private data class PermissionsRoute(val id: String)
@Serializable private data object BlockedRoute
@Serializable private data object CreateRoute
@Serializable private data class StudioRoute(val draftId: String? = null)
@Serializable private data class PreviewRoute(val draftId: String, val friendId: String? = null)
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
    // Surface sets the default text colour for the theme (needed for dark mode).
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
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

/** The main app: Home and Friends tabs, plus the screens they open. */
@Composable
private fun MainNav(pendingDestination: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val destination by pendingDestination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        val target = destination ?: return@LaunchedEffect
        when {
            target == AndroidPushNotifications.DESTINATION_DELIVERY_SETUP -> nav.navigate(DeliverySetupRoute) { launchSingleTop = true }
            target == AndroidPushNotifications.DESTINATION_INBOX -> nav.navigate(InboxRoute) { launchSingleTop = true }
            target.startsWith(DESTINATION_ADD_FRIEND) -> nav.navigate(AddFriendRoute(target.removePrefix(DESTINATION_ADD_FRIEND))) { launchSingleTop = true }
        }
        pendingDestination.value = null
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTab = current?.hasRoute<HomeRoute>() == true || current?.hasRoute<FriendsRoute>() == true ||
        current?.hasRoute<CreateRoute>() == true

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = nav, startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        onOpenDeliverySetup = { nav.navigate(DeliverySetupRoute) },
                        onAddFriend = { nav.navigate(AddFriendRoute()) },
                        onDraw = { nav.navigate(StudioRoute()) },
                        heart = { InboxHeart(onClick = { nav.navigate(InboxRoute) }) },
                    )
                }
                composable<CreateRoute> {
                    DraftsScreen(onNew = { nav.navigate(StudioRoute()) }, onOpen = { nav.navigate(StudioRoute(it)) })
                }
                composable<StudioRoute> { entry ->
                    StudioScreen(
                        draftId = entry.toRoute<StudioRoute>().draftId,
                        onBack = { nav.popBackStack() },
                        onPreview = { nav.navigate(PreviewRoute(it)) },
                    )
                }
                composable<PreviewRoute> { entry ->
                    val route = entry.toRoute<PreviewRoute>()
                    PreviewScreen(draftId = route.draftId, friendId = route.friendId, onBack = { nav.popBackStack() })
                }
                composable<FriendsRoute> {
                    FriendsScreen(
                        onAddFriend = { nav.navigate(AddFriendRoute()) },
                        onOpenFriend = { nav.navigate(FriendDetailRoute(it)) },
                        onOpenBlocked = { nav.navigate(BlockedRoute) },
                    )
                }
                composable<InboxRoute> {
                    InboxScreen(onBack = { nav.popBackStack() }, onOpenFriend = { nav.navigate(FriendDetailRoute(it)) })
                }
                composable<AddFriendRoute> { entry ->
                    AddFriendScreen(prefill = entry.toRoute<AddFriendRoute>().prefill, onBack = { nav.popBackStack() })
                }
                composable<FriendDetailRoute> { entry ->
                    FriendDetailScreen(
                        friendId = entry.toRoute<FriendDetailRoute>().id,
                        onBack = { nav.popBackStack() },
                        onOpenPermissions = { nav.navigate(PermissionsRoute(it)) },
                    )
                }
                composable<PermissionsRoute> { entry ->
                    ContactPermissionsScreen(contactId = entry.toRoute<PermissionsRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<BlockedRoute> { BlockedScreen(onBack = { nav.popBackStack() }) }
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
        if (onTab) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = current?.hasRoute<HomeRoute>() == true,
                    onClick = { nav.switchTab(HomeRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                )
                NavigationBarItem(
                    selected = current?.hasRoute<FriendsRoute>() == true,
                    onClick = { nav.switchTab(FriendsRoute) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_friends)) },
                )
                NavigationBarItem(
                    selected = current?.hasRoute<CreateRoute>() == true,
                    onClick = { nav.switchTab(CreateRoute) },
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_create)) },
                )
            }
        }
    }
}

/** Tabs keep one copy each on the back stack and remember their scroll state. */
private fun <T : Any> NavHostController.switchTab(route: T) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Prefix for "open Add friend with this ID" (invite links and QR codes). */
const val DESTINATION_ADD_FRIEND = "add_friend:"
