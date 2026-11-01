package com.lechixy.kick.util.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal interface MediaVolumeDevice {
    val fixed: Boolean
    val minimum: Int
    val maximum: Int
    var volume: Int
}

/** In sync mode only the system attenuates audio; fixed-volume devices retain local control. */
internal class PlayerVolumeController(private val device: MediaVolumeDevice) {
    private var syncRequested by mutableStateOf(false)
    private var localVolume by mutableFloatStateOf(1f)
    private var systemVolume by mutableFloatStateOf(1f)
    private var previousLocalVolume = 1f
    private var previousSystemVolume: Int? = null
    var syncActive by mutableStateOf(false)
        private set
    val volume: Float get() = if (syncActive) systemVolume else localVolume
    val muted: Boolean get() = volume <= 0f
    val playerGain: Float get() = if (syncActive) 1f else localVolume

    fun setSyncEnabled(enabled: Boolean) {
        syncRequested = enabled
        refresh()
    }

    fun refresh() {
        syncActive = syncRequested && !device.fixed && device.maximum > device.minimum
        if (!syncActive) return
        val minimum = device.minimum
        val maximum = device.maximum
        val current = device.volume.coerceIn(minimum, maximum)
        systemVolume = (current - minimum).toFloat() / (maximum - minimum)
        if (current > minimum) previousSystemVolume = current
    }

    fun setVolume(value: Float) {
        if (!value.isFinite()) return
        val bounded = value.coerceIn(0f, 1f)
        if (syncActive) {
            val minimum = device.minimum
            val maximum = device.maximum
            val target = minimum + (bounded * (maximum - minimum)).roundToInt()
            runCatching { device.volume = target }.onFailure { refresh(); return }
            refresh() // Display the device's quantized volume rather than an unsupported fraction.
        } else {
            localVolume = bounded
            if (bounded > 0f) previousLocalVolume = bounded
        }
    }

    fun toggleMute() {
        if (syncActive) {
            refresh()
            if (!muted) setVolume(0f)
            else {
                val minimum = device.minimum
                val maximum = device.maximum
                val restore = previousSystemVolume?.coerceIn(minimum + 1, maximum)
                    ?: (minimum + ((maximum - minimum) * .25f).roundToInt()).coerceIn(minimum + 1, maximum)
                setVolume((restore - minimum).toFloat() / (maximum - minimum))
            }
        } else setVolume(if (muted) previousLocalVolume else 0f)
    }
}
