package com.lechixy.kick.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientFrameCadenceTest {
    @Test
    fun newVideoFramesDriveTheEffectAtOrBelowTheSelectedRate() {
        for (videoFps in listOf(24, 30, 60)) {
            for (ambientFps in listOf(30, 60)) {
                val cadence = AmbientFrameCadence()
                val updates = (0 until 120).count { displayFrame ->
                    val latestVideoFrame = displayFrame * videoFps / 60 + 1L
                    cadence.accept(latestVideoFrame, displayFrame * 1_000_000_000L / 60, ambientFps)
                }
                assertEquals("video=$videoFps ambient=$ambientFps", minOf(videoFps, ambientFps) * 2, updates)
            }
        }
    }

    @Test
    fun switchingLimitsUsesNewFramesAndARecreatedSurfaceStartsAFreshCadence() {
        val cadence = AmbientFrameCadence()
        assertFalse(cadence.accept(0L, 0L, 30))
        assertTrue(cadence.accept(100L, 0L, 30))
        assertFalse(cadence.accept(100L, 17_000_000L, 60))
        assertTrue(cadence.accept(101L, 17_000_000L, 60))
        assertFalse(cadence.accept(102L, 25_000_000L, 60))
        assertTrue(cadence.accept(103L, 34_000_000L, 60))
        cadence.reset()
        assertTrue(cadence.accept(100L, 35_000_000L, 60))
    }
}
