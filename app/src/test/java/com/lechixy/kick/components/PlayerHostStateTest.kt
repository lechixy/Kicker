package com.lechixy.kick.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelLivestream
import com.lechixy.kick.data.player.model.RecordedPlayback
import kotlinx.coroutines.runBlocking

class PlayerHostStateTest {
    @Test fun theaterNavigationCanOpenAfterChatClosesAndCloseAgain() {
        val host = liveHost()
        host.toggleTheaterMode()
        host.updateChatVisibility(false)
        assertTrue(host.isTheaterMode)
        assertFalse(host.isSideChatOpen(isWide = true))
        host.updatePlayerNavigationVisibility(true)
        assertTrue(host.isPlayerNavigationVisible)
        host.updatePlayerNavigationVisibility(false)
        assertFalse(host.isPlayerNavigationVisible)
        assertFalse(host.isSideChatOpen(isWide = true))
    }

    @Test fun navigationDragIsResetWhenLeavingTheaterOrMinimizing() {
        val host = liveHost()
        host.toggleTheaterMode()
        host.updatePlayerNavigationVisibility(true)
        host.isDirectNavigationDrag = true
        host.minimize()
        assertFalse(host.isDirectNavigationDrag)
        assertFalse(host.isTheaterNavigationVisible)
        host.expand()
        assertFalse(host.isPlayerNavigationVisible)
    }

    private fun liveHost() = PlayerHostState().apply {
        open("one")
        onChannelLoaded("one", ChannelDetail(1, slug = "one", livestream = ChannelLivestream(1)))
    }

    @Test fun fullscreenChatGesturesDoNotOverwriteInlineLayout() {
        val host = liveHost()
        host.setFullscreen(true)
        host.updateChatVisibility(false)
        host.updateFullscreenNavigationVisibility(true)
        host.setFullscreen(false)
        assertEquals(PlayerMode.Expanded, host.mode)
        assertTrue(host.isChatVisible)
        assertFalse(host.isTheaterMode)
        assertFalse(host.isFullscreenNavigationVisible)
    }

    @Test fun autoFullscreenRestoresTheaterAndHiddenChatPreference() {
        val host = liveHost()
        host.isTheaterMode = true
        host.isChatVisible = false
        host.consumeAutoFullscreen(true)
        host.updateChatVisibility(true)
        host.setFullscreen(false)
        assertTrue(host.isTheaterMode)
        assertFalse(host.isChatVisible)
    }

    @Test fun fullscreenOverlayFollowsTheSidePanelProgressInBothDirections() = runBlocking {
        val host = liveHost()
        host.toggleTheaterMode()
        host.setFullscreen(true)
        assertFalse(host.isSideChatOpen(isWide = true))
        assertEquals(1f, host.fullscreenChatOverlayAlpha, 0f)
        host.snapChatOpenProgress(0.25f)
        assertEquals(0.75f, host.fullscreenChatOverlayAlpha, 0f)
        host.updateChatVisibility(true)
        assertTrue(host.isSideChatOpen(isWide = true))
        assertEquals(0.75f, host.fullscreenChatOverlayAlpha, 0f)
        host.snapChatOpenProgress(1f)
        assertEquals(0f, host.fullscreenChatOverlayAlpha, 0f)
        host.updateChatVisibility(false)
        assertEquals(0f, host.fullscreenChatOverlayAlpha, 0f)
        host.snapChatOpenProgress(0.5f)
        assertEquals(0.5f, host.fullscreenChatOverlayAlpha, 0f)
        host.snapChatOpenProgress(0f)
        assertEquals(1f, host.fullscreenChatOverlayAlpha, 0f)

        host.setFullscreen(false)
        assertTrue(host.isSideChatOpen(isWide = true))
        assertEquals(0f, host.fullscreenChatOverlayAlpha, 0f)
        host.setFullscreen(true)
        assertFalse(host.isSideChatOpen(isWide = true))
        assertEquals(1f, host.fullscreenChatOverlayAlpha, 0f)
    }

    @Test fun fullscreenChatButtonRestoresOverlayFromSideChatAndCanTurnItOff() {
        val host = liveHost()
        host.setFullscreen(true)
        val enabled = host.toggleFullscreenChatOverlay(enabled = false)
        assertTrue(enabled)
        host.updateChatVisibility(true)
        assertTrue(host.toggleFullscreenChatOverlay(enabled))
        assertFalse(host.isSideChatOpen(isWide = true))
        assertFalse(host.toggleFullscreenChatOverlay(enabled))
    }

    @Test fun recordingSeeksInTheSameBucketStillCreateNewChatPositions() {
        val host = liveHost()
        host.playRecording(RecordedPlayback("replay", "", 30_000))
        host.seekRecording("replay", 6_000)
        assertEquals(RecordingChatSeek(1, 6_000), host.recordingChatSeek)
        host.seekRecording("replay", 5_500)
        assertEquals(RecordingChatSeek(2, 5_500), host.recordingChatSeek)
        assertEquals(5_500L, host.recordingPositionMs)
        host.updateRecordingPosition("replay", 5_750)
        assertEquals(RecordingChatSeek(2, 5_500), host.recordingChatSeek)
    }

    @Test fun minimizingFromFullscreenRestoresInlineLayoutBeforeExpanding() {
        val host = liveHost()
        host.setFullscreen(true)
        host.updateChatVisibility(false)
        host.minimize()
        assertEquals(PlayerMode.Mini, host.mode)
        host.expand()
        assertTrue(host.isChatVisible)
        assertFalse(host.isTheaterMode)
    }

    @Test fun interruptedCardExpansionCannotBeReplayedByMiniPlayer() {
        val host = PlayerHostState()
        host.open("one", ExpansionSource(Rect(0f, 0f, 300f, 200f)))
        host.onChannelLoaded("one", ChannelDetail(1, slug = "one", livestream = ChannelLivestream(1)))
        host.minimize()
        assertNull(host.expansionSource)
        host.expand()
        assertNull(host.expansionSource)
        host.open("two", ExpansionSource(Rect(0f, 200f, 300f, 400f)))
        assertNull(host.expansionSource)
        assertNull(host.channel)
        host.onChannelLoaded("one", ChannelDetail(1, slug = "one", livestream = ChannelLivestream(1)))
        assertNull(host.channel)
    }
}
