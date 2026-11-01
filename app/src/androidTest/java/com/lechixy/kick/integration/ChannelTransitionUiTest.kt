package com.lechixy.kick.integration

import com.lechixy.kick.data.channel.model.ChannelLivestream

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository

@RunWith(AndroidJUnit4::class)
class ChannelTransitionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var storeScope: CoroutineScope
    private lateinit var settingsFile: File
    private lateinit var settings: SettingsRepository

    @Before fun prepare() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settingsFile = File(compose.activity.cacheDir, "transition-${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { settingsFile }))
    }

    @After fun cleanup() {
        if (::storeScope.isInitialized) storeScope.cancel()
        if (::settingsFile.isInitialized) settingsFile.delete()
    }

    @Test fun connectedOpeningKeepsPageSizeAndCompositionStable() {
        val host = PlayerHostState()
        val pageSizes = mutableListOf<IntSize>()
        var disposals = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) { MaterialTheme {
                Box(Modifier.fillMaxSize().padding(start = 80.dp)) {
                    PlayerHost(host, false, host::minimize, host::expand, host::close,
                        pageContent = {
                            DisposableEffect(Unit) { onDispose { disposals++ } }
                            Box(Modifier.fillMaxSize().onSizeChanged { pageSizes += it }.testTag("page"))
                        })
                }
            } }
        }
        compose.runOnUiThread {
            host.open("offline", ExpansionSource(Rect(180f, 200f, 580f, 480f)))
            host.onChannelLoaded("offline", ChannelDetail(1, slug = "offline"))
        }
        compose.mainClock.advanceTimeByFrame()
        repeat(6) {
            compose.mainClock.advanceTimeBy(96)
            compose.runOnUiThread { assertEquals(0, disposals) }
        }
        compose.runOnUiThread {
            assertTrue(pageSizes.isNotEmpty())
            assertEquals("The page must never be measured at card dimensions", 1, pageSizes.distinct().size)
            assertNull(host.expansionSource)
        }
        compose.onNodeWithTag("page").assertIsDisplayed()
        compose.runOnUiThread {
            host.onChannelLoaded("offline", ChannelDetail(1, slug = "offline",
                livestream = com.lechixy.kick.data.channel.model.ChannelLivestream(1)))
            host.minimize()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("page").assertDoesNotExist()
        compose.runOnUiThread { assertEquals(PlayerMode.Mini, host.mode) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun reopeningDuringDismissalCancelsRemovalAndRestoresPage() {
        val host = PlayerHostState().apply {
            open("offline")
            onChannelLoaded("offline", ChannelDetail(1, slug = "offline"))
        }
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) { MaterialTheme {
                PlayerHost(host, false, host::minimize, host::expand, host::close,
                    pageContent = { Box(Modifier.fillMaxSize().testTag("page")) })
            } }
        }
        val width = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { host.minimize() }
        compose.mainClock.advanceTimeBy(120)
        compose.runOnUiThread { host.open("offline") }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnUiThread {
            assertEquals(PlayerMode.Expanded, host.mode)
            assertFalse(host.isExiting)
            assertEquals("offline", host.slug)
        }
        assertEquals(width, compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot.width, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun normalAndPinnedLinksOpenOnTouch() {
        val opened = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings,
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { opened += uri }
                }) { MaterialTheme {
                ChatView(messages = List(100) { ChatMessage(id = "filler-$it", content = "Message $it") } +
                    ChatMessage(id = "normal", content = "Normal https://example.com/live"),
                    connectionState = ChatConnectionState.CONNECTED,
                    pinnedMessage = PinnedMessageInfo(message = ChatMessage(id = "pinned", content = "Pinned https://example.com/pin")))
            } }
        }
        for ((prefix, url) in listOf("Normal" to "https://example.com/live", "Pinned" to "https://example.com/pin")) {
            val node = compose.onNodeWithText("$prefix $url", substring = true, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.first()
            val offset = layout.layoutInput.text.text.indexOf(url) + 5
            val link = layout.layoutInput.text.getLinkAnnotations(offset, offset + 1).single().item
            assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, link.styles?.style?.fontWeight)
            assertEquals(androidx.compose.ui.text.style.TextDecoration.Underline, link.styles?.style?.textDecoration)
            val bounds = layout.getBoundingBox(offset)
            node.performTouchInput { click(bounds.center) }
            compose.runOnIdle { assertTrue("Link in $prefix message must open", opened.contains(url)) }
        }
    }
}
