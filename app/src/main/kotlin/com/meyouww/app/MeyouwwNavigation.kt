package com.meyouww.app

import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.meyouww.core.alarm.AlarmScheduler
import com.meyouww.core.designsystem.component.kasukabeBackdrop
import com.meyouww.core.designsystem.crayon.crayonSurface
import com.meyouww.core.designsystem.theme.LocalCrayonGrain
import com.meyouww.core.designsystem.theme.LocalReducedMotion
import com.meyouww.core.designsystem.theme.MeyouwwTheme
import com.meyouww.core.notifications.chat.ChatNotifications
import com.meyouww.core.notifications.push.AndroidPushNotifications
import com.meyouww.feature.alarms.AlarmsScreen
import com.meyouww.feature.alarms.SendAlarmScreen
import com.meyouww.feature.chat.ChatListScreen
import com.meyouww.feature.chat.ChatScreen
import com.meyouww.feature.chat.SafetyScreen
import com.meyouww.feature.friends.AddFriendScreen
import com.meyouww.feature.friends.BlockedScreen
import com.meyouww.feature.friends.ContactPermissionsScreen
import com.meyouww.feature.friends.FriendDetailScreen
import com.meyouww.feature.friends.FriendsScreen
import com.meyouww.feature.home.AddEventScreen
import com.meyouww.feature.home.HomeScreen
import com.meyouww.feature.inbox.InboxHeart
import com.meyouww.feature.inbox.InboxScreen
import com.meyouww.feature.inbox.WaitingWallpapers
import com.meyouww.feature.inbox.WallpaperHistoryScreen
import com.meyouww.feature.onboarding.AgeGateMode
import com.meyouww.feature.onboarding.AgeGateScreen
import com.meyouww.feature.onboarding.PermissionsScreen
import com.meyouww.feature.onboarding.ProfileSetupScreen
import com.meyouww.feature.onboarding.SignInScreen
import com.meyouww.feature.onboarding.WelcomeScreen
import com.meyouww.feature.settings.DeliverySetupScreen
import com.meyouww.feature.settings.LegalLinks
import com.meyouww.feature.settings.LicensesScreen
import com.meyouww.feature.settings.SettingsScreen
import com.meyouww.feature.settings.SystemSettings
import com.meyouww.feature.studio.DraftsScreen
import com.meyouww.feature.studio.PreviewScreen
import com.meyouww.feature.studio.StudioScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import java.time.LocalDate

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
@Serializable private data class StudioRoute(val draftId: String? = null, val friendId: String? = null, val photo: Boolean = false)
@Serializable private data class PreviewRoute(val draftId: String, val friendId: String? = null)
@Serializable private data class AddEventRoute(val date: String)
@Serializable private data object AlarmsRoute
@Serializable private data class SendAlarmRoute(val peerId: String? = null)
@Serializable private data object ChatsRoute
@Serializable private data class ChatRoute(val conversationId: String? = null, val peerId: String? = null)
@Serializable private data class SafetyRoute(val peerId: String)
@Serializable private data object WallpaperHistoryRoute
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
    // The sky (or starry night) behind every screen, as on the Kuchhu-chan site.
    Surface(
        modifier = Modifier.fillMaxSize().kasukabeBackdrop(),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        when (phase) {
            AppPhase.Loading -> Unit // the splash screen stays up meanwhile
            AppPhase.SignedOut -> SignedOutNav()
            AppPhase.NeedsAgeGate -> LegalAware { terms, privacy ->
                AgeGateScreen(AgeGateMode.AfterSignIn, onDone = viewModel::recheck, onOpenTerms = terms, onOpenPrivacy = privacy)
            }
            AppPhase.NeedsProfile -> ProfileSetupScreen(onDone = viewModel::recheck)
            AppPhase.NeedsPermissions -> PermissionsScreen(onDone = viewModel::permissionsDone)
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
    NavHost(
        navController = nav,
        startDestination = WelcomeRoute,
        enterTransition = { slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { scaleOut(tween(260), targetScale = 0.9f) + slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(220)) },
    ) {
        composable<WelcomeRoute> {
            // Sign in first: Google can answer the age question; otherwise it is asked right after.
            WelcomeScreen(onGetStarted = { nav.navigate(SignInRoute) })
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
            LegalAware { terms, privacy -> SignInScreen(onSignedIn = {}, onOpenTerms = terms, onOpenPrivacy = privacy) }
        }
    }
}

/** The main app: Home and Friends tabs, plus the screens they open. */
@Composable
private fun MainNav(pendingDestination: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val reducedMotion = LocalReducedMotion.current
    val destination by pendingDestination.collectAsStateWithLifecycle()
    LaunchedEffect(destination) {
        val target = destination ?: return@LaunchedEffect
        when {
            target == AndroidPushNotifications.DESTINATION_DELIVERY_SETUP -> nav.navigate(DeliverySetupRoute) { launchSingleTop = true }
            target == AndroidPushNotifications.DESTINATION_INBOX -> nav.navigate(InboxRoute) { launchSingleTop = true }
            target.startsWith(DESTINATION_ADD_FRIEND) -> nav.navigate(AddFriendRoute(target.removePrefix(DESTINATION_ADD_FRIEND))) { launchSingleTop = true }
            target == AlarmScheduler.DESTINATION_ALARMS -> nav.navigate(AlarmsRoute) { launchSingleTop = true }
            target.startsWith(ChatNotifications.DESTINATION_PREFIX) ->
                nav.navigate(ChatRoute(conversationId = target.removePrefix(ChatNotifications.DESTINATION_PREFIX))) { launchSingleTop = true }
        }
        pendingDestination.value = null
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    val onTab = current?.hasRoute<HomeRoute>() == true || current?.hasRoute<FriendsRoute>() == true ||
        current?.hasRoute<CreateRoute>() == true || current?.hasRoute<ChatsRoute>() == true
    val chatBadge: ChatBadgeViewModel = hiltViewModel()
    val unreadChats by chatBadge.unread.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            // Screens slide in from the right and back out as you swipe back (the predictive
            // back gesture plays the pop transition along with your finger).
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                enterTransition = { if (reducedMotion) EnterTransition.None else slideInHorizontally(tween(240)) { it / 12 } + fadeIn(tween(180)) },
                exitTransition = { if (reducedMotion) ExitTransition.None else slideOutHorizontally(tween(200)) { -it / 24 } + fadeOut(tween(160)) },
                popEnterTransition = { if (reducedMotion) EnterTransition.None else slideInHorizontally(tween(240)) { -it / 12 } + fadeIn(tween(180)) },
                popExitTransition = { if (reducedMotion) ExitTransition.None else slideOutHorizontally(tween(200)) { it / 12 } + fadeOut(tween(160)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                        onOpenDeliverySetup = { nav.navigate(DeliverySetupRoute) },
                        onAddFriend = { nav.navigate(AddFriendRoute()) },
                        onAddEvent = { nav.navigate(AddEventRoute(it.toString())) },
                        heart = { InboxHeart(onClick = { nav.navigate(InboxRoute) }) },
                        onAlarms = { nav.navigate(AlarmsRoute) },
                        banner = {
                            UpdateBanner()
                            WaitingWallpapers(onOpenInbox = { nav.navigate(InboxRoute) })
                        },
                    )
                }
                composable<CreateRoute> {
                    DraftsScreen(onNew = { nav.navigate(StudioRoute()) }, onOpen = { nav.navigate(StudioRoute(it)) })
                }
                composable<StudioRoute> { entry ->
                    val route = entry.toRoute<StudioRoute>()
                    StudioScreen(
                        draftId = route.draftId,
                        onBack = { nav.popBackStack() },
                        onPreview = { nav.navigate(PreviewRoute(it, route.friendId)) },
                        startWithPhoto = route.photo,
                    )
                }
                composable<PreviewRoute> { entry ->
                    val route = entry.toRoute<PreviewRoute>()
                    PreviewScreen(
                        draftId = route.draftId,
                        friendId = route.friendId,
                        onBack = { nav.popBackStack() },
                        // After sending, go back to wherever drawing started: a chat, a friend's page or Create.
                        onSent = { nav.popBackStack<StudioRoute>(inclusive = true) || nav.popBackStack() },
                    )
                }
                composable<FriendsRoute> {
                    FriendsScreen(
                        onAddFriend = { nav.navigate(AddFriendRoute()) },
                        onOpenFriend = { nav.navigate(ChatRoute(peerId = it)) },
                        onOpenBlocked = { nav.navigate(BlockedRoute) },
                    )
                }
                composable<InboxRoute> {
                    InboxScreen(
                        onBack = { nav.popBackStack() },
                        onOpenFriend = { nav.navigate(FriendDetailRoute(it)) },
                        onOpenHistory = { nav.navigate(WallpaperHistoryRoute) },
                    )
                }
                composable<ChatsRoute> {
                    ChatListScreen(
                        onOpenChat = { nav.navigate(ChatRoute(conversationId = it)) },
                        onStartChat = { nav.navigate(ChatRoute(peerId = it)) },
                        onAddFriend = { nav.navigate(AddFriendRoute()) },
                        onManageFriends = { nav.navigate(FriendsRoute) },
                    )
                }
                composable<ChatRoute> { entry ->
                    val route = entry.toRoute<ChatRoute>()
                    ChatScreen(
                        conversationId = route.conversationId,
                        peerId = route.peerId,
                        onBack = { nav.popBackStack() },
                        onOpenSafety = { nav.navigate(SafetyRoute(it)) },
                        onDraw = { nav.navigate(StudioRoute(friendId = it)) },
                        onSendPhoto = { nav.navigate(StudioRoute(friendId = it, photo = true)) },
                        onAlarm = { nav.navigate(SendAlarmRoute(it)) },
                        onReminder = { nav.navigate(AddEventRoute(LocalDate.now().toString())) },
                        onOpenFriend = { nav.navigate(FriendDetailRoute(it)) },
                    )
                }
                composable<AddEventRoute> { entry ->
                    AddEventScreen(
                        initialDate = runCatching { LocalDate.parse(entry.toRoute<AddEventRoute>().date) }.getOrDefault(LocalDate.now()),
                        onBack = { nav.popBackStack() },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable<AlarmsRoute> {
                    AlarmsScreen(onBack = { nav.popBackStack() }, onSetForMe = { nav.navigate(SendAlarmRoute()) })
                }
                composable<SendAlarmRoute> { entry ->
                    SendAlarmScreen(
                        peerId = entry.toRoute<SendAlarmRoute>().peerId,
                        onBack = { nav.popBackStack() },
                        onDone = { nav.popBackStack() },
                    )
                }
                composable<SafetyRoute> { entry ->
                    SafetyScreen(peerId = entry.toRoute<SafetyRoute>().peerId, onBack = { nav.popBackStack() })
                }
                composable<WallpaperHistoryRoute> {
                    WallpaperHistoryScreen(onBack = { nav.popBackStack() })
                }
                composable<AddFriendRoute> { entry ->
                    AddFriendScreen(prefill = entry.toRoute<AddFriendRoute>().prefill, onBack = { nav.popBackStack() })
                }
                composable<FriendDetailRoute> { entry ->
                    FriendDetailScreen(
                        friendId = entry.toRoute<FriendDetailRoute>().id,
                        onBack = { nav.popBackStack() },
                        onOpenPermissions = { nav.navigate(PermissionsRoute(it)) },
                        onDraw = { nav.navigate(StudioRoute(friendId = it)) },
                        onSendPhoto = { nav.navigate(StudioRoute(friendId = it, photo = true)) },
                        onChat = { nav.navigate(ChatRoute(peerId = it)) },
                        onAlarm = { nav.navigate(SendAlarmRoute(it)) },
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
            val crayon = MeyouwwTheme.crayon
            val navColors = NavigationBarItemDefaults.colors(
                indicatorColor = crayon.yellow,
                selectedIconColor = crayon.onFill,
                selectedTextColor = crayon.onFill,
                unselectedIconColor = crayon.textSoft,
                unselectedTextColor = crayon.textSoft,
            )
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).crayonSurface(
                    fill = MaterialTheme.colorScheme.surface,
                    outline = crayon.outline,
                    seed = 918,
                    cornerRadius = 24.dp,
                    strokeWidth = 2.dp,
                    shadow = crayon.shadow,
                    shadowOffsetX = 3.dp,
                    shadowOffsetY = 3.dp,
                    grain = LocalCrayonGrain.current,
                ),
            ) {
                NavigationBarItem(
                    selected = current?.hasRoute<HomeRoute>() == true,
                    onClick = { nav.switchTab(HomeRoute) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = crayon.blue,
                        selectedIconColor = crayon.onFill,
                        selectedTextColor = crayon.onFill,
                        unselectedIconColor = crayon.textSoft,
                        unselectedTextColor = crayon.textSoft,
                    ),
                )
                NavigationBarItem(
                    selected = current?.hasRoute<ChatsRoute>() == true || current?.hasRoute<FriendsRoute>() == true,
                    onClick = { nav.switchTab(ChatsRoute) },
                    icon = {
                        BadgedBox(badge = { if (unreadChats > 0) Badge { Text(if (unreadChats > 99) "99+" else unreadChats.toString()) } }) {
                            Icon(Icons.Filled.Person, contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(R.string.tab_chats)) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = crayon.bubblegum,
                        selectedIconColor = crayon.onFill,
                        selectedTextColor = crayon.onFill,
                        unselectedIconColor = crayon.textSoft,
                        unselectedTextColor = crayon.textSoft,
                    ),
                )
                NavigationBarItem(
                    selected = current?.hasRoute<CreateRoute>() == true,
                    onClick = { nav.switchTab(CreateRoute) },
                    icon = { Icon(Icons.Filled.Create, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_create)) },
                    colors = navColors,
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
