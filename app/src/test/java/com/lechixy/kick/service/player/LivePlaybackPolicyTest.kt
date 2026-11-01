package com.lechixy.kick.service.player

import com.lechixy.kick.data.account.model.Window

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class LivePlaybackPolicyTest {
    @Test fun liveResumesSeekOnceForEachPauseRegardlessOfCommandSource() {
        val calls = mutableListOf<String>()
        val player = player(live = true, calls = calls)
        val listener = LiveResumeListener(player)
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        // Changing the reason while already playing must not cause another seek.
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)
        listener.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertEquals(listOf("seekToDefaultPosition", "seekToDefaultPosition"), calls)
    }

    @Test fun recordingsKeepTheirPausedPosition() {
        val calls = mutableListOf<String>()
        val listener = LiveResumeListener(player(live = false, calls = calls))
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertTrue(calls.isEmpty())
    }

    @Test fun attachingWhilePlayingDoesNotSeek() {
        val calls = mutableListOf<String>()
        val listener = LiveResumeListener(player(live = true, playing = true, calls = calls))
        listener.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        assertTrue(calls.isEmpty())
    }

    @Test fun unknownDistanceIsNeverLiveAndHysteresisUsesExtraLag() {
        assertFalse(isNearLiveTarget(C.TIME_UNSET, true))
        assertFalse(isNearLiveTarget(8_000L, true))
        assertTrue(isNearLiveTarget(2_000L, true))
        assertFalse(isNearLiveTarget(2_000L, false))
        assertTrue(isNearLiveTarget(1_000L, false))
    }

    @Test fun displayedLagUsesDefaultTargetRatherThanSyntheticWindowEndOrDeviceClock() {
        val timeline = timeline(defaultMs = 27_000, durationMs = 30_000)
        val player = player(live = true, calls = mutableListOf(), timeline = timeline, positionMs = 25_000)
        // Synthetic edge is 5s away, but only 2s needs to be recovered to reach the target.
        assertEquals(2_000L, player.distanceBehindLiveTargetMs())
        assertEquals(0L, player(live = true, calls = mutableListOf(), timeline = timeline,
            positionMs = 28_000).distanceBehindLiveTargetMs())
    }

    @Test fun emptyAndUnknownTimelinesDoNotReportZeroLag() {
        assertEquals(C.TIME_UNSET, player(live = true, calls = mutableListOf()).distanceBehindLiveTargetMs())
        assertEquals(C.TIME_UNSET, player(live = true, calls = mutableListOf(),
            timeline = timeline(C.TIME_UNSET, 30_000)).distanceBehindLiveTargetMs())
        assertEquals(C.TIME_UNSET, player(live = false, calls = mutableListOf(),
            timeline = timeline(27_000, 30_000)).distanceBehindLiveTargetMs())
    }

    private fun timeline(defaultMs: Long, durationMs: Long): Timeline = object : Timeline() {
        override fun getWindowCount() = 1
        override fun getPeriodCount() = 1
        override fun getWindow(index: Int, window: Window, defaultPositionProjectionUs: Long): Window =
            window.apply {
                defaultPositionUs = if (defaultMs == C.TIME_UNSET) C.TIME_UNSET else defaultMs * 1000
                durationUs = durationMs * 1000
            }
        override fun getPeriod(index: Int, period: Period, setIds: Boolean): Period = error("Unused")
        override fun getIndexOfPeriod(uid: Any) = 0
        override fun getUidOfPeriod(index: Int): Any = "period"
    }

    private fun player(live: Boolean, playing: Boolean = false, calls: MutableList<String>,
        timeline: Timeline = Timeline.EMPTY, positionMs: Long = 0L): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlayWhenReady" -> playing
                "isCurrentMediaItemLive" -> live
                "getCurrentTimeline" -> timeline
                "getCurrentMediaItemIndex" -> 0
                "getCurrentPosition" -> positionMs
                "seekToDefaultPosition" -> { calls.add(method.name); null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
}
