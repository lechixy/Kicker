package com.lechixy.kick.service.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LivePlaybackSpeedControl
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class PlaybackDiagnosticsTest {
    private class Control : LivePlaybackSpeedControl {
        var adjustments = 0
        var rebufferCalls = 0
        var inputs: Pair<Long, Long>? = null
        var overrideUs: Long? = null
        var configuration: MediaItem.LiveConfiguration? = null
        override fun setLiveConfiguration(value: MediaItem.LiveConfiguration) { configuration = value }
        override fun setTargetLiveOffsetOverrideUs(value: Long) { overrideUs = value }
        override fun notifyRebuffer() { rebufferCalls++ }
        override fun getAdjustedPlaybackSpeed(offset: Long, buffer: Long): Float {
            adjustments++
            inputs = offset to buffer
            return 1.075f
        }
        override fun getTargetLiveOffsetUs() = 2500000L
    }

    @Test fun diagnosticsObserveAutomaticSpeedWithoutRequestingExtraAdjustments() {
        val control = Control()
        val observed = DiagnosticLiveSpeedControl(control)
        assertEquals(1.075f, observed.getAdjustedPlaybackSpeed(4000000, 1500000), 0f)
        assertEquals(4000000L to 1500000L, control.inputs)
        repeat(5) {
            assertEquals(1.075f, observed.adjustedSpeed, 0f)
            assertEquals(2500000L, observed.targetUs)
        }
        assertEquals(1, control.adjustments)
    }

    @Test fun diagnosticsPreserveControllerCommandsAndResetOnlyDiagnosticCounters() {
        val control = Control()
        val observed = DiagnosticLiveSpeedControl(control)
        val config = MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(2500).build()
        observed.setLiveConfiguration(config)
        observed.setTargetLiveOffsetOverrideUs(3000000)
        observed.notifyRebuffer()
        observed.getAdjustedPlaybackSpeed(4000000, 1500000)
        assertSame(config, control.configuration)
        assertEquals(3000000L, control.overrideUs)
        assertEquals(1, control.rebufferCalls)
        assertEquals(1, observed.rebuffers)
        observed.resetDiagnostics()
        assertEquals(0, observed.rebuffers)
        assertEquals(1f, observed.adjustedSpeed, 0f)
        assertEquals(C.TIME_UNSET, observed.targetUs)
        assertEquals(1, control.rebufferCalls)
        assertSame(config, control.configuration)
    }
}
