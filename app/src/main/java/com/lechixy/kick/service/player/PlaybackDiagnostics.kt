package com.lechixy.kick.service.player

import android.os.SystemClock
import android.util.Log
import com.lechixy.kick.BuildConfig
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LivePlaybackSpeedControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.Allocator
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration.Companion.milliseconds

internal const val PLAYBACK_CHART_SAMPLE_COUNT = 30

data class PlaybackChartSample(
    val bandwidthBps: Long?,
    val networkBytesPerSecond: Long,
    val bufferedMs: Long
)

/** Process-local values only; never includes stream URLs or account data. */
data class PlaybackDiagnosticState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val live: Boolean = false,
    val speed: Float = 1f,
    val requestedSpeed: Float = 1f,
    val liveOffsetMs: Long? = null,
    val targetOffsetMs: Long? = null,
    val behindTargetMs: Long? = null,
    val bufferedMs: Long = 0,
    val rebuffers: Int = 0,
    val bufferingMs: Long = 0,
    val droppedFrames: Int = 0,
    val skippedFrames: Int = 0,
    val renderedFrames: Int = 0,
    val lastDropAgeMs: Long? = null,
    val lastDropCount: Int = 0,
    val processingOffsetMs: Double? = null,
    val audioUnderruns: Int = 0,
    val bandwidthBps: Long? = null,
    val videoBitrate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val errorCode: String? = null,
    val sessionId: String? = null,
    val bestVideoSize: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val color: String? = null,
    val networkBytesPerSecond: Long = 0,
    val positionMs: Long = 0,
    val bufferedPositionMs: Long = 0,
    val sampledAtMs: Long = 0,
    val chartSamples: List<PlaybackChartSample> = emptyList()
)

object PlaybackDiagnostics {
    internal val mutableState = MutableStateFlow(PlaybackDiagnosticState())
    val state = mutableState.asStateFlow()
}

@UnstableApi
internal class PlaybackNetworkCounter : TransferListener {
    val bytes = AtomicLong()

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
        if (isNetwork) bytes.addAndGet(bytesTransferred.toLong())
    }
}

/** Observe the actual automatic speed decisions on the playback thread without changing them. */
@UnstableApi
internal class DiagnosticLiveSpeedControl(private val delegate: LivePlaybackSpeedControl) : LivePlaybackSpeedControl by delegate {
    @Volatile var adjustedSpeed = 1f
        private set
    @Volatile var rebuffers = 0
        private set
    @Volatile var targetUs = C.TIME_UNSET
        private set

    override fun getAdjustedPlaybackSpeed(liveOffsetUs: Long, bufferedDurationUs: Long): Float {
        return delegate.getAdjustedPlaybackSpeed(liveOffsetUs, bufferedDurationUs).also {
            adjustedSpeed = it
            targetUs = delegate.targetLiveOffsetUs
        }
    }

    override fun notifyRebuffer() {
        rebuffers++
        delegate.notifyRebuffer()
    }

    fun resetDiagnostics() {
        adjustedSpeed = 1f
        targetUs = C.TIME_UNSET
        rebuffers = 0
    }
}

@UnstableApi
internal class PlaybackDiagnosticTracker(
    private val player: ExoPlayer,
    private val speedControl: DiagnosticLiveSpeedControl,
    scope: CoroutineScope,
    allocator: Allocator,
    private val network: PlaybackNetworkCounter
) : AnalyticsListener, Player.Listener {
    private var bufferingStarted: Long? = null
    private var bufferingTotal = 0L
    private var lastDropAt: Long? = null
    private var lastDropCount = 0
    private var processingOffsetMs: Double? = null
    private var audioUnderruns = 0
    private var bandwidthBps: Long? = null
    private var sessionId: String? = null
    private var bestVideoSize: String? = null
    private var lastNetworkSampleAt = SystemClock.elapsedRealtime()
    private var lastNetworkBytes = network.bytes.get()
    private var networkBytesPerSecond = 0L
    private var chartSamples = emptyList<PlaybackChartSample>()
    private val job: Job
    private val memoryMonitor = PlaybackMemoryMonitor(allocator)

    init {
        player.addAnalyticsListener(this)
        player.addListener(this)
        job = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                publish()
                delay(500.milliseconds)
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        sessionId = mediaItem?.let { UUID.randomUUID().toString() }
        bestVideoSize = null
        lastNetworkSampleAt = SystemClock.elapsedRealtime()
        lastNetworkBytes = network.bytes.get()
        networkBytesPerSecond = 0L
        chartSamples = emptyList()
        bufferingStarted = null
        bufferingTotal = 0
        lastDropAt = null
        lastDropCount = 0
        processingOffsetMs = null
        audioUnderruns = 0
        bandwidthBps = null
        speedControl.resetDiagnostics()
        publish()
    }

    override fun onEvents(player: Player, events: Player.Events) { publish() }

    override fun onTracksChanged(tracks: Tracks) {
        bestVideoSize = tracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .flatMap { group ->
                (0 until group.length).asSequence().filter { group.isTrackSupported(it) }
                    .map { group.getTrackFormat(it) }
            }
            .filter { it.width > 0 && it.height > 0 }
            .maxWithOrNull(compareBy<Format> { it.width.toLong() * it.height }.thenBy { it.frameRate })
            ?.let { videoSizeLabel(it.width, it.height, it.frameRate) }
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        lastDropAt = SystemClock.elapsedRealtime()
        lastDropCount = droppedFrames
    }

    override fun onVideoFrameProcessingOffset(eventTime: AnalyticsListener.EventTime, totalProcessingOffsetUs: Long, frameCount: Int) {
        processingOffsetMs = if (frameCount > 0) totalProcessingOffsetUs / (frameCount * 1000.0) else null
    }

    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        audioUnderruns++
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) {
        bandwidthBps = bitrateEstimate.takeIf { it > 0 }
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData) {
        if (BuildConfig.DEBUG) Log.d("KickLoad", "type=${mediaLoadData.dataType} durationMs=${loadEventInfo.loadDurationMs} bytes=${loadEventInfo.bytesLoaded} mediaStartMs=${mediaLoadData.mediaStartTimeMs} mediaEndMs=${mediaLoadData.mediaEndTimeMs}")
    }

    override fun onLoadError(eventTime: AnalyticsListener.EventTime, loadEventInfo: LoadEventInfo, mediaLoadData: MediaLoadData, error: IOException, wasCanceled: Boolean) {
        // Exception messages and URLs may contain signed credentials.
        if (BuildConfig.DEBUG) Log.d("KickLoad", "error=${error.javaClass.simpleName} type=${mediaLoadData.dataType} durationMs=${loadEventInfo.loadDurationMs} bytes=${loadEventInfo.bytesLoaded} canceled=$wasCanceled")
    }

    private fun publish() {
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastNetworkSampleAt
        val sampleDue = elapsed >= 1_000
        if (sampleDue) {
            val bytes = network.bytes.get()
            networkBytesPerSecond = (bytes - lastNetworkBytes) * 1_000 / elapsed
            lastNetworkBytes = bytes
            lastNetworkSampleAt = now
        }
        val buffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
        if (buffering && bufferingStarted == null) bufferingStarted = now
        if (!buffering) bufferingStarted?.let { bufferingTotal += now - it; bufferingStarted = null }
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val format = player.videoFormat
        val audio = player.audioFormat
        val active = player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE
        if (active) memoryMonitor.sample(if (player.isCurrentMediaItemLive) "live" else "recording", now)
        val bufferedMs = player.totalBufferedDuration
        if (sampleDue && active) {
            val sample = PlaybackChartSample(bandwidthBps, networkBytesPerSecond, bufferedMs)
            chartSamples = (chartSamples + sample).takeLast(PLAYBACK_CHART_SAMPLE_COUNT)
        }
        val live = player.isCurrentMediaItemLive
        val window = if (player.currentMediaItemIndex in 0 until player.currentTimeline.windowCount)
            player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()) else null
        val requestedSpeed = player.playbackParameters.speed
        val targetMs = speedControl.targetUs.takeIf { it != C.TIME_UNSET }?.div(1000)
            ?: window?.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET }
        PlaybackDiagnostics.mutableState.value = PlaybackDiagnosticState(
            active = active,
            playing = player.isPlaying, buffering = buffering, live = live,
            speed = if (live && requestedSpeed == 1f) speedControl.adjustedSpeed else requestedSpeed,
            requestedSpeed = requestedSpeed,
            liveOffsetMs = if (live) player.currentLiveOffset.takeIf { it != C.TIME_UNSET } else null,
            targetOffsetMs = if (live) targetMs else null,
            behindTargetMs = player.distanceBehindLiveTargetMs().takeIf { it != C.TIME_UNSET },
            bufferedMs = bufferedMs,
            rebuffers = speedControl.rebuffers,
            bufferingMs = bufferingTotal + (bufferingStarted?.let { now - it } ?: 0),
            droppedFrames = counters?.droppedBufferCount ?: 0,
            skippedFrames = (counters?.skippedInputBufferCount ?: 0) + (counters?.skippedOutputBufferCount ?: 0),
            renderedFrames = counters?.renderedOutputBufferCount ?: 0,
            lastDropAgeMs = lastDropAt?.let { now - it }, lastDropCount = lastDropCount,
            processingOffsetMs = processingOffsetMs, audioUnderruns = audioUnderruns,
            bandwidthBps = bandwidthBps, videoBitrate = format?.bitrate?.takeIf { it > 0 },
            width = format?.width?.takeIf { it > 0 }, height = format?.height?.takeIf { it > 0 },
            frameRate = format?.frameRate?.takeIf { it > 0 }, errorCode = player.playerError?.errorCodeName,
            sessionId = sessionId, bestVideoSize = bestVideoSize,
            videoCodec = format?.let { it.codecs ?: it.sampleMimeType },
            audioCodec = audio?.let { it.codecs ?: it.sampleMimeType },
            color = format?.colorInfo?.statsLabel(),
            networkBytesPerSecond = networkBytesPerSecond,
            positionMs = player.currentPosition,
            bufferedPositionMs = player.bufferedPosition,
            sampledAtMs = if (active) System.currentTimeMillis() else 0,
            chartSamples = chartSamples
        )
        if (BuildConfig.DEBUG && live) Log.d("KickStats", "t=$now state=${player.playbackState} play=${player.playWhenReady} speed=${speedControl.adjustedSpeed} offsetMs=${player.currentLiveOffset} targetMs=$targetMs bufferMs=${player.totalBufferedDuration} rebuffers=${speedControl.rebuffers} bufferingMs=${PlaybackDiagnostics.state.value.bufferingMs} bandwidthBps=$bandwidthBps bitrate=${format?.bitrate} dropped=${counters?.droppedBufferCount} audioUnderruns=$audioUnderruns")
    }

    fun close() {
        job.cancel()
        player.removeListener(this)
        player.removeAnalyticsListener(this)
        PlaybackDiagnostics.mutableState.value = PlaybackDiagnosticState()
    }
}

internal fun videoSizeLabel(width: Int?, height: Int?, frameRate: Float?): String? {
    if (width == null || height == null) return null
    val fps = frameRate?.takeIf { it > 0 }?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
    return "${width}×$height" + (fps?.let { "@$it" } ?: "")
}

@UnstableApi
private fun ColorInfo.statsLabel(): String {
    val space = when (colorSpace) {
        C.COLOR_SPACE_BT709 -> "BT.709"
        C.COLOR_SPACE_BT601 -> "BT.601"
        C.COLOR_SPACE_BT2020 -> "BT.2020"
        else -> "—"
    }
    val transfer = when (colorTransfer) {
        C.COLOR_TRANSFER_SDR -> "SDR"
        C.COLOR_TRANSFER_ST2084 -> "PQ"
        C.COLOR_TRANSFER_HLG -> "HLG"
        C.COLOR_TRANSFER_SRGB -> "sRGB"
        C.COLOR_TRANSFER_LINEAR -> "Linear"
        else -> "—"
    }
    return "$space / $transfer"
}
