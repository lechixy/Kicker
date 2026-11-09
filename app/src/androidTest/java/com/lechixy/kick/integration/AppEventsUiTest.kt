package com.lechixy.kick.integration

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.lechixy.kick.R
import com.lechixy.kick.components.*
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.notifications.*
import com.lechixy.kick.data.releases.*
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.*
import com.lechixy.kick.screens.settings.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale

class AppEventsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var settings: SettingsRepository
    private lateinit var file: File
    private lateinit var scope: CoroutineScope
    @Before fun prepare() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true); it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        file = File(compose.activity.cacheDir, "app-events-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }))
    }
    @After fun clean() {
        scope.cancel(); file.delete()
        AccountGraph.notifications.notifications.value.forEach { AccountGraph.notifications.dismiss(it.id) }
    }
    private fun context(language: String) = compose.activity.createConfigurationContext(
        Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    private fun show(language: String = "tr", content: @Composable () -> Unit) {
        val localized = context(language)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides localized.resources.configuration,
                LocalSettingsRepository provides settings, LocalSharedTopBar provides true) { MaterialTheme { content() } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun turkishSearchFindsEditableChatSettingsAndClearRestoresCategories() {
        val localized = context("tr")
        show { SettingsHost(null, {}) }
        compose.onNodeWithText(localized.getString(R.string.settings_search)).performTextInput(localized.getString(R.string.player_chat_font))
        compose.onNode(hasText(localized.getString(R.string.player_chat_font)) and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.settings_app_language)).assertDoesNotExist()
        compose.onNodeWithContentDescription(localized.getString(R.string.settings_clear_search)).performClick()
        compose.onNodeWithText(localized.getString(R.string.chat)).assertIsDisplayed()
    }
    @Test fun generalCategoryDoesNotContainChatControls() {
        val localized = context("tr")
        show { SettingsCategoryScreen(SettingsDestinations.GENERAL, {}) }
        compose.onNodeWithText(localized.getString(R.string.settings_app_language)).assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.player_chat_font)).assertDoesNotExist()
        compose.onNodeWithText(localized.getString(R.string.chat_show_timestamps)).assertDoesNotExist()
    }
    @Test fun chatSettingsOpenFullscreenPositionPreview() {
        val language = compose.activity.resources.configuration.locales[0].toLanguageTag()
        val localized = context(language)
        show(language) { SettingsCategoryScreen(SettingsDestinations.CHAT, {}) }
        val edit = localized.getString(R.string.fullscreen_chat_edit_position)
        val done = localized.getString(R.string.fullscreen_chat_done)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(edit))
        compose.onNodeWithText(edit).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(done).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(edit).assertIsDisplayed()
        compose.onNodeWithText(done).performClick()
        compose.onNodeWithContentDescription(edit).assertDoesNotExist()
    }
    @Test fun englishSearchUsesEnglishResources() {
        val localized = context("en")
        show("en") { SettingsHost(null, {}) }
        compose.onNodeWithText(localized.getString(R.string.settings_search)).performTextInput(localized.getString(R.string.settings_volume_sync))
        compose.onNode(hasText(localized.getString(R.string.settings_volume_sync)) and !hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText(localized.getString(R.string.settings_chat_connection)).assertDoesNotExist()
    }
    @Test fun notificationRendersHtmlTitleWithoutCloseButtonAndSupportsClick() {
        var opened = 0; var dismissed = 0
        show {
            Box(Modifier.width(360.dp)) {
                NotificationCard(AppNotification("test", NotificationKind.StreamStarted, 1, 1,
                    "<strong>RRaenee</strong> just went live!", "A very long description ".repeat(40), "rraenee"),
                    { opened++ }, { dismissed++ })
            }
        }
        compose.onNodeWithText("RRaenee just went live!").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null), "notification-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("RRaenee just went live!").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithContentDescription(context("tr").getString(R.string.notification_dismiss)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, dismissed); assertEquals(1, opened) }
    }
    @Test fun notificationQueueSlidesInAndOpensTheCorrectDestination() {
        var activated: AppNotification? = null
        compose.mainClock.autoAdvance = false
        show { Box(Modifier.fillMaxSize()) { InAppNotificationHost({ activated = it }) } }
        compose.runOnIdle { AccountGraph.notifications.preview(NotificationKind.StreamStarted) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(context("tr").getString(R.string.notification_live_title, "rraenee")).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals("rraenee", activated?.channelSlug) }
    }
    @Test fun notificationsDismissLeftRightAndUpWithoutOpeningTheirDestination() {
        var opened = 0
        compose.mainClock.autoAdvance = false
        show { Box(Modifier.fillMaxSize()) { InAppNotificationHost({ opened++ }) } }
        val title = context("tr").getString(R.string.notification_live_title, "rraenee")
        for (direction in listOf("left", "right", "up")) {
            compose.runOnIdle { AccountGraph.notifications.preview(NotificationKind.StreamStarted) }
            compose.mainClock.advanceTimeBy(700)
            compose.onNode(hasText(title) and hasClickAction()).performTouchInput {
                when (direction) { "left" -> swipeLeft(); "right" -> swipeRight(); else -> swipeUp() }
            }
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText(title).assertDoesNotExist()
            compose.runOnIdle { assertEquals(0, opened) }
        }
    }
    @Test fun shortNotificationDragSnapsBackWithoutDismissing() {
        var dismissed = 0
        compose.mainClock.autoAdvance = false
        show {
            NotificationCard(AppNotification("short", NotificationKind.StreamStarted, 1, 1,
                "Short drag", "Description", "rraenee"), {}, { dismissed++ })
        }
        compose.onNode(hasText("Short drag") and hasClickAction()).performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(30f, 0f), durationMillis = 500)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Short drag").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, dismissed) }
    }
    @Test fun pinnedMessageRestoresOnLastSwipedSideAndHideKeepsThatSide() {
        compose.mainClock.autoAdvance = false
        show {
            Box(Modifier.width(360.dp).height(500.dp)) {
                com.lechixy.kick.components.channel.chat.PinnedMessageOverlay(
                    com.lechixy.kick.data.chat.model.PinnedMessageInfo(message = com.lechixy.kick.data.chat.model.ChatMessage(
                        id = "pin", content = "Pinned test message", sender = com.lechixy.kick.data.chat.model.ChatSender(username = "Alice"))),
                    14, emptyList(), null, "test-channel", Modifier.fillMaxWidth())
            }
        }
        val pinLabel = context("tr").getString(R.string.chat_show_pinned)
        val hideLabel = context("tr").getString(R.string.hide)
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText(hideLabel).performClick()
        compose.mainClock.advanceTimeBy(800)
        val initialRight = compose.onNodeWithContentDescription(pinLabel).fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithContentDescription(pinLabel).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Pinned test message")), useUnmergedTree = true)
            .performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(900)
        val left = compose.onNodeWithContentDescription(pinLabel).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(left < initialRight)
        compose.onNodeWithContentDescription(pinLabel).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Pinned test message").assertIsDisplayed()
        compose.onNodeWithText(hideLabel).performClick()
        compose.mainClock.advanceTimeBy(800)
        assertEquals(left, compose.onNodeWithContentDescription(pinLabel).fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithContentDescription(pinLabel).performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("Pinned test message")), useUnmergedTree = true)
            .performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(900)
        val right = compose.onNodeWithContentDescription(pinLabel).fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(right > left)
    }
    @Test fun notificationsWaitWhileADialogIsOnTop() {
        var enabled by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        show { InAppNotificationHost({}, enabled = enabled) }
        compose.runOnIdle { AccountGraph.notifications.preview(NotificationKind.WatchTimeGoal) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithText(context("tr").getString(R.string.notification_reward_title)).assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(context("tr").getString(R.string.notification_reward_title)).assertIsDisplayed()
    }
    @Test fun liveRevealStartsWithAcquiringSignalAndFinishesAfterItsExit() {
        var finished = false
        compose.mainClock.autoAdvance = false
        show { Box(Modifier.width(380.dp).height(220.dp)) { StreamerIsLiveOverlay("RRaenee", 1, { finished = true }) } }
        compose.onNodeWithText(context("tr").getString(R.string.live_signal_incoming)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText(context("tr").getString(R.string.live_reveal_headline)).assertIsDisplayed()
        compose.onNodeWithText("RRaenee").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(compose.activity.getExternalFilesDir(null), "live-reveal-preview.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { assertFalse(finished) }
        compose.mainClock.advanceTimeBy(3_600)
        compose.runOnIdle { assertTrue(finished) }
    }
    @Test fun liveRevealSwitchesAllKickEffectsToTheMaterialPalette() {
        runBlocking { settings.set(SettingsKeys.DYNAMIC_COLOR, false) }
        compose.mainClock.autoAdvance = false
        show {
            MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF3F51B5),
                tertiary = androidx.compose.ui.graphics.Color(0xFFE91E63))) {
                Box(Modifier.width(380.dp).height(220.dp)) { StreamerIsLiveOverlay("RRaenee", 77, {}) }
            }
        }
        compose.mainClock.advanceTimeBy(1_200)
        fun capture(name: String): IntArray {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            return IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
        }
        fun greenPixels(pixels: IntArray) = pixels.count {
            android.graphics.Color.green(it) > 180 && android.graphics.Color.red(it) < 150 && android.graphics.Color.blue(it) < 140
        }
        val kick = capture("live-reveal-kick.png")
        assertTrue("Kick palette should contain volt green", greenPixels(kick) > 100)
        runBlocking { settings.set(SettingsKeys.DYNAMIC_COLOR, true) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        val material = capture("live-reveal-material.png")
        assertEquals("Dynamic theme should replace all volt green effects", 0, greenPixels(material))
        assertTrue("Dynamic theme should render the configured primary/tertiary gradient", material.count {
            android.graphics.Color.blue(it) > android.graphics.Color.green(it) * 1.3f && android.graphics.Color.red(it) < 220
        } > 100)
    }
    @Test fun releaseModesHaveTheExpectedActions() {
        show {
            var mode by remember { mutableStateOf(ReleaseDialogMode.UpdateAvailable) }
            ReleaseDialog(ReleaseDialogRequest(mode, GitHubRelease("v2.0.0", name = "Release test", body = "# Heading\n\n- **Bold**\n- *Italic*\n\n```kotlin\nval longLine = 1234567890\n```")),
                { mode = ReleaseDialogMode.WhatsNew })
        }
        compose.onNodeWithText(context("tr").getString(R.string.release_update)).assertIsDisplayed()
        compose.onNodeWithText(context("tr").getString(R.string.release_later)).performClick()
        compose.onNodeWithText(context("tr").getString(android.R.string.ok)).assertIsDisplayed()
        compose.onNodeWithText(context("tr").getString(R.string.release_update)).assertDoesNotExist()
    }
    @Test fun startupVersionMarkerIsPersistedImmediatelyAndOnlyChangesWithVersion() = runBlocking {
        assertTrue(settings.recordStartupVersion("v1.0.0"))
        assertFalse(settings.get(SettingsKeys.IS_FIRST_RUN).first())
        assertEquals("v1.0.0", settings.get(SettingsKeys.LAST_USED_VERSION).first())
        assertFalse(settings.recordStartupVersion("V1.0.0"))
        assertTrue(settings.recordStartupVersion("1.1.0"))
    }
}
