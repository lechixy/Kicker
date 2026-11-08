package com.lechixy.kick.integration

import com.lechixy.kick.components.channel.OfflineBannerState
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.common.model.Channel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.channel.OfflineChannelBanner
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository

@RunWith(AndroidJUnit4::class)
class OfflineBannerUiTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before fun keepTestVisible() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun draggingTheOfflineBannerItselfStillCollapsesIt() {
        lateinit var state: OfflineBannerState
        compose.setContent { MaterialTheme {
            val scope = rememberCoroutineScope()
            val banner = remember { OfflineBannerState(scope) }
            SideEffect { state = banner }
            Box(Modifier.width(700.dp)) {
                OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, banner,
                    { banner.animateTo(1f) }, {}, Modifier.testTag("banner"),
                    onTravelChanged = { banner.travelPx = it }, onDrag = { banner.drag(it) }, onDragFinished = banner::settle)
            }
        } }
        compose.onNodeWithTag("banner").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, center.y * 0.6f), durationMillis = 400)
        }
        compose.runOnIdle { assertTrue(state.value > 0f) }
    }

    @Test fun nearEndpointsSettleAndLabelClearsMinimizeButton() {
        lateinit var banner: com.lechixy.kick.components.channel.OfflineBannerState
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            val scope = rememberCoroutineScope()
            val state = remember { com.lechixy.kick.components.channel.OfflineBannerState(scope) }
            SideEffect { banner = state }
            Box(Modifier.width(700.dp)) {
                OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, state,
                    { state.animateTo(1f) }, {}, Modifier.testTag("banner"), onTravelChanged = { state.travelPx = it })
            }
        } }
        compose.runOnIdle { banner.drag(banner.travelPx * 0.02f); banner.settle() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0f, banner.value, 0.001f); banner.drag(banner.travelPx * 0.98f); banner.settle() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1f, banner.value, 0.001f) }
        val bannerBounds = compose.onNodeWithTag("banner").fetchSemanticsNode().boundsInRoot
        assertEquals(with(compose.density) { 128.dp.toPx() }, bannerBounds.height, 1f)
        val button = compose.onNodeWithContentDescription(context.getString(R.string.offline_minimize)).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithText(context.getString(R.string.offline_channel_name, "tester"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(text.left > button.right)
    }

    @Test fun downwardBannerGestureIsRoutedToScreenDismissal() {
        var distance = 0f
        var released = false
        var progressDrag = false
        compose.setContent { MaterialTheme {
            OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, remember { mutableFloatStateOf(0f) }, {}, {},
                Modifier.width(700.dp).testTag("banner"), onDrag = { progressDrag = true },
                onDismissDrag = { distance += it }, onDismissDragFinished = { _, cancelled -> released = !cancelled })
        } }
        compose.onNodeWithTag("banner").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 180f), durationMillis = 400)
        }
        compose.runOnIdle { assertTrue(distance > 0f); assertTrue(released); assertFalse(progressDrag) }
    }

    @Test fun offlineHostScalesDownBeforeClosingAndNeverEntersMiniMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "offline-exit-${System.nanoTime()}.preferences_pb")
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file }))
        val host = PlayerHostState().apply {
            open("tester")
            onChannelLoaded("tester", ChannelDetail(1, slug = "tester"))
        }
        val modes = mutableListOf<PlayerMode>()
        try {
            compose.setContent {
                val mode = host.mode
                SideEffect { modes += mode }
                CompositionLocalProvider(LocalSettingsRepository provides repository) { MaterialTheme {
                    PlayerHost(host, false, host::minimize, host::expand, host::close,
                        pageContent = { Box(Modifier.fillMaxSize().testTag("offlinePage")) })
                } }
            }
            compose.waitForIdle()
            val originalBounds = compose.onNodeWithTag("offlinePage").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { host.dragOffsetY = 80f; host.minimize() }
            compose.mainClock.advanceTimeBy(160)
            compose.runOnUiThread { assertEquals(PlayerMode.Expanded, host.mode); assertTrue(host.isExiting) }
            val currentBounds = compose.onNodeWithTag("offlinePage").fetchSemanticsNode().boundsInRoot
            assertTrue("The offline page must shrink before it is removed", currentBounds.width < originalBounds.width)
            assertTrue("The swipe displacement must be retained during dismissal", currentBounds.top > originalBounds.top)
            compose.mainClock.advanceTimeBy(400)
            compose.runOnUiThread { assertEquals(PlayerMode.Hidden, host.mode); assertFalse(modes.contains(PlayerMode.Mini)) }
            compose.mainClock.autoAdvance = true
        } finally { storeScope.cancel(); file.delete() }
    }
}
