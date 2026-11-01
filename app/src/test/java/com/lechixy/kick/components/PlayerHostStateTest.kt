package com.lechixy.kick.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.components.ExpansionSource
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelLivestream

class PlayerHostStateTest {
    @Test fun theaterNavigationCanOpenAfterChatClosesAndCloseAgain() {
        val host = liveHost()
        host.toggleTheaterMode()
        host.setSideChatVisible(false, isWide = true)
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
        host.setSideChatVisible(false, isWide = true)
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
        host.setSideChatVisible(true, isWide = true)
        host.setFullscreen(false)
        assertTrue(host.isTheaterMode)
        assertFalse(host.isChatVisible)
    }

    @Test fun minimizingFromFullscreenRestoresInlineLayoutBeforeExpanding() {
        val host = liveHost()
        host.setFullscreen(true)
        host.setSideChatVisible(false, isWide = true)
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
