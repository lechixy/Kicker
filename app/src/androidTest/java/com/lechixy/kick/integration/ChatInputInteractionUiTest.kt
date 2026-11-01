package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.channel.ChannelTabPager
import com.lechixy.kick.components.channel.chat.ChannelPointsPanel
import com.lechixy.kick.components.channel.chat.ChatCompletionPanel
import com.lechixy.kick.components.channel.chat.ChatReplyPreview
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.components.channel.chat.SwipeToReply
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.ChatCompletionToken
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.CompletionKind
import com.lechixy.kick.data.chat.model.EmoteDto
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.screens.channel.ChannelTab

class ChatInputInteractionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var settings: SettingsRepository
    private lateinit var settingsFile: File
    private lateinit var storeScope: CoroutineScope
    @Before fun prepare() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true); it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsFile = File(compose.activity.cacheDir, "chat-input-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { settingsFile }))
    }
    @After fun cleanup() { storeScope.cancel(); settingsFile.delete() }
    private fun show(content: @Composable () -> Unit) {
        compose.setContent { CompositionLocalProvider(LocalSettingsRepository provides settings) { MaterialTheme { content() } } }
        compose.waitUntil(20_000) { runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
    }
    @Test fun swipingRightRepliesAndSwipingLeftDoesNot() {
        var replies = 0
        show {
            SwipeToReply(true, { replies++ }, Modifier.width(360.dp).testTag("message")) {
                Text("Alice: Hello", Modifier.fillMaxWidth().height(60.dp))
            }
        }
        compose.onNodeWithTag("message").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, replies) }
        compose.onNodeWithTag("message").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, replies) }
    }
    @Test fun replyPreviewUsesTheMessageRendererAndCloses() {
        var reply by mutableStateOf<ChatMessage?>(ChatMessage(id = "id", content = "original content",
            sender = ChatSender(id = 42, username = "Alice", identity = ChatIdentity(color = "#6F87FF"))))
        show { Box(Modifier.width(360.dp)) { ChatReplyPreview(reply, { reply = null }) } }
        compose.onNodeWithText("original content", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.chat_cancel_reply)).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("original content", substring = true).assertDoesNotExist()
    }
    @Test fun colonResultsInsertARealEmoteToken() {
        var inserted = ""
        show {
            ChatCompletionPanel(ChatCompletionToken(CompletionKind.EMOTE, "trF", 0, 4), emptyList(), emptyList(),
                listOf(EmoteDto(42, name = "trFire")), { inserted = it })
        }
        compose.onNodeWithText("trFire").performClick()
        compose.runOnIdle { assertEquals("[emote:42:trFire]", inserted) }
    }
    @Test fun disabledChannelPointsExplainTheStateAndNeverShowZero() {
        show { ChannelPointsPanel("tester", emptyList(), false, 0, null, {}, {}, pointsEnabled = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.points_disabled)).assertIsDisplayed()
        compose.onAllNodesWithText("0").assertCountEquals(0)
        compose.onAllNodesWithContentDescription(compose.activity.getString(R.string.points_disabled)).assertCountEquals(1)
    }
    @Test fun tabPagesFollowTheFingerAndRespondToShortDragsAndFlicks() {
        lateinit var pager: PagerState
        show {
            pager = rememberPagerState { 2 }
            ChannelTabPager(pager, listOf(ChannelTab.HOME, ChannelTab.VIDEOS), Modifier.size(360.dp, 200.dp).testTag("pager")) {
                Box(Modifier.fillMaxSize().testTag(it.name)) { Text(it.name) }
            }
        }
        compose.mainClock.autoAdvance = false
        val width = compose.onNodeWithTag("pager").fetchSemanticsNode().boundsInRoot.width
        compose.onNodeWithTag("pager").performTouchInput { down(center); moveBy(Offset(-width * .3f, 0f)) }
        compose.mainClock.advanceTimeByFrame()
        val home = compose.onNodeWithTag("HOME").fetchSemanticsNode().boundsInRoot
        val video = compose.onNodeWithTag("VIDEOS").fetchSemanticsNode().boundsInRoot
        // Semantics bounds are clipped by the pager; the outgoing page gets narrower while the next enters.
        assertTrue("Outgoing page should be partially visible: $home / $width", home.width > 0f && home.width < width)
        assertTrue("Incoming page should enter before release: $video / $width", video.left > 0f && video.left < width)
        // Holding before release removes fling velocity, so this checks the lower distance threshold.
        compose.onNodeWithTag("pager").performTouchInput { advanceEventTime(250); up() }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput {
            down(center); moveBy(Offset(width * .3f, 0f)); advanceEventTime(250); up()
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(0, pager.settledPage) }
        // A quick flick can advance even below the positional threshold.
        compose.onNodeWithTag("pager").performTouchInput {
            swipe(center, center + Offset(-width * .18f, 0f), durationMillis = 80)
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(1, pager.settledPage) }
        compose.mainClock.autoAdvance = true
    }
    @Test fun predictiveBackCancellationRestoresTheChannelAndCommitClosesIt() {
        val host = PlayerHostState().apply {
            open("offline"); onChannelLoaded("offline", ChannelDetail(1, slug = "offline"))
        }
        show {
            PlayerHost(host, false, host::minimize, host::expand, host::close,
                pageContent = { Box(Modifier.fillMaxSize().testTag("channel-page")) })
        }
        val initial = compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 100f, .6f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        val preview = compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width
        assertTrue(preview < initial)
        compose.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(PlayerMode.Expanded, host.mode) }
        assertEquals(initial, compose.onNodeWithTag("channel-page").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 100f, 1f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(PlayerMode.Hidden, host.mode) }
    }
    @Test fun theBundledRewardIsAnAnimatedImage() {
        val source = ImageDecoder.createSource(compose.activity.resources, R.drawable.reward_available)
        val drawable = ImageDecoder.decodeDrawable(source)
        assertTrue(drawable is AnimatedImageDrawable)
        assertTrue(drawable.intrinsicWidth > 0)
        assertEquals(drawable.intrinsicWidth, drawable.intrinsicHeight)
    }
    @Test fun initialHistoryDoesNotShowTheJumpToLatestButton() {
        var messages by mutableStateOf(emptyList<ChatMessage>())
        show { Box(Modifier.size(360.dp, 300.dp)) { ChatView(messages, ChatConnectionState.CONNECTED) } }
        compose.runOnUiThread {
            messages = (1..50).map { ChatMessage(id = "$it", content = "history $it", sender = ChatSender(username = "Alice"), isHistory = true) }
        }
        compose.waitForIdle()
        compose.onNodeWithText(compose.activity.getString(R.string.chat_scroll_bottom)).assertDoesNotExist()
        compose.onNodeWithText("history 50", substring = true).assertIsDisplayed()
    }
}
