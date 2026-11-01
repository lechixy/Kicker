package com.lechixy.kick.integration

import com.lechixy.kick.data.common.model.Channel

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.service.buildPlaybackMediaItem
import com.lechixy.kick.service.player.PlaybackProgressTracker

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackProgressTrackerTest {
    private class Session(val id: String, isLive: Boolean = false) {
        val item = buildPlaybackMediaItem("https://example.com/master.m3u8", "media-$id", isLive,
            "Test", "Channel", null, android.net.Uri.EMPTY, vodDurationMs = 100_000, recordingId = id)
        var position = 20_000L
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getApplicationLooper" -> Looper.getMainLooper()
                "getCurrentMediaItem" -> item
                "getPlaybackState" -> Player.STATE_READY
                "getCurrentPosition" -> position
                "getDuration" -> 100_000L
                "addListener", "removeListener" -> null
                else -> error("Unexpected method: ${method.name}")
            }
        } as Player
    }
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun pausePlayAndSeekEventsPersistCurrentPosition() {
        val session = Session("instrumented-events")
        onMain {
            val tracker = PlaybackProgressTracker(session.player)
            try {
                tracker.onEvents(session.player, Player.Events(FlagSet.Builder().add(Player.EVENT_PLAY_WHEN_READY_CHANGED).build()))
                assertEquals(20_000L, PlaybackProgressStore.get(session.id)?.positionMs)
                session.position = 35_000
                tracker.onEvents(session.player, Player.Events(FlagSet.Builder().add(Player.EVENT_POSITION_DISCONTINUITY).build()))
                assertEquals(35_000L, PlaybackProgressStore.get(session.id)?.positionMs)
                session.position = 45_000
            } finally { tracker.close() }
            assertEquals(45_000L, PlaybackProgressStore.get(session.id)?.positionMs)
            val prefs = InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("recording_progress", android.content.Context.MODE_PRIVATE)
            assertTrue(prefs.getString("progress", "")!!.contains(session.id))
        }
    }

    @Test fun periodicCheckpointIsSavedWithoutAControlEvent() {
        val session = Session("instrumented-periodic")
        val done = CountDownLatch(1)
        var tracker: PlaybackProgressTracker? = null
        onMain {
            tracker = PlaybackProgressTracker(session.player)
            android.os.Handler(Looper.getMainLooper()).postDelayed({ done.countDown() }, 15_500)
        }
        try {
            assertTrue(done.await(20, TimeUnit.SECONDS))
            assertEquals(20_000L, PlaybackProgressStore.get(session.id)?.positionMs)
        } finally { onMain { tracker?.close() } }
    }

    @Test fun liveMediaNeverCreatesRecordingProgress() {
        val session = Session("instrumented-live", isLive = true)
        onMain {
            val tracker = PlaybackProgressTracker(session.player)
            tracker.onEvents(session.player, Player.Events(FlagSet.Builder().add(Player.EVENT_PLAY_WHEN_READY_CHANGED).build()))
            tracker.close()
            assertNull(PlaybackProgressStore.get(session.id))
        }
    }
}
