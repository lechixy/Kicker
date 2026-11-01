package com.lechixy.kick.service.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LivePlaybackSpeedControl

/** Keep a playable reserve when IVS prefetch delivery briefly stops between segments. */
@UnstableApi
internal class BufferAwareLiveSpeedControl(
    private val delegate: LivePlaybackSpeedControl
) : LivePlaybackSpeedControl by delegate {
    private var minSpeed = 0.95f

    override fun setLiveConfiguration(liveConfiguration: MediaItem.LiveConfiguration) {
        minSpeed = liveConfiguration.minPlaybackSpeed.takeIf { it != C.RATE_UNSET } ?: 0.95f
        delegate.setLiveConfiguration(liveConfiguration)
    }

    override fun getAdjustedPlaybackSpeed(liveOffsetUs: Long, bufferedDurationUs: Long): Float {
        val requested = delegate.getAdjustedPlaybackSpeed(liveOffsetUs, bufferedDurationUs)
        if (delegate.targetLiveOffsetUs == C.TIME_UNSET) return requested
        return bufferSafeLiveSpeed(requested, bufferedDurationUs / 1000, minSpeed)
    }
}

internal fun bufferSafeLiveSpeed(requested: Float, bufferedMs: Long, minSpeed: Float): Float {
    val ceiling = when {
        bufferedMs < 2_000L -> 0.95f + 0.05f * bufferedMs.coerceAtLeast(0L) / 2_000f
        bufferedMs < 3_000L -> 1f
        else -> 1f + 0.05f * ((bufferedMs - 3_000L) / 1_000f).coerceAtMost(2f)
    }
    return minOf(requested, ceiling.coerceAtLeast(minSpeed))
}
