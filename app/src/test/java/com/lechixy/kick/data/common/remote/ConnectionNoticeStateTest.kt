package com.lechixy.kick.data.common.remote

import org.junit.Assert.*
import org.junit.Test

class ConnectionNoticeStateTest {
    @Test fun startingOnlineDoesNotShowAReconnectionMessage() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
    }

    @Test fun offlinePersistsUntilInternetReturnsAndRestoredCanExpire() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Offline)
        state.expireRestored()
        assertEquals(ConnectionNotice.Offline, state.notice)
        state.update(ConnectionQuality.Online)
        assertEquals(ConnectionNotice.Restored, state.notice)
        state.expireRestored()
        assertNull(state.notice)
        state.update(ConnectionQuality.Online)
        assertNull(state.notice)
    }

    @Test fun expiredReconnectionCannotHideANewDisconnection() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Offline)
        state.update(ConnectionQuality.Online)
        state.update(ConnectionQuality.Offline)
        state.expireRestored()
        assertEquals(ConnectionNotice.Offline, state.notice)
    }

    @Test fun weakNetworkRecoveryIsVisibleWithoutLosingOfflinePriority() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Weak)
        assertEquals(ConnectionNotice.Weak, state.notice)
        state.update(ConnectionQuality.Offline)
        assertEquals(ConnectionNotice.Offline, state.notice)
        state.update(ConnectionQuality.Weak)
        assertEquals(ConnectionNotice.Weak, state.notice)
        state.update(ConnectionQuality.Online)
        assertEquals(ConnectionNotice.Restored, state.notice)
    }
    @Test fun backgroundRecoveryDoesNotAnnounceRestoredOnResume() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Online)
        state.setForeground(false)
        state.update(ConnectionQuality.Offline)
        assertNull(state.notice)
        state.update(ConnectionQuality.Online)
        state.setForeground(true)
        assertNull(state.notice)
    }

    @Test fun resumingOfflineStillShowsOfflineAndSubsequentRecovery() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Online)
        state.setForeground(false)
        state.update(ConnectionQuality.Offline)
        state.setForeground(true)
        assertEquals(ConnectionNotice.Offline, state.notice)
        state.update(ConnectionQuality.Online)
        assertEquals(ConnectionNotice.Restored, state.notice)
    }

    @Test fun foregroundRecoveryNoticeDoesNotPersistAcrossBackgrounding() {
        val state = ConnectionNoticeState()
        state.update(ConnectionQuality.Weak)
        state.update(ConnectionQuality.Online)
        state.setForeground(false)
        state.setForeground(true)
        assertNull(state.notice)
    }
}
