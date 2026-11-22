package com.lechixy.kick.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStreamRecoveryTest {
    @Test
    fun bufferingHasTimeToRecoverOnItsOwn() {
        val recovery = LiveStreamRecovery()
        recovery.onInterrupted(1_000)
        assertFalse(recovery.shouldRetry(16_000, buffering = true))
        assertFalse(recovery.shouldCheckStatus(16_000))
        recovery.reset()

        recovery.onInterrupted(17_000)
        assertFalse(recovery.shouldCheckStatus(34_000))
        assertTrue(recovery.shouldCheckStatus(35_000))
        assertTrue(recovery.shouldRetry(35_000, buffering = true))
    }

    @Test
    fun terminalPlaylistIsRetriedBeforeStatusIsChecked() {
        val recovery = LiveStreamRecovery()
        recovery.onInterrupted(1_000)
        assertTrue(recovery.shouldRetry(4_000, buffering = false))
        recovery.onRetry(4_000)
        assertFalse(recovery.shouldRetry(5_000, buffering = false))
        assertFalse(recovery.shouldCheckStatus(5_000))
    }

    @Test
    fun declaringTheStreamEndedRequiresTwoConsecutiveSuccessfulChecks() {
        val recovery = LiveStreamRecovery()
        assertFalse(recovery.onChannelStatus(false))
        assertFalse(recovery.onChannelStatus(null))
        assertFalse(recovery.onChannelStatus(false))
        assertFalse(recovery.onChannelStatus(true))
        assertFalse(recovery.onChannelStatus(false))
        recovery.reset()
        assertFalse(recovery.onChannelStatus(false))
        assertTrue(recovery.onChannelStatus(false))
    }
}
