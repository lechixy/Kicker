package com.lechixy.kick.service.player

import android.os.SystemClock
import android.util.Log
import com.lechixy.kick.BuildConfig
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LivePlaybackSpeedControl
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val errorCode: String? = null
)

object PlaybackDiagnostics {
    internal val mutableState = MutableStateFlow(PlaybackDiagnosticState())
    val state = mutableState.asStateFlow()
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
    scope: CoroutineScope
) : AnalyticsListener, Player.Listener {
    private var bufferingStarted: Long? = null
    private var bufferingTotal = 0L
    private var lastDropAt: Long? = null
    private var lastDropCount = 0
    private var processingOffsetMs: Double? = null
    private var audioUnderruns = 0
    private var bandwidthBps: Long? = null
    private val job: Job

    init {
        player.addAnalyticsListener(this)
        player.addListener(this)
        job = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                publish()
                delay(500)
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
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
        val buffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
        if (buffering && bufferingStarted == null) bufferingStarted = now
        if (!buffering) bufferingStarted?.let { bufferingTotal += now - it; bufferingStarted = null }
        val counters = player.videoDecoderCounters
        counters?.ensureUpdated()
        val format = player.videoFormat
        val live = player.isCurrentMediaItemLive
        val window = if (player.currentMediaItemIndex in 0 until player.currentTimeline.windowCount)
            player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()) else null
        val requestedSpeed = player.playbackParameters.speed
        val targetMs = speedControl.targetUs.takeIf { it != C.TIME_UNSET }?.div(1000)
            ?: window?.liveConfiguration?.targetOffsetMs?.takeIf { it != C.TIME_UNSET }
        PlaybackDiagnostics.mutableState.value = PlaybackDiagnosticState(
            active = player.currentMediaItem != null && player.playbackState != Player.STATE_IDLE,
            playing = player.isPlaying, buffering = buffering, live = live,
            speed = if (live && requestedSpeed == 1f) speedControl.adjustedSpeed else requestedSpeed,
            requestedSpeed = requestedSpeed,
            liveOffsetMs = if (live) player.currentLiveOffset.takeIf { it != C.TIME_UNSET } else null,
            targetOffsetMs = if (live) targetMs else null,
            behindTargetMs = player.distanceBehindLiveTargetMs().takeIf { it != C.TIME_UNSET },
            bufferedMs = player.totalBufferedDuration,
            rebuffers = speedControl.rebuffers,
            bufferingMs = bufferingTotal + (bufferingStarted?.let { now - it } ?: 0),
            droppedFrames = counters?.droppedBufferCount ?: 0,
            skippedFrames = (counters?.skippedInputBufferCount ?: 0) + (counters?.skippedOutputBufferCount ?: 0),
            renderedFrames = counters?.renderedOutputBufferCount ?: 0,
            lastDropAgeMs = lastDropAt?.let { now - it }, lastDropCount = lastDropCount,
            processingOffsetMs = processingOffsetMs, audioUnderruns = audioUnderruns,
            bandwidthBps = bandwidthBps, videoBitrate = format?.bitrate?.takeIf { it > 0 },
            width = format?.width?.takeIf { it > 0 }, height = format?.height?.takeIf { it > 0 },
            frameRate = format?.frameRate?.takeIf { it > 0 }, errorCode = player.playerError?.errorCodeName
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
