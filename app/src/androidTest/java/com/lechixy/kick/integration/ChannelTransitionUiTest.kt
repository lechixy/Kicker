package com.lechixy.kick.integration

import com.lechixy.kick.data.channel.model.ChannelLivestream

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume

import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.R
import com.lechixy.kick.components.AmbientFrame
import com.lechixy.kick.components.LocalPlayerAmbientState
import com.lechixy.kick.components.LocalPlayerFullscreenProgress
import com.lechixy.kick.components.PlayerAmbientState
import com.lechixy.kick.components.PlayerHost
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.components.channel.chat.ChatView
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEventResponse
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.chat.model.ChatHistoryResponse
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.screens.channel.ChannelScreen
import com.lechixy.kick.screens.channel.ChannelTab
import com.lechixy.kick.screens.channel.ChannelViewModel

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

    @Test fun coldChannelChatTabStaysInPlaceAsMetadataAndContentArrive() {
        val channelReply = CompletableDeferred<ChannelDetail>()
        val videosReply = CompletableDeferred<List<ChannelVideo>>()
        fun awaitReply(reply: Deferred<*>, arguments: Array<out Any?>): Any {
            @Suppress("UNCHECKED_CAST")
            val continuation = arguments.last() as Continuation<Any?>
            storeScope.launch { continuation.resume(reply.await()) }
            return COROUTINE_SUSPENDED
        }
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, args ->
            when (method.name) {
                "getChannel" -> awaitReply(channelReply, args)
                "getChannelVideos" -> awaitReply(videosReply, args)
                "getChannelEvent" -> ChannelEventResponse()
                "getChatHistory" -> ChatHistoryResponse()
                "getCurrentViewers" -> emptyList<Any>()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as KickApi
        runBlocking { settings.set(SettingsKeys.AUTO_CONNECT_CHAT, false) }
        val host = PlayerHostState().apply { open("cold-channel") }
        var fullscreenProgress by mutableFloatStateOf(0f)
        lateinit var model: ChannelViewModel
        compose.setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settings,
                LocalPlayerFullscreenProgress provides { fullscreenProgress },
            ) {
                MaterialTheme {
                    model = viewModel { ChannelViewModel(KickRepository(api)) }
                    ChannelScreen(
                        channelSlug = "cold-channel",
                        modifier = Modifier.width(400.dp),
                        playerHost = host,
                        viewModel = model,
                        onChannelLoaded = { host.onChannelLoaded("cold-channel", it) },
                    )
                }
            }
        }
        val tab = compose.onNode(
            hasContentDescription(compose.activity.getString(ChannelTab.CHAT.label)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        )
        val skeletonTop = tab.fetchSemanticsNode().boundsInRoot.top
        channelReply.complete(ChannelDetail(1, slug = "cold-channel", livestream = ChannelLivestream(1)))
        compose.waitUntil(5_000) { model.uiState.value.channel != null }
        assertTrue(model.uiState.value.isLoading)
        assertEquals(skeletonTop, tab.fetchSemanticsNode().boundsInRoot.top, 1f)

        videosReply.complete(emptyList())
        compose.waitUntil(5_000) { !model.uiState.value.isLoading }
        assertEquals(skeletonTop, tab.fetchSemanticsNode().boundsInRoot.top, 1f)

        compose.runOnIdle {
            host.setFullscreen(true)
            fullscreenProgress = 0.35f
        }
        tab.assertIsDisplayed()
        compose.runOnIdle { fullscreenProgress = 1f }
        tab.assertIsNotDisplayed()
        compose.runOnIdle {
            host.setFullscreen(false)
            fullscreenProgress = 0.65f
        }
        tab.assertIsDisplayed()
    }

    @Test fun channelTabsShowTheBackdropUntilTheHeaderFullyCollapses() {
        assumeTrue(compose.activity.resources.configuration.screenWidthDp >= 740)
        val channel = ChannelDetail(1, slug = "wide-channel", livestream = ChannelLivestream(1))
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, _ ->
            when (method.name) {
                "getChannel" -> channel
                "getChannelVideos" -> emptyList<ChannelVideo>()
                "getChannelEvent" -> ChannelEventResponse()
                "getChatHistory" -> ChatHistoryResponse()
                "getCurrentViewers" -> emptyList<Any>()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as KickApi
        runBlocking { settings.set(SettingsKeys.AUTO_CONNECT_CHAT, false) }
        val host = PlayerHostState().apply {
            open(channel.slug)
            onChannelLoaded(channel.slug, channel)
            updateChatVisibility(false)
        }
        lateinit var model: ChannelViewModel
        compose.setContent {
            CompositionLocalProvider(
                LocalSettingsRepository provides settings,
                LocalPlayerAmbientState provides remember { PlayerAmbientState() },
            ) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                        model = viewModel { ChannelViewModel(KickRepository(api)) }
                        ChannelScreen(channel.slug, playerHost = host, viewModel = model)
                    }
                }
            }
        }
        compose.waitUntil(5_000) { !model.uiState.value.isLoading }
        val tabs = compose.onNode(
            hasText(compose.activity.getString(ChannelTab.HOME.label)) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
        )
        val root = compose.onRoot()
        fun tabBackground(): Color {
            val bounds = tabs.fetchSemanticsNode().boundsInRoot
            val pixels = root.captureToImage().toPixelMap()
            val padding = with(compose.density) { 3.dp.toPx() }
            return pixels[(bounds.right - padding).toInt(), bounds.center.y.toInt()]
        }
        val topWithHeader = tabs.fetchSemanticsNode().boundsInRoot.top
        assertTrue(tabBackground().green < 0.1f)

        root.performTouchInput {
            down(Offset(width * 0.45f, height - 36.dp.toPx()))
            moveBy(Offset(0f, -30.dp.toPx()))
            advanceEventTime(300)
            up()
        }
        assertTrue(tabs.fetchSemanticsNode().boundsInRoot.top < topWithHeader)
        assertTrue(tabBackground().green < 0.1f)

        repeat(2) {
            val tabsBottom = tabs.fetchSemanticsNode().boundsInRoot.bottom
            root.performTouchInput {
                val x = width * 0.45f
                swipe(Offset(x, height - 36.dp.toPx()), Offset(x, tabsBottom + 12.dp.toPx()))
            }
        }
        assertTrue("Pinned tabs should use the opaque theme surface", tabBackground().green > 0.8f)
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

    @Test fun matureLiveDialogOnlyPersistsOptOutOnConfirmation() {
        val host = PlayerHostState()
        val channel = ChannelDetail(1, slug = "test", livestream = ChannelLivestream(1, isMature = true))
        host.open(channel.slug)
        host.onChannelLoaded(channel.slug, channel)
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) {
                MaterialTheme {
                    PlayerHost(host, false, host::minimize, host::expand, host::close, pageContent = {})
                }
            }
        }
        fun label(res: Int) = compose.activity.getString(res)
        val prompt = compose.onNodeWithText(label(R.string.mature_live_title))
        val dontAskAgain = compose.onNodeWithText(label(R.string.mature_live_dont_ask))
        val cancel = compose.onNodeWithText(label(R.string.mature_live_cancel))
        val watch = compose.onNodeWithText(label(R.string.mature_live_watch))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(label(R.string.mature_live_title)).fetchSemanticsNodes().isNotEmpty()
        }
        prompt.assertIsDisplayed()
        compose.onNodeWithText(label(R.string.mature_live_message)).assertIsDisplayed()
        assertTrue(cancel.fetchSemanticsNode().boundsInRoot.right <= watch.fetchSemanticsNode().boundsInRoot.left)
        dontAskAgain.performClick()
        cancel.performClick()
        compose.waitUntil(5_000) { host.mode == PlayerMode.Hidden }
        assertTrue(runBlocking { settings.get(SettingsKeys.ASK_BEFORE_MATURE_LIVE).first() })

        compose.runOnIdle {
            host.open(channel.slug)
            host.onChannelLoaded(channel.slug, channel)
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(label(R.string.mature_live_title)).fetchSemanticsNodes().isNotEmpty()
        }
        dontAskAgain.performClick()
        watch.performClick()
        compose.waitUntil(5_000) { !runBlocking { settings.get(SettingsKeys.ASK_BEFORE_MATURE_LIVE).first() } }
        prompt.assertDoesNotExist()
        compose.runOnIdle {
            host.onChannelLoaded(channel.slug, channel.copy(livestream = ChannelLivestream(2, isMature = true)))
        }
        prompt.assertDoesNotExist()
    }

    @Test fun pictureInPictureResizeKeepsTheChannelPageOffscreenAndRestoresIt() {
        val host = PlayerHostState().apply {
            open("test")
            onChannelLoaded("test", ChannelDetail(1, slug = "test", livestream = ChannelLivestream(1)))
        }
        var enteringPip by mutableStateOf(false)
        var inPip by mutableStateOf(false)
        var disposals = 0
        val sizes = mutableListOf<IntSize>()
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) { MaterialTheme {
                val frame = if (inPip) Modifier.size(180.dp, 102.dp) else Modifier.fillMaxSize()
                Box(frame) {
                    PlayerHost(host, inPip, host::minimize, host::expand, host::close,
                        enteringPip = enteringPip, pageContent = {
                            DisposableEffect(Unit) { onDispose { disposals++ } }
                            Box(Modifier.fillMaxSize().onSizeChanged { sizes += it }.testTag("channel-page"))
                        })
                }
            } }
        }
        val page = compose.onNodeWithTag("channel-page")
        page.assertIsDisplayed()
        compose.runOnIdle { enteringPip = true }
        page.assertIsNotDisplayed()
        compose.runOnIdle { enteringPip = false; inPip = true }
        page.assertIsNotDisplayed()
        compose.runOnIdle { inPip = false }
        page.assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, sizes.distinct().size)
            assertEquals(0, disposals)
            assertEquals(PlayerMode.Expanded, host.mode)
        }
    }

    @Test fun fullscreenProgressAnimatesInBothDirections() {
        val host = PlayerHostState().apply {
            open("test")
            onChannelLoaded("test", ChannelDetail(1, slug = "test", livestream = ChannelLivestream(1)))
        }
        lateinit var progress: () -> Float
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) {
                MaterialTheme {
                    PlayerHost(host, false, host::minimize, host::expand, host::close, pageContent = {
                        progress = checkNotNull(LocalPlayerFullscreenProgress.current)
                    })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread { host.setFullscreen(true) }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnUiThread { assertTrue("Fullscreen entry should be in progress", progress() in 0.01f..0.99f) }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnUiThread { assertEquals(1f, progress(), 0.001f) }

            compose.runOnUiThread { host.setFullscreen(false) }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnUiThread { assertTrue("Fullscreen exit should be in progress", progress() in 0.01f..0.99f) }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnUiThread { assertEquals(0f, progress(), 0.001f) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun fullscreenDownwardDragRevealsAmbientInTheSameScreenPosition() {
        val host = PlayerHostState().apply {
            open("test")
            onChannelLoaded("test", ChannelDetail(1, slug = "test", livestream = ChannelLivestream(1)))
            setFullscreen(true)
        }
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    PlayerHost(host, false, host::minimize, host::expand, host::close, pageContent = {
                        val ambient = checkNotNull(LocalPlayerAmbientState.current)
                        val palette = remember { AmbientFrame.Palette(List(8) { Color.Red }) }
                        SideEffect { ambient.frame = palette }
                    })
                }
            }
        }
        val root = compose.onRoot()
        compose.waitForIdle()
        root.performTouchInput { down(center); moveBy(Offset(0f, 56.dp.toPx())) }
        try {
            val first = root.captureToImage().toPixelMap()
            root.performTouchInput { moveBy(Offset(0f, 40.dp.toPx())) }
            val second = root.captureToImage().toPixelMap()
            for (x in listOf(first.width / 6, first.width * 5 / 6)) {
                val before = first[x, 8]
                val after = second[x, 8]
                assertTrue("The exposed canvas should be lit", before.red > 0.1f)
                assertEquals(before.red, after.red, 0.02f)
                assertEquals(before.green, after.green, 0.02f)
            }
        } finally {
            root.performTouchInput { cancel() }
        }
        compose.runOnIdle { assertEquals(PlayerMode.Fullscreen, host.mode) }
    }

    @Test fun inlineDownwardDragMovesAndFadesTheAmbientGlow() {
        val host = PlayerHostState().apply {
            open("test")
            onChannelLoaded("test", ChannelDetail(1, slug = "test", livestream = ChannelLivestream(1)))
        }
        var playerBounds = Rect.Zero
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Box(Modifier.width(420.dp).fillMaxHeight()) {
                        PlayerHost(
                            host, false, host::minimize, host::expand, host::close,
                            onVideoBoundsChanged = { playerBounds = it ?: Rect.Zero },
                            pageContent = {
                                val ambient = checkNotNull(LocalPlayerAmbientState.current)
                                val palette = remember { AmbientFrame.Palette(List(8) { Color.Red }) }
                                SideEffect { ambient.frame = palette }
                            },
                        )
                    }
                }
            }
        }
        val root = compose.onRoot()
        fun glowAtVideoEdge(bounds: Rect): Float {
            val pixels = root.captureToImage().toPixelMap()
            val x = bounds.center.x.toInt()
            val density = compose.density.density
            fun redTintAt(offset: Int): Float {
                val y = (bounds.bottom + offset * density).toInt().coerceAtMost(pixels.height - 1)
                val color = pixels[x, y]
                return color.red - color.green
            }
            val backgroundTint = redTintAt(155)
            return listOf(12, 24, 36, 48).maxOf { redTintAt(it) } - backgroundTint
        }
        val initialBounds = compose.runOnIdle { playerBounds }
        val initialGlow = glowAtVideoEdge(initialBounds)
        assertTrue("A resting video should have a red glow, measured $initialGlow", initialGlow > 0.1f)
        root.performTouchInput { down(initialBounds.center); moveBy(Offset(0f, 90.dp.toPx())) }
        try {
            val movedBounds = compose.runOnIdle { playerBounds }
            assertTrue(movedBounds.bottom > initialBounds.bottom + 30f)
            val fadedGlow = glowAtVideoEdge(movedBounds)
            val details = "initial=$initialGlow at $initialBounds, faded=$fadedGlow at $movedBounds"
            assertTrue("The glow should follow the new video edge ($details)", fadedGlow > initialGlow * 0.37f)
            assertTrue("The glow should fade as the page is pulled down ($details)", fadedGlow < initialGlow * 0.77f)
        } finally {
            root.performTouchInput { cancel() }
        }
        val restoredBounds = compose.runOnIdle { playerBounds }
        assertEquals(initialBounds.bottom, restoredBounds.bottom, 2f)
        assertEquals(initialGlow, glowAtVideoEdge(restoredBounds), 0.025f)
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
