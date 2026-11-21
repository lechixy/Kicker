package com.lechixy.kick.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.chunk.MediaChunk
import androidx.media3.exoplayer.source.chunk.MediaChunkIterator
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.upstream.BandwidthMeter
import com.google.common.collect.ImmutableList

internal data class StreamVideoQuality(
    val preferredHeight: Int = -1,
    val compactHeightLimit: Int? = null,
) {
    companion object {
        // The UI and PlaybackService share a process. Selections read this on the playback thread.
        @Volatile
        var current = StreamVideoQuality()
    }
}

@OptIn(UnstableApi::class)
internal class StreamTrackSelectionFactory(
    private val videoQuality: () -> StreamVideoQuality = { StreamVideoQuality.current },
) : AdaptiveTrackSelection.Factory() {
    override fun createAdaptiveTrackSelection(
        group: TrackGroup,
        tracks: IntArray,
        type: Int,
        bandwidthMeter: BandwidthMeter,
        adaptationCheckpoints: ImmutableList<AdaptiveTrackSelection.AdaptationCheckpoint>,
    ): AdaptiveTrackSelection = if (group.type == C.TRACK_TYPE_VIDEO) {
        StreamVideoTrackSelection(group, tracks, type, bandwidthMeter, adaptationCheckpoints, videoQuality)
    } else {
        super.createAdaptiveTrackSelection(group, tracks, type, bandwidthMeter, adaptationCheckpoints)
    }
}

@OptIn(UnstableApi::class)
private class StreamVideoTrackSelection(
    group: TrackGroup,
    tracks: IntArray,
    type: Int,
    bandwidthMeter: BandwidthMeter,
    adaptationCheckpoints: List<AdaptiveTrackSelection.AdaptationCheckpoint>,
    private val videoQuality: () -> StreamVideoQuality,
) : AdaptiveTrackSelection(
    group, tracks, type, bandwidthMeter,
    /* minDurationForQualityIncreaseMs = */ 2_500,
    DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS.toLong(),
    DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS.toLong(),
    DEFAULT_MAX_WIDTH_TO_DISCARD, DEFAULT_MAX_HEIGHT_TO_DISCARD, DEFAULT_BANDWIDTH_FRACTION,
    DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE, adaptationCheckpoints, Clock.DEFAULT,
) {
    private var compactLimit: Int? = null
    private var targetHeight = Format.NO_VALUE
    private var fallbackFormat: Format? = null

    override fun updateSelectedTrack(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        availableDurationUs: Long,
        queue: List<MediaChunk>,
        mediaChunkIterators: Array<out MediaChunkIterator>,
    ) {
        val quality = videoQuality()
        compactLimit = quality.compactHeightLimit
        targetHeight = Format.NO_VALUE
        fallbackFormat = null
        if (compactLimit != null || quality.preferredHeight > 0) {
            val nowMs = Clock.DEFAULT.elapsedRealtime()
            var lowestResolution: Format? = null
            for (index in 0 until length()) {
                if (isTrackExcluded(index, nowMs)) continue
                val format = getFormat(index)
                if (format.height <= 0) continue
                if (lowestResolution == null || format.height <= lowestResolution.height) {
                    lowestResolution = format
                }
                if (fitsCompactLimit(format)) {
                    // Formats are in descending bitrate order. Keep the cheapest eligible fallback.
                    fallbackFormat = format
                }
                if (format.height <= quality.preferredHeight && format.height > targetHeight) {
                    targetHeight = format.height
                }
            }
            if (compactLimit != null) {
                fallbackFormat = fallbackFormat ?: lowestResolution
            } else if (targetHeight == Format.NO_VALUE) {
                targetHeight = lowestResolution?.height ?: Format.NO_VALUE
            }
        }
        super.updateSelectedTrack(
            playbackPositionUs, bufferedDurationUs, availableDurationUs, queue, mediaChunkIterators
        )
    }

    override fun canSelectFormat(format: Format, trackBitrate: Int, effectiveBitrate: Long): Boolean {
        if (compactLimit != null && fallbackFormat != null) {
            return format === fallbackFormat ||
                (fitsCompactLimit(format) && super.canSelectFormat(format, trackBitrate, effectiveBitrate))
        }
        if (targetHeight > 0) return format.height == targetHeight
        return super.canSelectFormat(format, trackBitrate, effectiveBitrate)
    }

    private fun fitsCompactLimit(format: Format): Boolean {
        val maxHeight = compactLimit ?: return false
        val maxWidth = (maxHeight * 16 + 8) / 9
        return format.height in 1..maxHeight && (format.width == Format.NO_VALUE || format.width <= maxWidth)
    }

    // A switch only affects future loads. Play every segment already in the HLS queue.
    override fun evaluateQueueSize(playbackPositionUs: Long, queue: List<MediaChunk>): Int = queue.size
}
