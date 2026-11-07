package com.lechixy.kick.integration

import com.lechixy.kick.components.channel.OfflineBannerState
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.common.model.Channel

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.launch
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
import com.lechixy.kick.components.channel.OfflineBannerScrollConnection
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

    @Test fun draggingHoldsAnIntermediateHeightAndOnlyExpandsAtTheTop() {
        lateinit var state: com.lechixy.kick.components.channel.OfflineBannerState
        lateinit var jump: (Int) -> Unit
        lateinit var firstIndex: () -> Int
        lateinit var contentHasMoved: () -> Boolean
        compose.setContent { MaterialTheme {
            val list = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val banner = remember { com.lechixy.kick.components.channel.OfflineBannerState(scope) }
            SideEffect {
                state = banner
                jump = { index -> scope.launch { list.scrollToItem(index) } }
                firstIndex = { list.firstVisibleItemIndex }
                contentHasMoved = { list.canScrollBackward }
            }
            val connection = remember { OfflineBannerScrollConnection({ true }, { !list.canScrollBackward }, banner) }
            Column(Modifier.width(700.dp).fillMaxHeight().nestedScroll(connection)) {
                OfflineChannelBanner(ChannelDetail(1, slug = "tester"), null, banner,
                    { banner.animateTo(1f) }, {}, Modifier.testTag("banner"),
                    onTravelChanged = { banner.travelPx = it }, onDrag = { banner.drag(it) }, onDragFinished = banner::settle)
                LazyColumn(Modifier.weight(1f).testTag("content"), state = list) {
                    items(80) { Text("Channel content $it", Modifier.fillMaxWidth().height(48.dp)) }
                }
            }
        } }
        compose.onNodeWithTag("content").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, 140f), durationMillis = 700)
        }
        var held = 0f
        compose.runOnIdle { held = state.value; assertTrue(held > 0.06f && held < 0.94f) }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(held, state.value, 0.001f) }
        // Release a short, fast swipe while the header is still partially expanded.
        // Kinetic frames must finish collapsing it before scrolling the list underneath.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("content").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, 180f), durationMillis = 60)
        }
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnUiThread {
                if (contentHasMoved()) assertEquals("Content must not outrun the collapsing banner", 1f, state.value, 0.001f)
            }
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("Fling must continue moving the banner after release", 1f, state.value, 0.001f)
            assertTrue("The remaining fling should reach the content", contentHasMoved())
            jump(30)
        }
        compose.waitUntil(3_000) { firstIndex() == 30 }
        compose.onNodeWithTag("content").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 80f), durationMillis = 700)
        }
        compose.runOnIdle { assertTrue(firstIndex() > 0); assertEquals(1f, state.value, 0.001f); jump(2) }
        compose.waitForIdle()
        compose.onNodeWithTag("content").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue("Returning to the top must expand the banner progressively", state.value < 1f) }
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
