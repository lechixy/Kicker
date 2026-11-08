package com.lechixy.kick

import android.animation.ValueAnimator
import android.app.PictureInPictureUiState
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.rememberPlayerHostState
import com.lechixy.kick.data.settings.local.SettingsBackupManager
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.screens.search.SearchScreen
import com.lechixy.kick.screens.channel.ChannelScreen
import com.lechixy.kick.screens.channel.ChannelViewModel
import com.lechixy.kick.screens.following.FollowingScreen
import com.lechixy.kick.screens.following.FollowingViewModel
import com.lechixy.kick.screens.home.HomeScreen
import com.lechixy.kick.screens.home.HomeViewModel
import com.lechixy.kick.screens.settings.SettingsBackupHost
import com.lechixy.kick.screens.settings.SettingsScreen
import com.lechixy.kick.screens.settings.general.GeneralSettingsScreen
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import com.lechixy.kick.util.LanguageUtils.getCurrentAppLanguage
import com.lechixy.kick.util.findActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    internal var inPipMode by mutableStateOf(false)
        private set
    internal var enteringPip by mutableStateOf(false)
        private set

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35) enteringPip = pipState.isTransitioningToPip && !inPipMode
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode = isInPictureInPictureMode
        enteringPip = false
    }

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var settingsBackupManager: SettingsBackupManager

    override fun onStart() {
        super.onStart()
        com.lechixy.kick.data.common.remote.NetworkMonitor.setForeground(true)
    }

    override fun onStop() {
        com.lechixy.kick.data.common.remote.NetworkMonitor.setForeground(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        syncLanguageWithDataStore()
    }

    private fun syncLanguageWithDataStore() {
        val systemLang = getCurrentAppLanguage(this)

        lifecycleScope.launch {
            val savedLang = settingsRepository.get(SettingsKeys.APP_LANGUAGE).first()
            // Sistemdeki dil ile DataStore farklıysa DataStore'u güncelle
            if (systemLang.isNotEmpty() && systemLang != savedLang) {
                settingsRepository.set(SettingsKeys.APP_LANGUAGE, systemLang)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        inPipMode = isInPictureInPictureMode
        enableEdgeToEdge()
        var nativeSplashDismissed by mutableStateOf(false)
        splashScreen.setOnExitAnimationListener { provider ->
            provider.remove()
            nativeSplashDismissed = true
        }

        lifecycleScope.launch {
            val currentSystemLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()

            settingsRepository.get(SettingsKeys.APP_LANGUAGE).first().let { savedLang ->
                if (currentSystemLocale.isNotEmpty() && currentSystemLocale != savedLang) {
                    settingsRepository.set(SettingsKeys.APP_LANGUAGE, currentSystemLocale)
                } else if (currentSystemLocale.isEmpty() && savedLang.isNotEmpty()) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(
                            savedLang
                        )
                    )
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settingsRepository
            ) {
                val repository = LocalSettingsRepository.current
                val themeSettings: Pair<String, Boolean>? by remember(repository) {
                    combine(
                        repository.get(SettingsKeys.THEME),
                        repository.get(SettingsKeys.DYNAMIC_COLOR)
                    ) { theme, dynamicColor ->
                        theme to dynamicColor
                    }
                }.collectAsStateWithLifecycle(initialValue = null)
                val theme = themeSettings?.first ?: SettingsKeys.THEME.defaultValue
                val dynamicColor = themeSettings?.second ?: SettingsKeys.DYNAMIC_COLOR.defaultValue
                var launchSplashVisible by rememberSaveable {
                    mutableStateOf(savedInstanceState == null && ValueAnimator.areAnimatorsEnabled())
                }

                val themeEnum = when (theme) {
                    "system" -> Theme.SYSTEM
                    "light" -> Theme.LIGHT
                    "dark" -> Theme.DARK
                    "amoled" -> Theme.AMOLED
                    else -> Theme.SYSTEM
                }

                KickTheme(
                    theme = themeEnum,
                    dynamicColor = dynamicColor,
                    launchSplashVisible = launchSplashVisible
                ) {
                    // KickSplash
//                    LaunchSplashHost(
//                        visible = launchSplashVisible,
//                        appReady = themeSettings != null,
//                        playAnimation = nativeSplashDismissed,
//                        onFinished = { launchSplashVisible = false }
//                    ) { KickApp() }
                    SettingsBackupHost(settingsBackupManager) { restorePending ->
                        KickApp(restorePending)
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    @StringRes val label: Int,
    val activeIcon: Int,
    val inactiveIcon: Int = activeIcon
) {
    HOME(
        R.string.home,
        R.drawable.home_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.home_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    FOLLOWING(
        R.string.following,
        R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    SEARCH(R.string.search, R.drawable.search_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    BROWSE(
        R.string.browse,
        R.drawable.browse_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.browse_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    EVENTS(
        R.string.events,
        R.drawable.local_activity_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.local_activity_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    ),
    SETTINGS(
        R.string.settings,
        R.drawable.settings_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
        R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    );

    fun getLabel(context: Context): String {
        return context.getString(this.label)
    }
}

@Composable
fun KickApp(restorePending: Boolean = false) {
    val releaseViewModel: com.lechixy.kick.screens.settings.ReleaseViewModel = hiltViewModel()
    val releaseDialogs by releaseViewModel.dialogs.collectAsStateWithLifecycle()
    var challengesOpen by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val notifications = com.lechixy.kick.data.account.AccountGraph.notifications
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) notifications.setForeground(
                true
            )
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) notifications.setForeground(
                false
            )
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        notifications.setForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer); notifications.setForeground(
            false
        )
        }
    }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val searchViewModel: com.lechixy.kick.screens.search.SearchViewModel = viewModel()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Tek yerde tutulan player oturumu; ChannelScreen, mini player ve nav bar buradan beslenir.
    val playerHost = rememberPlayerHostState()
    var chatClipId by rememberSaveable { mutableStateOf<String?>(null) }
    val openChatClip: (String) -> Unit = remember { { chatClipId = it } }
    val settingsRepository = LocalSettingsRepository.current
    LaunchedEffect(settingsRepository) {
        settingsRepository.get(SettingsKeys.CHAT_WIDTH_FRACTION).collect { savedFraction ->
            playerHost.updateChatWidthFraction(savedFraction)
        }
    }
    val pipActivity = activity as? MainActivity
    val pipActive = pipActivity?.inPipMode == true
    val enteringPip = pipActivity?.enteringPip == true
    val isInPipMode = pipActive || enteringPip

    val openChannel: (String, ExpansionSource?) -> Unit =
        { slug, source -> playerHost.open(slug, source) }
    val navType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    val useBottomBar = navType.toString().contains("NavigationBar", ignoreCase = true)
    val isScaffoldHidden = if (useBottomBar) {
        playerHost.isChannelVisible || playerHost.mode == PlayerMode.Fullscreen || isInPipMode
    } else {
        ((playerHost.mode == PlayerMode.Fullscreen ||
                (playerHost.mode == PlayerMode.Expanded && playerHost.isTheaterMode)) &&
                !playerHost.isPlayerNavigationVisible) || isInPipMode
    }

    LaunchedEffect(isScaffoldHidden, playerHost.isDirectNavigationDrag) {
        if (!playerHost.isDirectNavigationDrag) {
            val velocity = playerHost.navigationReleaseVelocity
            playerHost.navigationReleaseVelocity = 0f
            playerHost.animateNavigationOpenProgressTo(if (isScaffoldHidden) 0f else 1f, velocity)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        CompositionLocalProvider(
            com.lechixy.kick.components.channel.chat.LocalOpenChatClip provides openChatClip,
            com.lechixy.kick.screens.settings.LocalOpenChangelog provides {
                navController.navigate("settings/changelog") {
                    launchSingleTop = true
                }
            },
            com.lechixy.kick.components.LocalReleasePreview provides releaseViewModel::preview,
            com.lechixy.kick.components.account.LocalOpenChallenges provides {
                challengesOpen = true
            },
            com.lechixy.kick.components.LocalSharedTopBar provides true,
            com.lechixy.kick.components.LocalContentNavigation provides com.lechixy.kick.components.ContentNavigation(
                search = { query ->
                    navController.navigate(
                        "search?query=${
                            android.net.Uri.encode(
                                query
                            )
                        }"
                    ) { launchSingleTop = true }
                },
                category = { slug ->
                    if (playerHost.isChannelVisible) playerHost.minimize()
                    navController.navigate("browse?slug=${android.net.Uri.encode(slug)}") {
                        launchSingleTop = true
                    }
                }, channel = { openChannel(it, null) },
                recording = { slug, recording, source ->
                    playerHost.open(slug, source)
                    playerHost.playRecording(recording)
                }
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                KickNavScaffold(
                    hidden = isScaffoldHidden,
                    navigationProgress = { playerHost.navigationOpenProgress.value },
                    onNavigationWidthChanged = { playerHost.navigationWidthPx = it },
                    currentRoute = currentRoute,
                    activeChannelSlug = playerHost.pageSlug.takeIf { playerHost.isChannelVisible },
                    onChannelClick = { openChannel(it, null) },
                    onNavigate = { targetRoute ->
                        if (playerHost.isChannelVisible) playerHost.minimize()
                        if (currentRoute?.substringBefore("?") != targetRoute) {
                            navController.navigate(targetRoute) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = false
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            com.lechixy.kick.components.HomeTopBar(
                                forceVisible = true,
                                showAccount = true,
                                centerContent = if (currentRoute?.substringBefore("?") == "search" &&
                                    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
                                )
                                    ({
                                        com.lechixy.kick.screens.search.SearchBar(
                                            searchViewModel
                                        )
                                    }) else null
                            )
                            NavHost(
                                navController = navController,
                                modifier = Modifier
                                    .weight(1f)
                                    .consumeWindowInsets(WindowInsets.statusBars),
                                startDestination = "home",
                                enterTransition = {
                                    com.lechixy.kick.components.navigationEnter(
                                        initialState.destination.route,
                                        targetState.destination.route
                                    )
                                },
                                exitTransition = {
                                    com.lechixy.kick.components.navigationExit(
                                        initialState.destination.route,
                                        targetState.destination.route
                                    )
                                },
                                popEnterTransition = {
                                    com.lechixy.kick.components.navigationPopEnter(
                                        initialState.destination.route,
                                        targetState.destination.route
                                    )
                                },
                                popExitTransition = {
                                    com.lechixy.kick.components.navigationPopExit(
                                        initialState.destination.route,
                                        targetState.destination.route
                                    )
                                }

                            ) {
                                composable("home") {
                                    val homeViewModel: HomeViewModel = hiltViewModel()
                                    HomeScreen(
                                        viewModel = homeViewModel,
                                        onStreamClick = { channelSlug, source ->
                                            openChannel(
                                                channelSlug,
                                                source
                                            )
                                        },
                                        onFollowingClick = {
                                            navController.navigate("following") {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }

                                composable("following") {
                                    val followingViewModel: FollowingViewModel = viewModel()
                                    FollowingScreen(
                                        viewModel = followingViewModel,
                                        onStreamClick = { channelSlug, source ->
                                            openChannel(
                                                channelSlug,
                                                source
                                            )
                                        }
                                    )
                                }

                                composable(
                                    "search?query={query}",
                                    arguments = listOf(navArgument("query") { defaultValue = "" })
                                ) { entry ->
                                    SearchScreen(
                                        viewModel = searchViewModel,
                                        initialQuery = entry.arguments?.getString("query")
                                            .orEmpty(),
                                        onChannelClick = { channelSlug, source ->
                                            openChannel(
                                                channelSlug,
                                                source
                                            )
                                        }
                                    )
                                }

                                composable(
                                    "browse?slug={slug}",
                                    arguments = listOf(navArgument("slug") { defaultValue = "" })
                                ) { entry ->
                                    com.lechixy.kick.screens.browse.BrowseScreen(
                                        onChannelClick = { openChannel(it, null) },
                                        selectedSlug = entry.arguments?.getString("slug")
                                    )
                                }
                                composable("events") {
                                    com.lechixy.kick.screens.events.EventsScreen(onChannelClick = {
                                        openChannel(
                                            it,
                                            null
                                        )
                                    })
                                }

                                composable("settings") {
                                    SettingsScreen(
                                        onBackClick = { navController.popBackStack() },
                                        onSubsettingClick = { subsettingRoute ->
                                            navController.navigate(
                                                "settings/${subsettingRoute.name.lowercase()}"
                                            )
                                        }
                                    )
                                }

                                composable(
                                    route = "settings/{settingRoute}",
                                    arguments = listOf(navArgument("settingRoute") {
                                        type = NavType.StringType
                                    })
                                ) { backStackEntry ->
                                    val settingRoute =
                                        backStackEntry.arguments?.getString("settingRoute") ?: ""

                                    when (settingRoute) {
                                        "changelog" -> com.lechixy.kick.screens.settings.ChangelogScreen(
                                            releaseViewModel,
                                            { navController.popBackStack() })

                                        "developer" -> com.lechixy.kick.screens.settings.DeveloperToolsScreen(
                                            onBackClick = { navController.popBackStack() },
                                            onTriggerLive = {
                                                playerHost.triggerLiveAnnouncement(
                                                    System.nanoTime()
                                                )
                                            },
                                            canTriggerLive = playerHost.channel?.livestream != null && playerHost.recording == null && playerHost.mode != PlayerMode.Hidden
                                        )

                                        "chat" -> com.lechixy.kick.screens.settings.SettingsCategoryScreen(
                                            com.lechixy.kick.screens.settings.SettingsDestinations.CHAT,
                                            { navController.popBackStack() })

                                        "general" -> {
                                            GeneralSettingsScreen(
                                                onBackClick = { navController.popBackStack() }
                                            )
                                        }

                                        "customization" -> com.lechixy.kick.screens.settings.customization.CustomizationSettingsScreen(
                                            onBackClick = { navController.popBackStack() })

                                        "playback" -> {
                                            com.lechixy.kick.screens.settings.playback.PlayerPlaybackSettingsScreen(
                                                onBackClick = { navController.popBackStack() }
                                            )
                                        }

                                        else -> {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                Arrangement.Center,
                                                Alignment.CenterVertically
                                            ) {
                                                Text("Unknown route: $settingRoute")
                                            }
                                        }
                                    }
                                }
                            }

                        }
                        val activeSlug = playerHost.slug
                        val activeChannelVm = if (activeSlug != null) viewModel<ChannelViewModel>(key = activeSlug) else null
                        if (activeSlug != null && activeChannelVm != null) {
                            val activeChannelState by activeChannelVm.uiState.collectAsStateWithLifecycle()
                            LaunchedEffect(activeSlug, activeChannelState.channel) {
                                activeChannelState.channel?.let { detail ->
                                    playerHost.onChannelLoaded(activeSlug, detail)
                                }
                            }
                            LaunchedEffect(activeSlug, activeChannelState.liveAnnouncementId) {
                                val announcementId =
                                    activeChannelState.liveAnnouncementId ?: return@LaunchedEffect
                                playerHost.triggerLiveAnnouncement(announcementId)
                                activeChannelVm.consumeLiveAnnouncement(announcementId)
                            }
                        }

                        PlayerHost(
                            host = playerHost,
                            navigationRailAvailable = !useBottomBar,
                            isInPipMode = pipActive,
                            enteringPip = enteringPip,
                            onMinimize = playerHost::minimize,
                            onExpand = playerHost::expand,
                            onClose = playerHost::close,
                            liveChatViewModel = activeChannelVm,
                            pageContent = { slug ->
                                ChannelScreen(
                                    channelSlug = slug,
                                    playerHost = playerHost,
                                    viewModel = viewModel(key = slug),
                                    onChannelLoaded = { playerHost.onChannelLoaded(slug, it) },
                                    onBackClick = playerHost::minimize,
                                )
                            }
                        )

                    }
                }
                if (!isInPipMode) {
                    com.lechixy.kick.components.InAppNotificationHost(
                        modifier = Modifier.align(Alignment.TopCenter),
                        enabled = !restorePending && !challengesOpen && releaseDialogs.isEmpty(),
                        onActivate = { notification ->
                            when (notification.kind) {
                                com.lechixy.kick.data.notifications.NotificationKind.WatchTimeGoal -> challengesOpen =
                                    true

                                com.lechixy.kick.data.notifications.NotificationKind.StreamStarted -> notification.channelSlug?.let {
                                    openChannel(
                                        it,
                                        null
                                    )
                                }
                                com.lechixy.kick.data.notifications.NotificationKind.SleepTimerStarted,
                                com.lechixy.kick.data.notifications.NotificationKind.SleepTimerStopped -> Unit
                            }
                        })
                    com.lechixy.kick.components.account.AccountChallengeDialogHost(
                        challengesOpen,
                        { challengesOpen = false })
                    if (!restorePending && !challengesOpen) releaseDialogs.firstOrNull()?.let { request ->
                        com.lechixy.kick.components.ReleaseDialog(
                            request,
                            releaseViewModel::dismiss
                        )
                    }
                }
                if (!isInPipMode) com.lechixy.kick.components.NetworkInfoBar(
                    Modifier.align(
                        Alignment.BottomCenter
                    )
                )
                com.lechixy.kick.screens.settings.DeveloperToolsOverlay(
                    isInPipMode = isInPipMode,
                    onTriggerLive = { playerHost.triggerLiveAnnouncement(System.nanoTime()) },
                    canTriggerLive = playerHost.channel?.livestream != null && playerHost.recording == null && playerHost.mode != PlayerMode.Hidden
                )
                if (!isInPipMode) chatClipId?.let { id ->
                    key(id) {
                        com.lechixy.kick.screens.clip.ClipPlayerDialog(
                            clipId = id,
                            onClose = { chatClipId = null },
                            onWatchLivestream = { clip ->
                                chatClipId = null
                                openChannel(clip.channel.slug, null)
                                playerHost.playLive()
                            },
                            onWatchFullVideo = { clip ->
                                clip.video?.let { video ->
                                    chatClipId = null
                                    openChannel(clip.channel.slug, null)
                                    playerHost.playRecording(
                                        com.lechixy.kick.data.player.model.RecordedPlayback(
                                            id = video.id,
                                            title = video.title,
                                            durationMs = 0,
                                        )
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal fun orderedAppDestinations(savedOrder: String): List<AppDestinations> {
    val byName = AppDestinations.entries.associateBy { it.name }
    val savedDestinations = savedOrder.split(',').mapNotNull(byName::get)
    return (savedDestinations + AppDestinations.entries).distinct()
}

@Composable
internal fun KickNavScaffold(
    hidden: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    activeChannelSlug: String? = null,
    onChannelClick: (String) -> Unit = {},
    navigationProgress: (() -> Float)? = null,
    onNavigationWidthChanged: (Int) -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val type =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    val useBottomBar = type.toString().contains("NavigationBar", ignoreCase = true)
    val latestContent by rememberUpdatedState(content)
    val sharedContent = remember { movableContentOf { latestContent() } }

    val settings = LocalSettingsRepository.current
    val savedOrder by settings.get(SettingsKeys.NAVIGATION_ORDER)
        .collectAsStateWithLifecycle(SettingsKeys.NAVIGATION_ORDER.defaultValue)
    val destinations = remember(savedOrder) { orderedAppDestinations(savedOrder) }
    val railExpanded by settings.get(SettingsKeys.NAVIGATION_RAIL_EXPANDED)
        .collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    val railWidth = androidx.compose.animation.core.animateDpAsState(
        if (railExpanded) 240.dp else 80.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "rail width"
    )

    @Composable
    fun DestinationIcon(
        destination: AppDestinations,
        selected: Boolean,
        bouncy: Boolean = false,
        pressed: Boolean = false
    ) {
        val scale = remember { androidx.compose.animation.core.Animatable(1f) }
        LaunchedEffect(selected, pressed, bouncy) {
            if (bouncy && selected && !pressed) scale.snapTo(.8f)
            scale.animateTo(
                if (pressed) .8f else if (selected) 1.08f else 1f,
                androidx.compose.animation.core.spring(
                    dampingRatio = if (bouncy) .38f else 1f,
                    stiffness = 500f
                )
            )
        }
        androidx.compose.animation.Crossfade(
            targetState = selected,
            label = "navigation icon"
        ) { active ->
            Icon(
                painterResource(if (active) destination.activeIcon else destination.inactiveIcon),
                stringResource(destination.label), Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    })
        }
    }

    @Composable
    fun RowScope.BottomBarItems() {
        destinations.filter { it != AppDestinations.EVENTS }.forEach { destination ->
            val targetRoute = destination.name.lowercase()
            val isSelected = com.lechixy.kick.components.navigationDestinationSelected(
                currentRoute,
                activeChannelSlug,
                targetRoute
            )
            androidx.compose.runtime.key(destination) {
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigate(targetRoute) },
                    icon = { DestinationIcon(destination, isSelected) },
                    label = { Text(stringResource(destination.label)) }
                )
            }
        }
    }

    @Composable
    fun RailBarItems(expanded: Boolean) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            destinations.forEach { destination ->
                val targetRoute = destination.name.lowercase()
                val isSelected = com.lechixy.kick.components.navigationDestinationSelected(
                    currentRoute,
                    activeChannelSlug,
                    targetRoute
                )
                androidx.compose.runtime.key(destination) {
                    com.lechixy.kick.components.RailNavigationItem(
                        expanded = expanded,
                        selected = isSelected,
                        label = stringResource(destination.label),
                        onClick = { onNavigate(targetRoute) },
                        icon = {
                            androidx.compose.animation.Crossfade(
                                isSelected,
                                animationSpec = tween(180),
                                label = "rail icon"
                            ) { active ->
                                Icon(
                                    painterResource(if (active) destination.activeIcon else destination.inactiveIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (useBottomBar) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let {
                        if (hidden) it else it.consumeWindowInsets(
                            WindowInsets.systemBars.only(
                                WindowInsetsSides.Bottom
                            )
                        )
                    }
            ) { sharedContent() }
            AnimatedVisibility(
                visible = !hidden,
                enter = slideInVertically { it } + expandVertically(),
                exit = slideOutVertically { it } + shrinkVertically()
            ) {
                NavigationBar { BottomBarItems() }
            }
        }
    } else {
        val fallbackProgress = androidx.compose.animation.core.animateFloatAsState(
            if (hidden) 0f else 1f, label = "navigation rail"
        )
        KickRailLayout(
            navigationProgress = { navigationProgress?.invoke() ?: fallbackProgress.value },
            onNavigationWidthChanged = onNavigationWidthChanged,
            navigation = {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        Modifier
                            .layout { measurable, constraints ->
                                // Width animation should remeasure the page without recomposing its screens.
                                val width = railWidth.value.roundToPx()
                                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                                val rail = measurable.measure(
                                    constraints.copy(
                                        minWidth = width,
                                        maxWidth = width
                                    )
                                )
                                layout(rail.width, rail.height) { rail.placeRelative(0, 0) }
                            }
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val menuRotation by androidx.compose.animation.core.animateFloatAsState(
                            if (railExpanded) 90f else 0f,
                            animationSpec = tween(220),
                            label = "rail toggle"
                        )
                        Box(Modifier.fillMaxWidth().height(64.dp)) {
                            com.lechixy.kick.components.FadingAppBrand(
                                visible = railExpanded && !hidden,
                                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 56.dp),
                                enter = androidx.compose.animation.fadeIn(tween(180, delayMillis = 120)),
                                exit = androidx.compose.animation.fadeOut(tween(120))
                            )
                            val toggleBias by androidx.compose.animation.core.animateFloatAsState(
                                if (railExpanded) 1f else 0f,
                                animationSpec = tween(320, easing = FastOutSlowInEasing),
                                label = "rail toggle alignment"
                            )
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    scope.launch {
                                        settings.set(SettingsKeys.NAVIGATION_RAIL_EXPANDED, !railExpanded)
                                    }
                                },
                                modifier = Modifier.align(androidx.compose.ui.BiasAlignment(toggleBias, 0f))
                                    .padding(end = if (railExpanded) 8.dp else 0.dp)
                            ) {
                                Icon(
                                    painterResource(R.drawable.menu_24dp),
                                    stringResource(if (railExpanded) R.string.navigation_collapse else R.string.navigation_expand),
                                    modifier = Modifier.graphicsLayer { rotationZ = menuRotation }
                                )
                            }
                        }
                        val session by com.lechixy.kick.data.account.AccountGraph.auth.session.collectAsStateWithLifecycle()
                        val following = com.lechixy.kick.data.account.AccountGraph.following.rail
                        val followed by following.state.collectAsStateWithLifecycle()
                        val favorites by com.lechixy.kick.data.favorites.local.FavoriteUsersStore.favorites.collectAsStateWithLifecycle()
                        val railChannels = remember(followed.channels, favorites) {
                            com.lechixy.kick.data.following.repository.rankRailChannels(
                                followed.channels,
                                favorites
                            )
                        }
                        val lifecycle =
                            androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                        LaunchedEffect(session.signedIn, session.generation, lifecycle) {
                            if (session.signedIn) lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                                while (true) {
                                    following.refresh(); kotlinx.coroutines.delay(60_000.milliseconds)
                                }
                            }
                        }
                        androidx.compose.foundation.lazy.LazyColumn(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
                        ) {
                            item("destinations") {
                                RailBarItems(railExpanded)
                            }
                            if (session.signedIn) {
                                item("following-header") {
                                    com.lechixy.kick.components.FollowingRailHeader(railExpanded)
                                }
                                items(
                                    railChannels.size,
                                    key = { "followed:${railChannels[it].slug}" }) { index ->
                                    val channel = railChannels[index]
                                    com.lechixy.kick.components.FollowingRailItem(
                                        channel,
                                        selected = channel.slug.equals(
                                            activeChannelSlug,
                                            ignoreCase = true
                                        ),
                                        onClick = { onChannelClick(channel.slug) },
                                        expanded = railExpanded
                                    )
                                }
                                item("following-status") {
                                    when {
                                        followed.loading -> com.lechixy.kick.components.AppLoadingIndicator(
                                            Modifier
                                                .padding(12.dp)
                                                .size(24.dp)
                                        )

                                        followed.failed -> androidx.compose.material3.TextButton(
                                            onClick = following::retry
                                        ) {
                                            Text(androidx.compose.ui.res.stringResource(R.string.chat_retry))
                                        }

                                        followed.loaded && followed.channels.isEmpty() -> Text(
                                            androidx.compose.ui.res.stringResource(R.string.navigation_following_empty),
                                            Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            content = {
                CompositionLocalProvider(
                    com.lechixy.kick.components.LocalRailBrandVisible provides (railExpanded && !hidden)
                ) { sharedContent() }
            }
        )
    }
}

@Composable
internal fun KickRailLayout(
    navigationProgress: () -> Float,
    onNavigationWidthChanged: (Int) -> Unit = {},
    navigation: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Layout(
        contents = listOf(navigation, { Box(Modifier.fillMaxSize()) { content() } }),
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
    ) { (navigationItems, body), constraints ->
        val rail = navigationItems.single().measure(constraints.copy(minWidth = 0))
        onNavigationWidthChanged(rail.width)
        // Read progress during measurement so each drag event resizes the page without
        // recomposing or recreating its player, just like the side chat panel.
        val visibleWidth = (rail.width * navigationProgress().coerceIn(0f, 1f))
            .roundToInt().coerceIn(0, constraints.maxWidth)
        val page = body.single().measure(
            Constraints.fixed(
                constraints.maxWidth - visibleWidth, constraints.maxHeight
            )
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (visibleWidth > 0) rail.placeRelative(visibleWidth - rail.width, 0)
            page.placeRelative(visibleWidth, 0)
        }
    }
}
