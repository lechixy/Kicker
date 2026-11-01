package com.lechixy.kick.util.player

import org.junit.Assert.*
import org.junit.Test

class PlayerVolumeControllerTest {
    private class Device(override val fixed: Boolean = false, override val minimum: Int = 0,
        override val maximum: Int = 20, initial: Int = 8) : MediaVolumeDevice {
        var writes = 0
        override var volume = initial
            set(value) { field = value; writes++ }
    }

    @Test fun enablingAdoptsDeviceWithoutWritingOrDoubleAttenuation() {
        val device = Device()
        val state = PlayerVolumeController(device)
        state.setVolume(.2f)
        state.setSyncEnabled(true)
        assertEquals(.4f, state.volume, .001f)
        assertEquals(1f, state.playerGain, 0f)
        assertEquals(0, device.writes)
    }

    @Test fun synchronizedSliderAndHardwareChangesWorkBothWays() {
        val device = Device()
        val state = PlayerVolumeController(device)
        state.setSyncEnabled(true)
        state.setVolume(.52f)
        assertEquals(10, device.volume)
        assertEquals(.5f, state.volume, .001f)
        device.volume = 3
        state.refresh()
        assertEquals(.15f, state.volume, .001f)
        assertEquals(1f, state.playerGain, 0f)
    }

    @Test fun muteRestoresPreviousDeviceLevelRatherThanMaximum() {
        val device = Device(initial = 7)
        val state = PlayerVolumeController(device)
        state.setSyncEnabled(true)
        state.toggleMute()
        assertTrue(state.muted)
        assertEquals(0, device.volume)
        assertEquals(1f, state.playerGain, 0f)
        state.toggleMute()
        assertEquals(7, device.volume)
        assertFalse(state.muted)
    }

    @Test fun disabledSyncRetainsIndependentPlayerGainAndMuteLevel() {
        val device = Device()
        val state = PlayerVolumeController(device)
        state.setVolume(.3f)
        state.toggleMute()
        assertEquals(0f, state.playerGain, 0f)
        state.toggleMute()
        assertEquals(.3f, state.playerGain, .001f)
        state.setSyncEnabled(true)
        state.setVolume(.7f)
        state.setSyncEnabled(false)
        assertEquals(.3f, state.volume, .001f)
        assertEquals(14, device.volume)
    }

    @Test fun fixedVolumeDevicesUseLocalGainAndNeverAttemptSystemWrites() {
        val device = Device(fixed = true)
        val state = PlayerVolumeController(device)
        state.setSyncEnabled(true)
        state.setVolume(.25f)
        assertFalse(state.syncActive)
        assertEquals(.25f, state.playerGain, .001f)
        assertEquals(0, device.writes)
    }

    @Test fun boundsQuantizationAndInvalidInputAreHandled() {
        val device = Device(minimum = 2, maximum = 12, initial = 7)
        val state = PlayerVolumeController(device)
        state.setSyncEnabled(true)
        assertEquals(.5f, state.volume, .001f)
        state.setVolume(2f)
        assertEquals(12, device.volume)
        state.setVolume(Float.NaN)
        assertEquals(12, device.volume)
        state.setVolume(-1f)
        assertEquals(2, device.volume)
    }
}
