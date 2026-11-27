package com.lechixy.kick.integration

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.PixelCopy
import android.view.TextureView
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.components.AmbientFrame
import com.lechixy.kick.components.AmbientSeek
import com.lechixy.kick.components.CapturePlayerAmbient
import com.lechixy.kick.components.FrostedPlaybackButton
import com.lechixy.kick.components.FrostedPlayerRegions
import com.lechixy.kick.components.PlayerAmbientBackdrop
import com.lechixy.kick.components.PlayerAmbientState
import com.lechixy.kick.components.PlayerVideoSurface
import com.lechixy.kick.components.PlayerVideoZoom
import com.lechixy.kick.components.TrackAmbientVideoLayout
import com.lechixy.kick.components.VideoBarOptions
import com.lechixy.kick.components.VideoBars
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame
import com.lechixy.kick.components.channel.chat.VideoBackdropBlur
import com.lechixy.kick.components.channel.chat.rememberLegacyFullscreenChatFrame
import com.lechixy.kick.components.updateBounds
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PlayerAmbientPlaybackTest {
    @Test
    fun savedAmbientModesStayVisibleAcrossEveryTransitionWithZoomAndNoiseReduction() = withFixture { fixture ->
        val scope = CoroutineScope(Dispatchers.IO)
        val preferences = File(fixture.parentFile, "${fixture.name}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { preferences })
        try {
            withPlayback(fixture, settings) { playback ->
                playback.await("first paused video frame") { firstFrame > 0 }
                val transitions = listOf(
                    PlayerAmbientMode.Dynamic, PlayerAmbientMode.Live, PlayerAmbientMode.Off,
                    PlayerAmbientMode.Live, PlayerAmbientMode.Dynamic, PlayerAmbientMode.Off,
                )
                for ((scale, noise) in listOf(1f to false, 1.04f to false, 1.04f to true, 1f to true, 1f to false)) {
                    if (noise && Build.VERSION.SDK_INT < 33) continue
                    playback.onMain {
                        zoom = PlayerVideoZoom(scale)
                        reduceNoise = noise
                    }
                    for (selectedMode in transitions) {
                        runBlocking { settings.set(SettingsKeys.AMBIENT_MODE, selectedMode.value) }
                        playback.await("applied setting ${selectedMode.value}") { appliedMode == selectedMode }
                        playback.await("ambient ${selectedMode.value}, zoom=$scale, noise=$noise") {
                            when (selectedMode) {
                                PlayerAmbientMode.Dynamic -> state.frame is AmbientFrame.Palette
                                PlayerAmbientMode.Live -> state.frame is AmbientFrame.Projection
                                PlayerAmbientMode.Off -> state.frame == null
                            }
                        }
                        val pixel = playback.readLetterboxPixel()
                        assertTrue(
                            "Mode ${selectedMode.value}, zoom=$scale, noise=$noise rendered $pixel",
                            if (selectedMode == PlayerAmbientMode.Off) pixel < 5 else pixel > 10,
                        )
                    }
                }
                playback.onMain { surfaceRevision++ }
                runBlocking { settings.set(SettingsKeys.AMBIENT_MODE, PlayerAmbientMode.Live.value) }
                playback.await("live ambient after replacing the texture view") {
                    firstFrame > 1 && state.frame is AmbientFrame.Projection
                }
                assertTrue(playback.readLetterboxPixel() > 10)
            }
        } finally {
            scope.cancel()
            preferences.delete()
        }
    }

    @Test
    fun letterboxUsesTheAmbientColorAndDarkThemeMakesItStronger() = withVideo { playback ->
        playback.await("video in letterbox layout") { state.bounds?.hasLetterboxSpace == true }
        val bounds = playback.onMain { checkNotNull(state.bounds) }
        val video = bounds.videoInWindow
        val horizontalBar = bounds.videoInPlayer.top > 8f
        val x = if (horizontalBar) (video.left + video.width * 0.25f).toInt() else video.left.toInt() - 6
        val y = if (horizontalBar) video.top.toInt() - 6 else video.center.y.toInt()
        val insideX = if (horizontalBar) x else x + 18
        val insideY = if (horizontalBar) y + 18 else y
        assertTrue("Test video needs a letterbox at least 8 px wide", x >= 0 && y >= 0)
        assertTrue(AndroidColor.red(playback.readWindowPixel(x, y)) < 5)

        playback.onMain { mode = PlayerAmbientMode.Live }
        playback.await("ambient frame") { state.frame is AmbientFrame.Projection }
        val light = AndroidColor.red(playback.readWindowPixel(x, y))
        assertTrue("Expected red light in the letterbox, got $light", light > 15)

        playback.onMain { darkTheme = true }
        val dark = AndroidColor.red(playback.readWindowPixel(x, y))
        assertTrue("Expected stronger light in dark theme, got light=$light dark=$dark", dark > light + 15)
        assertTrue(AndroidColor.red(playback.readWindowPixel(insideX, insideY)) > 200)
    }

    @Test
    fun liveKeepsBothEdgeColorsAndUpdatesAfterAPausedSeek() = withVideo { playback ->
        playback.onMain {
            mode = PlayerAmbientMode.Live
            player.play()
        }
        playback.await("red and green Live frame") {
            val bitmap = (state.frame as? AmbientFrame.Projection)?.bitmap ?: return@await false
            AndroidColor.red(bitmap.getPixel(0, bitmap.height / 2)) > 200 &&
                AndroidColor.green(bitmap.getPixel(bitmap.width - 1, bitmap.height / 2)) > 200
        }
        playback.onMain { player.pause() }
        SystemClock.sleep(300)
        val pausedFrame = playback.onMain { state.frame }
        assertNotNull(pausedFrame)
        SystemClock.sleep(200)
        assertSame(pausedFrame, playback.onMain { state.frame })

        playback.onMain { seekWhilePaused(2_500L) }
        playback.await("blue and yellow Live frame after seeking") {
            val bitmap = (state.frame as? AmbientFrame.Projection)?.bitmap ?: return@await false
            val left = bitmap.getPixel(0, bitmap.height / 2)
            val right = bitmap.getPixel(bitmap.width - 1, bitmap.height / 2)
            AndroidColor.blue(left) > 200 && AndroidColor.red(right) > 200 && AndroidColor.green(right) > 200
        }
        playback.onMain { mode = PlayerAmbientMode.Dynamic }
        playback.await("blue and yellow Dynamic palette") {
            val colors = (state.frame as? AmbientFrame.Palette)?.colors ?: return@await false
            colors[3].blue > 0.8f && colors[4].red > 0.8f && colors[4].green > 0.8f
        }
        playback.onMain { mode = PlayerAmbientMode.Off }
        playback.await("cleared ambient frame") { state.frame == null }
    }

    @Test
    fun coloredBarsCanBeMaskedAndCroppedIndependentlyOfAmbient() =
        withFixture("ambient-video-bars.mp4") { fixture ->
            withPlayback(fixture, null) { playback ->
                playback.onMain {
                    darkTheme = true
                    mode = PlayerAmbientMode.Dynamic
                }
                playback.await("colored top and side masks") {
                    state.barLayout.mask.top > 0f && state.barLayout.mask.left > 0f
                }
                val raw = playback.onMain { checkNotNull(state.bounds).videoInWindow }
                assertEquals(
                    AndroidColor.BLACK,
                    playback.readWindowPixel(raw.center.x.toInt(), raw.top.toInt() - 4),
                )
                assertTrue(AndroidColor.green(playback.readWindowPixel(raw.center.x.toInt(), raw.top.toInt() + 5)) > 130)

                playback.onMain { barOptions = barOptions.copy(removeHorizontal = true) }
                playback.await("horizontal crop and preserved side mask") {
                    state.barLayout.crop.top > 0f && state.barLayout.crop.left == 0f &&
                        state.barLayout.mask.left > 0f && state.barLayout.mask.top == 0f &&
                        (state.frame as? AmbientFrame.Palette)?.colors?.get(1)?.red?.let { it > 0.65f } == true &&
                        (state.bounds?.videoInWindow?.height ?: raw.height) < raw.height
                }
                val cropped = playback.onMain { checkNotNull(state.bounds).videoInWindow }
                val x = cropped.center.x.toInt()
                val y = cropped.top.toInt()
                val glow = playback.readWindowPixel(x, y - 4)
                assertTrue("Expected red ambient outside the crop, got ${Integer.toHexString(glow)}",
                    AndroidColor.red(glow) in 21..155 && AndroidColor.red(glow) > AndroidColor.green(glow) + 15)
                val newEdge = playback.readWindowPixel(x, y + 8)
                assertTrue("Expected the cropped video edge to be red, got ${Integer.toHexString(newEdge)}",
                    AndroidColor.red(newEdge) > 160 && AndroidColor.green(newEdge) < 80)

                playback.onMain { mode = PlayerAmbientMode.Live }
                playback.await("live projection from the new top edge") {
                    val bitmap = (state.frame as? AmbientFrame.Projection)?.bitmap ?: return@await false
                    AndroidColor.red(bitmap.getPixel(bitmap.width / 2, 0)) > 140 && state.barLayout.mask.left > 0f
                }
                val visibleCrop = playback.onMain { state.barLayout.crop }
                playback.onMain { showAmbient = false }
                playback.await("crop stays fitted when fullscreen hides ambient") {
                    state.frame == null && state.barLayout.crop == visibleCrop
                }
                playback.onMain {
                    mode = PlayerAmbientMode.Off
                    barOptions = barOptions.copy(removeVertical = true)
                }
                playback.await("both axes cropped with ambient off") {
                    state.frame == null && state.barLayout.crop.top > 0f && state.barLayout.crop.left > 0f
                }
                val fitted = playback.onMain { checkNotNull(state.bounds).videoInWindow }
                val side = playback.readWindowPixel(fitted.left.toInt() + 20, fitted.center.y.toInt())
                assertTrue("Expected the cropped side to be red, got ${Integer.toHexString(side)}",
                    AndroidColor.red(side) > 160 && AndroidColor.blue(side) < 80)
                playback.onMain { zoom = PlayerVideoZoom(scale = 1.2f) }
                playback.await("zoom applies on top of the cropped fit") {
                    (state.bounds?.videoInWindow?.width ?: 0f) > fitted.width * 1.19f
                }
                playback.onMain { zoom = PlayerVideoZoom() }
                playback.onMain { allowCrop = false }
                playback.await("full frame in a compact player") {
                    state.barLayout.crop.isEmpty && state.bounds?.videoInWindow?.height == raw.height
                }
                playback.onMain { allowCrop = true }
                playback.await("restored crop") { state.barLayout.crop.left > 0f }
                playback.onMain { seekWhilePaused(3_500L) }
                playback.await("no bars after a paused seek") {
                    hasSeekFrame() && state.barLayout.crop.isEmpty && state.bounds?.videoInWindow?.height == raw.height
                }
                assertTrue(AndroidColor.green(playback.readWindowPixel(x - 10, raw.top.toInt() + 8)) > 125)
            }
        }

    @Test
    fun comparesUiFramesAndVideoDropsAcrossAmbientModes() = withVideo { playback ->
        val cases = listOf(
            PlayerAmbientMode.Off to 30,
            PlayerAmbientMode.Dynamic to 30,
            PlayerAmbientMode.Live to 30,
            PlayerAmbientMode.Live to 60,
        )
        for ((mode, fps) in cases) {
            val uiFrames = mutableListOf<Long>()
            val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                uiFrames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            }
            val initialDrops = playback.onMain {
                this.mode = mode
                this.fps = fps
                player.seekTo(0L)
                window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
                player.videoDecoderCounters?.droppedBufferCount ?: 0
            }
            try {
                playback.onMain { player.play() }
                var sceneDelayMs: Long? = null
                if (mode != PlayerAmbientMode.Off) {
                    playback.await("red ambient light for ${mode.value} at $fps fps") {
                        when (val frame = state.frame) {
                            is AmbientFrame.Palette -> frame.colors[3].red > 0.8f
                            is AmbientFrame.Projection -> AndroidColor.red(frame.bitmap.getPixel(0, frame.bitmap.height / 2)) > 200
                            null -> false
                        }
                    }
                    playback.await("blue ambient light for ${mode.value} at $fps fps") {
                        val isBlue = when (val frame = state.frame) {
                            is AmbientFrame.Palette -> frame.colors[3].blue > 0.8f
                            is AmbientFrame.Projection -> AndroidColor.blue(frame.bitmap.getPixel(0, frame.bitmap.height / 2)) > 200
                            null -> false
                        }
                        if (isBlue) sceneDelayMs = player.currentPosition - 2_000L
                        isBlue
                    }
                }
                playback.await("playback ended for ${mode.value} at $fps fps") { player.playbackState == Player.STATE_ENDED }
                val result = playback.onMain {
                    val sorted = uiFrames.sorted()
                    val p95 = sorted.getOrNull(((sorted.size - 1) * 0.95).toInt()) ?: 0L
                    val drops = (player.videoDecoderCounters?.droppedBufferCount ?: initialDrops) - initialDrops
                    "mode=${mode.value} limit=$fps uiFrames=${sorted.size} p95Ms=${p95 / 1_000_000.0} " +
                        "videoDrops=$drops sceneDelayMs=$sceneDelayMs"
                }
                Log.i("AmbientPlaybackTest", result)
            } finally {
                playback.onMain { window.removeOnFrameMetricsAvailableListener(listener) }
            }
        }
    }

    @Test
    fun comparesPlaybackFramesWithAndWithoutFrostedControls() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        withVideo { playback ->
            playback.onMain { showControls = true }
            playback.await("first fixture frame") { firstFrame > 0 }
            for (frosted in listOf(false, true)) {
                val uiFrames = mutableListOf<Long>()
                val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                    if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) {
                        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
                        if (duration > 0) uiFrames += duration
                    }
                }
                playback.onMain {
                    frostedControls = frosted
                    player.seekTo(0L)
                    window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
                    player.play()
                }
                try {
                    playback.await("end of the fixture with frostedControls=$frosted") {
                        player.playbackState == Player.STATE_ENDED
                    }
                    val sorted = playback.onMain { uiFrames.sorted() }
                    assertTrue("Expected window frame samples", sorted.isNotEmpty())
                    val p50 = sorted[((sorted.size - 1) * 0.50).toInt()] / 1_000_000.0
                    val p95 = sorted[((sorted.size - 1) * 0.95).toInt()] / 1_000_000.0
                    Log.i("BackdropPlaybackTest", "frosted=$frosted frames=${sorted.size} p50Ms=$p50 p95Ms=$p95")
                } finally {
                    playback.onMain { window.removeOnFrameMetricsAvailableListener(listener) }
                }
            }
        }
    }

    @Test
    fun legacyFrostedControlsFollowARealPausedSeek() {
        assumeTrue(Build.VERSION.SDK_INT == 30)
        withVideo { playback ->
            playback.onMain {
                showControls = true
                frostedControls = true
            }
            playback.await("first frosted sample") { legacyControlsReady() }
            val bounds = playback.onMain { checkNotNull(state.bounds) }
            val x = bounds.videoInWindow.center.x.toInt() - 4
            val y = bounds.videoInWindow.center.y.toInt()
            val first = playback.readWindowPixel(x, y)
            playback.onMain { seekWhilePaused(2_500L) }
            playback.await("new blue video under the controls") { legacyControlsReady(blueScene = true) }
            val second = playback.readWindowPixel(x, y)
            assertTrue(AndroidColor.red(first) > AndroidColor.blue(first))
            assertTrue(AndroidColor.blue(second) > AndroidColor.blue(first) + 20)
        }
    }

    private fun withVideo(test: (Playback) -> Unit) = withFixture { withPlayback(it, null, test) }

    private fun withPlayback(fixture: File, settings: SettingsRepository?, test: (Playback) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var playback: Playback
            scenario.onActivity { playback = Playback(it, fixture, settings) }
            try {
                test(playback)
            } finally {
                playback.onMain { player.release() }
            }
        }
    }

    private fun withFixture(asset: String = "ambient-edges-60fps.mp4", test: (File) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File.createTempFile("ambient-edges", ".mp4", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open(asset).use { source ->
                fixture.outputStream().use { source.copyTo(it) }
            }
            test(fixture)
        } finally {
            fixture.delete()
        }
    }

    private class Playback(activity: ComponentActivity, fixture: File, settings: SettingsRepository?) {
        val window: Window = activity.window
        val state = PlayerAmbientState()
        val player = ExoPlayer.Builder(activity).build()
        var mode by mutableStateOf(PlayerAmbientMode.Off)
        var appliedMode = PlayerAmbientMode.Off
            private set
        var fps by mutableIntStateOf(30)
        var darkTheme by mutableStateOf(false)
        var showAmbient by mutableStateOf(true)
        var zoom by mutableStateOf(PlayerVideoZoom())
        var barOptions by mutableStateOf(VideoBarOptions(preserve = true))
        var allowCrop by mutableStateOf(true)
        var reduceNoise by mutableStateOf(false)
        var surfaceRevision by mutableIntStateOf(0)
        private var playing by mutableStateOf(false)
        var firstFrame by mutableIntStateOf(0)
            private set
        private var seek by mutableStateOf<AmbientSeek?>(null)
        private val textureState = mutableStateOf<TextureView?>(null)
        private val texture by textureState
        private var coordinates: LayoutCoordinates? = null
        var showControls by mutableStateOf(false)
        var frostedControls by mutableStateOf(false)
        private var legacyControlFrame: State<FullscreenChatVideoFrame?>? = null

        init {
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onRenderedFirstFrame() {
                    firstFrame++
                }
            })
            player.setMediaItem(MediaItem.fromUri(fixture.toURI().toString()))
            player.prepare()
            activity.setContent {
                MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                    val ambientMode = if (settings == null) mode else {
                        val storedMode by remember(settings) { settings.get(SettingsKeys.AMBIENT_MODE) }
                            .collectAsStateWithLifecycle(SettingsKeys.AMBIENT_MODE.defaultValue)
                        PlayerAmbientMode.fromValue(storedMode)
                    }
                    SideEffect { appliedMode = ambientMode }
                    TrackAmbientVideoLayout(state, texture, { zoom }) { coordinates }
                    CapturePlayerAmbient(
                        state, texture, ambientMode, fps, firstFrame > 0 && showAmbient, playing, "fixture", "fixture", firstFrame, seek,
                        barOptions = barOptions, allowCrop = allowCrop && firstFrame > 0,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned {
                        coordinates = it
                        state.updateBounds(texture, it, zoom)
                    }) {
                        PlayerAmbientBackdrop(state, modifier = Modifier.matchParentSize())
                        key(surfaceRevision) {
                            PlayerVideoSurface(
                                player, playing, Modifier.fillMaxSize().clipToBounds(), zoom = { zoom },
                                textureState = textureState,
                                crop = { if (allowCrop) state.barLayout.crop else VideoBars.None },
                            )
                        }
                        if (reduceNoise) VideoBackdropBlur(texture, emptyList(), reduceNoise = true)
                        if (showControls) {
                            val regions = remember { FrostedPlayerRegions() }
                            val backdropTexture = texture.takeIf { frostedControls && firstFrame > 0 }
                            VideoBackdropBlur(backdropTexture, regions.regions.values.toList())
                            val frame = if (Build.VERSION.SDK_INT == 30) {
                                rememberLegacyFullscreenChatFrame(backdropTexture, playing, soft = false, frosted = true)
                            } else null
                            SideEffect { legacyControlFrame = frame }
                            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                for (size in listOf(48.dp, 60.dp, 48.dp)) {
                                    FrostedPlaybackButton({}, frame, Modifier.size(size), regions, videoReady = firstFrame > 0) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        fun legacyControlsReady(blueScene: Boolean = false): Boolean {
            val bitmap = legacyControlFrame?.value?.frostedBitmap ?: return false
            val pixel = bitmap.toPixelMap()[bitmap.width / 4, bitmap.height / 2]
            return if (blueScene) pixel.blue > 0.7f else pixel.red > 0.7f
        }

        fun seekWhilePaused(positionMs: Long) {
            seek = AmbientSeek((seek?.sequence ?: 0) + 1, texture?.surfaceTexture?.timestamp ?: 0L)
            player.seekTo(positionMs)
        }

        fun hasSeekFrame(): Boolean {
            val timestamp = texture?.surfaceTexture?.timestamp ?: 0L
            return timestamp != 0L && timestamp != seek?.previousTimestamp
        }

        fun <T> onMain(block: Playback.() -> T): T {
            var result: Result<T>? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching { block() } }
            return checkNotNull(result).getOrThrow()
        }

        fun readWindowPixel(x: Int, y: Int): Int {
            // Allow Compose and HWUI to submit the frame for the new draw state.
            SystemClock.sleep(120)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val completion = CountDownLatch(1)
            var status = PixelCopy.ERROR_UNKNOWN
            onMain {
                PixelCopy.request(window, Rect(x, y, x + 1, y + 1), bitmap, {
                    status = it
                    completion.countDown()
                }, Handler(Looper.getMainLooper()))
            }
            assertTrue(completion.await(2, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, status)
            return bitmap.getPixel(0, 0)
        }

        fun readLetterboxPixel(): Int {
            await("letterbox") { state.bounds?.hasLetterboxSpace == true }
            val bounds = onMain { checkNotNull(state.bounds) }
            val video = bounds.videoInWindow
            val horizontalBar = bounds.videoInPlayer.top > 8f
            val x = if (horizontalBar) (video.left + video.width * 0.25f).toInt() else video.left.toInt() - 6
            val y = if (horizontalBar) video.top.toInt() - 6 else video.center.y.toInt()
            return AndroidColor.red(readWindowPixel(x, y))
        }

        fun await(description: String, condition: Playback.() -> Boolean) {
            awaitCondition(description) { onMain(condition) }
        }
    }
}

private fun awaitCondition(description: String, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(8)
    while (SystemClock.elapsedRealtime() < deadline) {
        if (condition()) return
        SystemClock.sleep(32)
    }
    assertTrue("Timed out waiting for $description", condition())
}
