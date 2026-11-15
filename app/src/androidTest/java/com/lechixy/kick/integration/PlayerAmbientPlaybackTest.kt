package com.lechixy.kick.integration

import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.Rect
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.lechixy.kick.components.PlayerAmbientBackdrop
import com.lechixy.kick.components.PlayerAmbientState
import com.lechixy.kick.components.PlayerVideoSurface
import com.lechixy.kick.components.TrackAmbientVideoLayout
import com.lechixy.kick.components.updateBounds
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class PlayerAmbientPlaybackTest {
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

    private fun withVideo(test: (Playback) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File.createTempFile("ambient-edges", ".mp4", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("ambient-edges-60fps.mp4").use { source ->
                fixture.outputStream().use { source.copyTo(it) }
            }
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var playback: Playback
                scenario.onActivity { playback = Playback(it, fixture) }
                try {
                    test(playback)
                } finally {
                    playback.onMain { player.release() }
                }
            }
        } finally {
            fixture.delete()
        }
    }

    private class Playback(activity: ComponentActivity, fixture: File) {
        val window: Window = activity.window
        val state = PlayerAmbientState()
        val player = ExoPlayer.Builder(activity).build()
        var mode by mutableStateOf(PlayerAmbientMode.Off)
        var fps by mutableIntStateOf(30)
        var darkTheme by mutableStateOf(false)
        private var playing by mutableStateOf(false)
        private var firstFrame by mutableIntStateOf(0)
        private var seek by mutableStateOf<AmbientSeek?>(null)
        private var texture by mutableStateOf<TextureView?>(null)
        private var coordinates: LayoutCoordinates? = null

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
                    TrackAmbientVideoLayout(state, texture) { coordinates }
                    CapturePlayerAmbient(
                        state, texture, mode, fps, firstFrame > 0, playing, "fixture", "fixture", firstFrame, seek,
                    )
                    Box(Modifier.fillMaxSize().background(Color.Black).onGloballyPositioned {
                        coordinates = it
                        state.updateBounds(texture, it)
                    }) {
                        PlayerAmbientBackdrop(state, modifier = Modifier.matchParentSize())
                        PlayerVideoSurface(player, playing, Modifier.fillMaxSize(), onTextureChanged = { texture = it })
                    }
                }
            }
        }

        fun seekWhilePaused(positionMs: Long) {
            seek = AmbientSeek((seek?.sequence ?: 0) + 1, texture?.surfaceTexture?.timestamp ?: 0L)
            player.seekTo(positionMs)
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

        fun await(description: String, condition: Playback.() -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(8)
            while (SystemClock.elapsedRealtime() < deadline) {
                if (onMain(condition)) return
                SystemClock.sleep(32)
            }
            assertTrue("Timed out waiting for $description", onMain(condition))
        }
    }
}
