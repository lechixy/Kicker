package com.lechixy.kick.integration

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.os.Build
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdrop
import com.lechixy.kick.components.channel.chat.FullscreenChatBackdropRegion
import com.lechixy.kick.components.channel.chat.FullscreenChatDock
import com.lechixy.kick.components.channel.chat.FullscreenChatEdges
import com.lechixy.kick.components.channel.chat.LocalFullscreenChatDock
import com.lechixy.kick.components.channel.chat.LocalFullscreenChatShape
import com.lechixy.kick.components.channel.chat.FullscreenChatBlur
import com.lechixy.kick.components.channel.chat.FullscreenChatIdentityStyle
import com.lechixy.kick.components.channel.chat.FullscreenChatOverlay
import com.lechixy.kick.components.channel.chat.FullscreenChatPreferences
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoBlur
import com.lechixy.kick.components.channel.chat.HorizontalChat
import com.lechixy.kick.components.channel.chat.OverlayMessage
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.ChatSenderBadge
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.settings.model.FullscreenChatPosition
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenChatOverlayUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tickerMovesRightPausesAndPassesTouchesToThePlayer() {
        val message = ChatMessage("message", content = "https://example.com", sender = ChatSender(username = "viewer"))
        val messages = listOf(message)
        var eligible by mutableStateOf(messages)
        var playing by mutableStateOf(true)
        var widthFraction by mutableStateOf(1f)
        var playerTaps = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth(widthFraction).height(56.dp).clickable { playerTaps++ }) {
                    HorizontalChat(messages, eligible, null, playing, 0.6f, 14)
                }
            }
        }

        val chip = compose.onNodeWithText("viewer: https://example.com", useUnmergedTree = true)
        compose.mainClock.advanceTimeBy(1_800)
        val firstX = chip.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(450)
        assertTrue(chip.fetchSemanticsNode().boundsInRoot.left > firstX)

        compose.runOnIdle { playing = false }
        compose.mainClock.advanceTimeByFrame()
        val pausedX = chip.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(450)
        assertEquals(pausedX, chip.fetchSemanticsNode().boundsInRoot.left, 0f)
        compose.runOnIdle { widthFraction = 0.85f }
        compose.mainClock.advanceTimeByFrame()
        assertEquals(pausedX, chip.fetchSemanticsNode().boundsInRoot.left, 0f)
        chip.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(1, playerTaps) }

        compose.runOnIdle { playing = true }
        compose.mainClock.advanceTimeBy(400)
        assertTrue(chip.fetchSemanticsNode().boundsInRoot.left > pausedX)
        compose.runOnIdle { eligible = emptyList() }
        compose.mainClock.advanceTimeByFrame()
        chip.assertDoesNotExist()
    }

    @Test fun tickerFillsFreeSpaceInOneLaneAndDistributesMessagesAcrossLanes() {
        val burst = listOf("one", "two", "three", "four").map { ChatMessage(it, content = it, sender = ChatSender(username = "v")) }
        var messages by mutableStateOf(emptyList<ChatMessage>())
        var lanes by mutableStateOf(1)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { HorizontalChat(messages, messages, null, true, 0.6f, 14, laneCount = lanes) }
        }
        compose.runOnIdle { messages = burst }
        compose.mainClock.advanceTimeBy(1_100)
        val first = compose.onNodeWithText("v: one").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("v: two").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 0f)
        assertTrue(first.left > second.right)
        assertTrue(second.width > 0f)

        compose.runOnIdle {
            lanes = 2
            messages = emptyList()
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { messages = burst }
        compose.mainClock.advanceTimeBy(600)
        val top = compose.onNodeWithText("v: one").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithText("v: two").fetchSemanticsNode().boundsInRoot
        assertTrue(top.top < bottom.top)
        assertEquals(top.right, bottom.right, 1f)
    }

    @Test fun senderColorAndEssentialBadgesRemainWhenOtherBadgesAreHidden() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = ChatMessage(
            "message", content = "hello",
            sender = ChatSender(username = "viewer", identity = ChatIdentity("#FF4477",
                listOf("moderator", "verified", "broadcaster", "founder").map { ChatSenderBadge(it) })),
        )
        var badges by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                OverlayMessage(message, 14, 2, FullscreenChatIdentityStyle(showOptionalBadges = badges))
            }
        }
        val optional = compose.onNodeWithContentDescription(context.getString(R.string.badge_founder), useUnmergedTree = true)
        optional.assertExists()
        val text = compose.onNodeWithText("viewer: hello", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(text.spanStyles.any { it.item.color == Color(0xFFFF4477) })
        compose.runOnIdle { badges = false }
        optional.assertDoesNotExist()
        for (label in listOf(R.string.badge_moderator, R.string.badge_verified, R.string.badge_broadcaster)) {
            compose.onNodeWithContentDescription(context.getString(label), useUnmergedTree = true).assertExists()
        }
    }

    @Test fun onlyTheWatchedStreamerMentionGetsTheAnimatedGradient() {
        val message = ChatMessage("message", content = "hello @caster @friend", sender = ChatSender(username = "viewer"))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                OverlayMessage(message, 14, 2, FullscreenChatIdentityStyle(streamerSlug = "caster"))
            }
        }
        val node = compose.onNodeWithText("viewer: hello @caster @friend", useUnmergedTree = true)
        fun streamerBrush(): androidx.compose.ui.graphics.Brush? {
            val text = node.fetchSemanticsNode().config[SemanticsProperties.Text].single()
            val highlights = text.spanStyles.filter { it.item.brush != null }
            assertEquals(listOf("@caster"), highlights.map { text.text.substring(it.start, it.end) })
            return highlights.single().item.brush
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = streamerBrush()
        compose.mainClock.advanceTimeBy(220)
        assertTrue(initial != streamerBrush())
    }

    @Test fun backgroundShapeCanSwitchBetweenSquareAndPill() {
        var shape by mutableStateOf(FullscreenChatShape.Square)
        compose.setContent {
            CompositionLocalProvider(LocalFullscreenChatShape provides shape) {
                Box(Modifier.size(90.dp, 40.dp).background(Color.White).testTag("chat-shape")) {
                    FullscreenChatBackdrop(1f, null)
                }
            }
        }
        val background = compose.onNodeWithTag("chat-shape")
        assertTrue(background.captureToImage().toPixelMap()[2, 2].red < 0.1f)
        compose.runOnIdle { shape = FullscreenChatShape.Pill }
        assertTrue(background.captureToImage().toPixelMap()[2, 2].red > 0.95f)
    }

    @Test fun legacyBlurAndTintFadeIntoTheVideoAtTheEdges() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val frame = mutableStateOf<FullscreenChatVideoFrame?>(null)
        compose.setContent {
            Box(Modifier.size(80.dp).testTag("frosted-chat").background(Color.Black).onGloballyPositioned {
                val location = it.positionInWindow()
                frame.value = FullscreenChatVideoFrame(bitmap.asImageBitmap(), IntOffset(location.x.toInt(), location.y.toInt()), it.size)
            }) {
                FullscreenChatBackdrop(0.35f, FullscreenChatBlur({}, frame))
            }
        }
        val image = compose.onNodeWithTag("frosted-chat").captureToImage()
        val pixels = image.toPixelMap()
        val center = pixels[image.width / 2, image.height / 2]
        assertTrue(center.red > 0.45f)
        assertTrue(center.green < 0.1f && center.blue < 0.1f)
        assertTrue(pixels[image.width / 2, 1].red < 0.1f)
    }

    @Test fun progressiveBlurTracksNewTextureFramesAndLeavesTheRestOfTheVideoSharp() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        var frameIndex by mutableStateOf(1)
        var blurOpacity by mutableStateOf(1f)
        var dockedTop by mutableStateOf(false)
        val displayedFrame = AtomicInteger()
        compose.setContent {
            var texture by remember { mutableStateOf<TextureView?>(null) }
            var region by remember { mutableStateOf<Rect?>(null) }
            var viewport by remember { mutableStateOf<Rect?>(null) }
            Box(Modifier.size(144.dp).testTag("live-video").onGloballyPositioned {
                viewport = it.boundsInWindow()
                val origin = it.positionInWindow()
                region = Rect(
                    origin.x + it.size.width * 0.25f, origin.y + it.size.height * 0.25f,
                    origin.x + it.size.width * 0.75f, origin.y + it.size.height * 0.75f,
                )
            }) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        TextureView(context).apply {
                            texture = this
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                    post { drawSplitFrame(tag as? Int ?: 1) }
                                }
                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                                    displayedFrame.set(tag as? Int ?: 1)
                                }
                            }
                        }
                    },
                    update = {
                        if (it.tag != frameIndex) {
                            it.tag = frameIndex
                            if (it.isAvailable) it.drawSplitFrame(frameIndex)
                        }
                    },
                )
                val dock = viewport?.takeIf { dockedTop }?.let { FullscreenChatDock(it, FullscreenChatEdges(top = true)) }
                FullscreenChatVideoBlur(texture, region?.let { dock?.extend(it) ?: FullscreenChatBackdropRegion(it) }) { blurOpacity }
            }
        }
        compose.waitUntil(10_000) { displayedFrame.get() == 1 }
        val video = compose.onNodeWithTag("live-video")
        val first = video.captureToImage()
        val x = first.width / 2 - 2
        val firstPixels = first.toPixelMap()
        assertTrue(firstPixels[x, first.height / 2].red > 0.10f)
        assertTrue(firstPixels[x, first.height / 8].red < 0.05f)

        compose.runOnIdle { frameIndex = 2 }
        compose.waitUntil(10_000) { displayedFrame.get() == 2 }
        val second = video.captureToImage().toPixelMap()
        assertTrue(second[x, first.height / 2].blue > 0.10f)
        assertTrue(second[x, first.height / 8].red > 0.95f)
        assertTrue(second[x, first.height / 8].blue < 0.05f)

        compose.runOnIdle { blurOpacity = 0.5f }
        val half = video.captureToImage().toPixelMap()[x, first.height / 2].blue
        assertTrue(half > 0.05f && half < second[x, first.height / 2].blue)

        compose.runOnIdle { blurOpacity = 0f }
        val clear = video.captureToImage().toPixelMap()[x, first.height / 2]
        assertTrue(clear.red > 0.95f && clear.blue < 0.05f)

        compose.runOnIdle { dockedTop = true; blurOpacity = 1f }
        val docked = video.captureToImage().toPixelMap()[x, 1]
        assertTrue(docked.blue > 0.10f && docked.red + docked.blue > 0.95f)
    }

    @Test fun dockedDarkBackdropReachesTheCornerWithBlurDisabled() {
        compose.setContent {
            var viewport by remember { mutableStateOf<Rect?>(null) }
            Box(Modifier.size(100.dp).background(Color.White).testTag("docked-tint")
                .onGloballyPositioned { viewport = it.boundsInWindow() }) {
                val dock = viewport?.let { FullscreenChatDock(it, FullscreenChatEdges(left = true, top = true)) }
                CompositionLocalProvider(LocalFullscreenChatDock provides dock) {
                    Box(Modifier.align(Alignment.TopStart).offset(16.dp, 16.dp).size(48.dp)) {
                        FullscreenChatBackdrop(0.6f, null)
                    }
                }
            }
        }
        val image = compose.onNodeWithTag("docked-tint").captureToImage()
        val pixels = image.toPixelMap()
        assertTrue(pixels[1, 1].red < 0.6f)
        assertTrue(pixels[image.width - 2, image.height - 2].red > 0.95f)
    }

    @Test fun visibleChatCanMoveAndRevealSettingsWhilePositionLockPreventsDragging() = withSettings { settings ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = PlayerHostState().apply { playRecording(RecordedPlayback("no-chat", "Replay", 30_000)) }
        var position by mutableStateOf(FullscreenChatPosition.VerticalDefault)
        var locked by mutableStateOf(false)
        var settingsOpened = 0
        compose.setContent {
            CompositionLocalProvider(LocalSettingsRepository provides settings) {
                MaterialTheme {
                    FullscreenChatOverlay(
                        host, ChannelDetail(1, slug = "fullscreen-test"), null,
                        FullscreenChatPreferences(false, 0.6f, position, FullscreenChatPosition.HorizontalDefault, positionLocked = locked),
                        playing = false, editing = false,
                        onSavePosition = { _, next -> position = next },
                        onCancelEditing = {},
                        onOpenSettings = { settingsOpened++ },
                    )
                }
            }
        }
        val chat = compose.onNodeWithText(context.getString(R.string.recorded_chat_unavailable))
        val gear = compose.onNodeWithContentDescription(context.getString(R.string.fullscreen_chat_open_settings))
        chat.performTouchInput { click(center) }
        val originalGearBounds = gear.fetchSemanticsNode().boundsInRoot
        val originalChatHeight = chat.fetchSemanticsNode().boundsInRoot.height
        gear.performTouchInput { down(center) }
        runBlocking { settings.set(SettingsKeys.CHAT_FONT_SIZE, 24) }
        compose.waitUntil(5_000) { chat.fetchSemanticsNode().boundsInRoot.height > originalChatHeight }
        assertEquals(originalGearBounds, gear.fetchSemanticsNode().boundsInRoot)
        gear.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, settingsOpened) }
        chat.performTouchInput { swipe(center, center + Offset(width * 0.2f, -height * 2f)) }
        val moved = compose.runOnIdle {
            assertTrue(position.x > 0f && position.y < 1f)
            locked = true
            position
        }
        chat.performTouchInput { swipe(center, center + Offset(width * 0.2f, -height * 2f)) }
        compose.runOnIdle { assertEquals(moved, position) }
        chat.performTouchInput { click(center) }
        gear.performClick()
        compose.runOnIdle { assertEquals(2, settingsOpened) }
    }

    @Test fun draggingSavesTheAxesForEachModeAndSupportsResetAndCancel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "fullscreen-chat-${System.nanoTime()}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        val host = PlayerHostState()
        val channel = ChannelDetail(1, slug = "fullscreen-test")
        var horizontal by mutableStateOf(false)
        var editing by mutableStateOf(true)
        var verticalPosition by mutableStateOf(FullscreenChatPosition.VerticalDefault)
        var horizontalPosition by mutableStateOf(FullscreenChatPosition.HorizontalDefault)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalSettingsRepository provides settings) {
                    MaterialTheme {
                        if (editing) {
                            FullscreenChatOverlay(
                                host, channel, null,
                                FullscreenChatPreferences(horizontal, 0.6f, verticalPosition, horizontalPosition),
                                playing = false,
                                editing = true,
                                onSavePosition = { isHorizontal, position ->
                                    if (isHorizontal) horizontalPosition = position else verticalPosition = position
                                    editing = false
                                },
                                onCancelEditing = { editing = false },
                            )
                        }
                    }
                }
            }

            val panel = compose.onNodeWithContentDescription(context.getString(R.string.fullscreen_chat_edit_position))
            val done = compose.onNodeWithText(context.getString(R.string.fullscreen_chat_done))
            panel.performTouchInput { swipe(center, center + Offset(width * 0.25f, -height * 0.3f)) }
            done.performClick()
            val movedVertical = compose.runOnIdle {
                assertTrue(verticalPosition.x > 0f)
                assertTrue(verticalPosition.y < 1f)
                horizontal = true
                editing = true
                verticalPosition
            }

            panel.performTouchInput { swipe(center, center + Offset(width * 0.25f, height * 0.6f)) }
            done.performClick()
            val movedHorizontal = compose.runOnIdle {
                assertEquals(0f, horizontalPosition.x, 0f)
                assertTrue(horizontalPosition.y > 0f)
                assertEquals(movedVertical, verticalPosition)
                editing = true
                horizontalPosition
            }

            val reset = compose.onNodeWithText(context.getString(R.string.fullscreen_chat_reset_position))
            reset.performClick()
            compose.onNodeWithText(context.getString(R.string.fullscreen_chat_cancel)).performClick()
            compose.runOnIdle {
                assertEquals(movedHorizontal, horizontalPosition)
                editing = true
            }
            reset.performClick()
            done.performClick()
            compose.runOnIdle {
                assertEquals(FullscreenChatPosition.HorizontalDefault, horizontalPosition)
                assertEquals(movedVertical, verticalPosition)
            }
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun withSettings(block: (SettingsRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "fullscreen-chat-${System.nanoTime()}.preferences_pb")
        try {
            block(SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun TextureView.drawSplitFrame(index: Int) {
        val canvas = lockCanvas() ?: return
        try {
            canvas.drawColor(if (index == 1) android.graphics.Color.BLACK else android.graphics.Color.RED)
            val paint = Paint().apply {
                color = if (index == 1) android.graphics.Color.WHITE else android.graphics.Color.BLUE
            }
            canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), paint)
        } finally {
            unlockCanvasAndPost(canvas)
        }
    }
}
