package com.lechixy.kick.integration

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.navigation.compose.*
import com.lechixy.kick.R
import com.lechixy.kick.components.*
import com.lechixy.kick.components.account.AdaptiveModalSheet
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.following.model.FollowedChannel
import com.lechixy.kick.data.releases.GitHubRelease
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.*
import com.lechixy.kick.screens.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NavigationUpdatesUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var settings: SettingsRepository
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    private fun label(id: Int) = compose.activity.getString(id)
    @Before fun prepare() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true); it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(compose.activity.cacheDir, "navigation-updates-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }
    @After fun cleanup() { scope.cancel(); file.delete() }
    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings, LocalSharedTopBar provides true) {
                MaterialTheme { content() }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    private fun startBack(dispatcher: OnBackPressedDispatcher, progress: Float = .6f) {
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 200f, progress, BackEventCompat.EDGE_LEFT)) }
        compose.mainClock.advanceTimeByFrame()
    }
    @Test fun predictiveDialogUsesItsOwnDispatcherAndRestoresAfterCancel() {
        var dispatcher: OnBackPressedDispatcher? = null
        var dismissed = false
        compose.mainClock.autoAdvance = false
        show {
            PredictiveDialog({ dismissed = true }) {
                val owner = LocalOnBackPressedDispatcherOwner.current!!
                SideEffect { dispatcher = owner.onBackPressedDispatcher }
                Surface(Modifier.size(240.dp).testTag("panel")) { Text("Predictive panel") }
            }
        }
        compose.runOnIdle { assertNotNull(dispatcher); assertNotSame(compose.activity.onBackPressedDispatcher, dispatcher) }
        val width = compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.width
        startBack(dispatcher!!)
        assertTrue(compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.width < width)
        compose.runOnIdle { dispatcher!!.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertFalse(dismissed) }
        assertEquals(width, compose.onNodeWithTag("panel").fetchSemanticsNode().boundsInRoot.width, 1f)
        startBack(dispatcher!!)
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue(dismissed) }
    }
    @Test fun tabletAccountAndChallengeSheetSeekBackAndCancelWithoutClosing() {
        var dispatcher: OnBackPressedDispatcher? = null
        var dismissed = false
        compose.mainClock.autoAdvance = false
        show {
            AdaptiveModalSheet({ dismissed = true }) {
                val owner = LocalOnBackPressedDispatcherOwner.current!!
                SideEffect { dispatcher = owner.onBackPressedDispatcher }
                Box(Modifier.fillMaxSize().testTag("sheet")) { Text("Account or challenges") }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        val original = compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot.width
        startBack(dispatcher!!)
        assertTrue(compose.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot.width < original)
        compose.runOnIdle { dispatcher!!.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertFalse(dismissed) }
        startBack(dispatcher!!)
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertTrue(dismissed) }
    }
    @Test fun navHostScreensAndSubsettingsSupportSeekCancelAndCommit() {
        lateinit var nav: androidx.navigation.NavHostController
        compose.mainClock.autoAdvance = false
        show {
            nav = rememberNavController()
            NavHost(nav, "home", popEnterTransition = { navigationPopEnter(initialState.destination.route, targetState.destination.route) }, popExitTransition = { navigationPopExit(initialState.destination.route, targetState.destination.route) }) {
                listOf("home", "following", "settings", "settings/chat").forEach { route ->
                    composable(route) { Box(Modifier.fillMaxSize().testTag(route)) { Text("Page $route") } }
                }
            }
        }
        for (route in listOf("following", "settings", "settings/chat")) {
            compose.runOnIdle { nav.navigate(route) }
            compose.mainClock.advanceTimeBy(800)
        }
        val dispatcher = compose.activity.onBackPressedDispatcher
        val width = compose.onNodeWithTag("settings/chat").fetchSemanticsNode().boundsInRoot.width
        startBack(dispatcher)
        assertTrue(compose.onNodeWithTag("settings/chat").fetchSemanticsNode().boundsInRoot.width < width)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.mainClock.advanceTimeBy(800)
        compose.runOnIdle { assertEquals("settings/chat", nav.currentDestination?.route) }
        for (expected in listOf("settings", "following", "home")) {
            startBack(dispatcher)
            compose.runOnIdle { dispatcher.onBackPressed() }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { assertEquals(expected, nav.currentDestination?.route) }
        }
    }
    @Test fun settingsDestinationCardsHaveEqualRowHeightWhenDescriptionsWrap() {
        show { Box(Modifier.width(820.dp)) { SettingsScreen({}, {}) } }
        val titles = listOf(R.string.settings_general, R.string.settings_customization, R.string.player_playback)
        val bounds = titles.map { title ->
            compose.onNode(hasText(label(title)) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].top, bounds[1].top, 1f)
        assertEquals(bounds[0].height, bounds[1].height, 1f)
        assertEquals(bounds[0].height, bounds[2].height, 1f)
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null), "settings-tablet-update.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
    @Test fun tabletSubsettingHeaderIsCenteredAtTheContentWidth() {
        show { Box(Modifier.fillMaxWidth().testTag("window")) { SettingsTopBar("Header", {}) } }
        val window = compose.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription(label(R.string.navigation_back)).fetchSemanticsNode().boundsInRoot
        val maxWidthPx = with(compose.density) { 840.dp.toPx() }
        if (window.width > maxWidthPx + 20) assertTrue(back.left >= (window.width - maxWidthPx) / 2 - 1)
        compose.onNodeWithText("Header").assertIsDisplayed()
    }
    @Test fun changelogOpensPublishedReleaseNotesAndCanDismissThem() {
        show {
            ChangelogContent(ReleaseHistoryState(listOf(GitHubRelease("v2.0", name = "Release two", body = "# Actual notes\n\n- Change one")), loaded = true), {}, {})
        }
        compose.onNodeWithText("Release two").performClick()
        compose.onNodeWithText(label(android.R.string.ok)).assertIsDisplayed().performClick()
        compose.onNodeWithText("Release two").assertIsDisplayed()
    }
    @Test fun followingAvatarLabelSelectionAndOpeningUseTheChannelSlug() {
        var opened: String? = null
        var active by mutableStateOf<String?>("rraenee")
        val channel = FollowedChannel("rraenee", "RRaenee", null, true, null, null, 26233, true)
        show { Column(Modifier.width(80.dp)) {
            FollowingRailItem(channel, channel.slug == active, { opened = channel.slug })
        } }
        compose.onNode(hasText("RRaenee") and isSelectable()).assertIsSelected().performClick()
        compose.runOnIdle { assertEquals("rraenee", opened); active = null }
        compose.onNode(hasText("RRaenee") and isSelectable()).assertIsNotSelected()
    }
    @Test fun expandedRailShowsTitleWithoutViewerCount() {
        var expanded by mutableStateOf(false)
        val channel = FollowedChannel("rraenee", "RRaenee", null, true, "A live stream title", null, 26233, true)
        show {
            Column(Modifier.width(240.dp)) { FollowingRailItem(channel, false, {}, expanded = expanded) }
        }
        compose.onNodeWithText("A live stream title").assertDoesNotExist()
        compose.runOnIdle { expanded = true }
        compose.onNodeWithText("A live stream title").assertIsDisplayed()
        val count = java.text.NumberFormat.getIntegerInstance(compose.activity.resources.configuration.locales[0]).format(26233)
        compose.onNodeWithText(compose.activity.getString(R.string.navigation_following_viewers, count)).assertDoesNotExist()
    }
    @Test fun followingRowsAnimateTogetherAndReturnToCompactSpacing() {
        var expanded by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        show {
            val width by androidx.compose.animation.core.animateDpAsState(if (expanded) 240.dp else 80.dp,
                animationSpec = androidx.compose.animation.core.tween(400), label = "test rail width")
            Column(Modifier.width(width)) {
                FollowingRailHeader(expanded)
                repeat(3) { index ->
                    FollowingRailItem(FollowedChannel("channel$index", "Long channel name $index", null, true,
                        if (index == 1) "A very long stream title that wraps over several lines while expanding" else "Title $index",
                        null, 100, true), false, {}, expanded, Modifier.testTag("row$index"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val initial = (0..2).map { compose.onNodeWithTag("row$it").fetchSemanticsNode().boundsInRoot }
        for (target in listOf(true, false)) {
            compose.runOnIdle { expanded = target }
            repeat(12) {
                compose.mainClock.advanceTimeBy(40)
                (0..2).forEach { index ->
                    val bounds = compose.onNodeWithTag("row$index").fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.height >= with(compose.density) { 48.dp.toPx() } - 1f)
                    if (index > 0) {
                        val previous = compose.onNodeWithTag("row${index - 1}").fetchSemanticsNode().boundsInRoot
                        assertEquals(previous.bottom, bounds.top, 1f)
                    }
                }
            }
            val settled = compose.onNodeWithTag("row0").fetchSemanticsNode().boundsInRoot
            if (target) assertTrue(settled.height < initial[0].height)
            else assertEquals(initial[0].height, settled.height, 1f)
        }
    }
    @Test fun chatterProfileRendersTheChannelSubscriptionBadgeForItsMonths() {
        val assets = listOf(com.lechixy.kick.data.channel.model.SubscriberBadge(24, months = 24,
            badgeImage = com.lechixy.kick.data.channel.model.SubscriberBadgeImage(src = "android.resource://com.lechixy.kick/drawable/reward_case")))
        val badges = com.lechixy.kick.components.channel.chat.resolveProfileBadges(
            com.lechixy.kick.data.chat.model.ChatIdentity(), 26, assets)
        show {
            Box(Modifier.width(420.dp)) {
                com.lechixy.kick.components.channel.chat.KickUserProfileCard(
                    com.lechixy.kick.components.channel.chat.UserProfileCardUiState(username = "Subscriber", subscribedMonths = 26,
                        badges = badges, isLoading = false))
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.badge_subscriber_months, 26)).assertIsDisplayed()
    }
    @Test fun recommendedLanguageDefaultUsesRegionAndPreservesExplicitChoice() = runBlocking {
        val expected = com.lechixy.kick.util.systemRecommendedStreamLanguage()
        assertEquals(expected, settings.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).first())
        settings.set(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE, "EN-us")
        assertEquals("en", settings.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).first())
        settings.reset(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE)
        assertEquals(expected, settings.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).first())
    }
}
