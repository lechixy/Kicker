package com.lechixy.kick.service.player

import androidx.media3.common.C
import org.junit.Assert.*
import org.junit.Test

class BroadcastPositionTest {
    @Test fun slidingWindowsKeepTheSameAbsoluteWatchedPosition() {
        val start = 1_800_000_000_000L
        assertEquals(3_610_000L, broadcastPositionMs(start + 3_600_000, 10_000, start))
        assertEquals(3_610_000L, broadcastPositionMs(start + 3_605_000, 5_000, start))
    }

    @Test fun invalidTimesAreNotSavedAsFalseResumePositions() {
        assertNull(broadcastPositionMs(C.TIME_UNSET, 10_000, 100_000))
        assertNull(broadcastPositionMs(100_000, 10_000, 0))
        assertNull(broadcastPositionMs(100_000, -1, 100_000))
        assertEquals(0L, broadcastPositionMs(90_000, 1_000, 100_000))
    }
}
