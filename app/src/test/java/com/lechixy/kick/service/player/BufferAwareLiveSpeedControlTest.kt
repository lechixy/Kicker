package com.lechixy.kick.service.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferAwareLiveSpeedControlTest {
    @Test fun depletedBufferBuildsReserveInsteadOfCatchingUp() {
        assertEquals(0.95f, bufferSafeLiveSpeed(1.1f, 0, 0.95f), 0.0001f)
        assertEquals(0.975f, bufferSafeLiveSpeed(1.1f, 1000, 0.95f), 0.0001f)
        assertEquals(1f, bufferSafeLiveSpeed(1.1f, 2500, 0.95f), 0f)
    }

    @Test fun catchupReturnsGraduallyWithEnoughReserve() {
        assertEquals(1f, bufferSafeLiveSpeed(1.1f, 3000, 0.95f), 0f)
        assertEquals(1.05f, bufferSafeLiveSpeed(1.1f, 4000, 0.95f), 0.0001f)
        assertEquals(1.1f, bufferSafeLiveSpeed(1.1f, 5000, 0.95f), 0f)
    }

    @Test fun respectsConfiguredMinimumAndExistingSlowingDecision() {
        assertEquals(1f, bufferSafeLiveSpeed(1.1f, 100, 1f), 0f)
        assertEquals(0.95f, bufferSafeLiveSpeed(0.95f, 5000, 0.95f), 0f)
        for (buffer in 0L..5000L step 50) {
            assertTrue(bufferSafeLiveSpeed(1.03f, buffer, 0.95f) <= 1.03f)
        }
    }
}
