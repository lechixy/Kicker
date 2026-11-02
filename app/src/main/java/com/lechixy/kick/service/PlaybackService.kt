package com.lechixy.kick.service

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.ParsingLoadable
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import com.lechixy.kick.BuildConfig
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.service.player.DiagnosticLiveSpeedControl
import com.lechixy.kick.service.player.BufferAwareLiveSpeedControl
import com.lechixy.kick.service.player.LiveResumeListener
import com.lechixy.kick.service.player.PlaybackDiagnosticTracker
import com.lechixy.kick.service.player.PlaybackProgressTracker
import com.lechixy.kick.service.player.SleepTimer
import com.lechixy.kick.service.player.distanceBehindLiveTargetMs
import com.lechixy.kick.service.player.transformKickLivePlaylist

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var lowLatencyEnabled: Boolean = SettingsKeys.LOW_LATENCY.defaultValue

    private var mediaSession: MediaSession? = null
    private var progressTracker: PlaybackProgressTracker? = null
    @OptIn(UnstableApi::class)
    private var diagnosticTracker: PlaybackDiagnosticTracker? = null
    private val viewerTracking = com.lechixy.kick.data.player.remote.ViewerTrackingClient(
        // Keep metadata decoding and websocket work off the player/UI thread.
        // A serial dispatcher also keeps the tracking heartbeat state confined.
        CoroutineScope(serviceScope.coroutineContext + Dispatchers.Default.limitedParallelism(1))
    )

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        serviceScope.launch(Dispatchers.Main.immediate) {
            com.lechixy.kick.data.account.AccountGraph.auth.session.map { it.generation }
                .distinctUntilChanged().collect { viewerTracking.refreshAuthentication() }
        }
        serviceScope.launch {
            settingsRepository.get(SettingsKeys.LOW_LATENCY).collect { enabled ->
                lowLatencyEnabled = enabled
            }
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf("Origin" to "https://kick.com", "Referer" to "https://kick.com/")
            )

        // Keep recovery below one typical 2s segment; shrinking this further risks stalls.
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val loadControl = DefaultLoadControl.Builder()
            .setAllocator(allocator)
            .setBufferDurationsMs(
                /* minBufferMs = */ 2500,
                /* maxBufferMs = */ 10000,
                /* bufferForPlaybackMs = */ 600,
                /* bufferForPlaybackAfterRebufferMs = */ 1200
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val liveSpeedControl = DiagnosticLiveSpeedControl(BufferAwareLiveSpeedControl(DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(0.95f)
            .setFallbackMaxPlaybackSpeed(1.10f)
            // Back off after a real stall; Media3 gradually reduces this target again
            // when delivery improves, so this is not a permanent latency penalty.
            .setTargetLiveOffsetIncrementOnRebufferMs(500L)
            .build()))

        val hlsParserFactory = KickHlsPlaylistParserFactory {
            lowLatencyEnabled
        }

        val hlsMediaSourceFactory = HlsMediaSource.Factory(dataSourceFactory)
            .setPlaylistParserFactory(hlsParserFactory)
            .setAllowChunklessPreparation(true)

        val defaultMediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val mediaSourceFactory = object : MediaSource.Factory {
            override fun setDrmSessionManagerProvider(
                drmSessionManagerProvider: DrmSessionManagerProvider
            ): MediaSource.Factory {
                hlsMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                defaultMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                loadErrorHandlingPolicy: LoadErrorHandlingPolicy
            ): MediaSource.Factory {
                hlsMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                return this
            }

            override fun getSupportedTypes(): IntArray = defaultMediaSourceFactory.supportedTypes

            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val path = mediaItem.localConfiguration?.uri?.path.orEmpty()
                return if (path.endsWith(".mp4", ignoreCase = true)) {
                    defaultMediaSourceFactory.createMediaSource(mediaItem)
                } else {
                    hlsMediaSourceFactory.createMediaSource(mediaItem)
                }
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(liveSpeedControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(LiveResumeListener(player))
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (BuildConfig.DEBUG && player.isCurrentMediaItemLive &&
                    events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)
                ) {
                    val timeline = player.currentTimeline
                    if (player.currentMediaItemIndex in 0 until timeline.windowCount) {
                        val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
                        // No signed stream URLs, media metadata, or account details in diagnostics.
                        Log.d("KickLive", "state=${player.playbackState} play=${player.playWhenReady} " +
                            "positionMs=${player.currentPosition} defaultMs=${window.defaultPositionMs} " +
                            "behindTargetMs=${player.distanceBehindLiveTargetMs()} " +
                            "liveOffsetMs=${player.currentLiveOffset} targetOffsetMs=${window.liveConfiguration?.targetOffsetMs} " +
                            "adaptiveTargetUs=${liveSpeedControl.targetLiveOffsetUs} " +
                            "bufferMs=${player.totalBufferedDuration} requestedSpeed=${player.playbackParameters.speed}")
                    }
                }
                val extras = player.currentMediaItem?.mediaMetadata?.extras
                val channelId = extras?.getLong(EXTRA_VIEWER_CHANNEL_ID) ?: 0L
                val livestreamId = extras?.getLong(EXTRA_VIEWER_LIVESTREAM_ID) ?: 0L
                val watching = player.playWhenReady && player.playerError == null &&
                    player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) &&
                    extras?.getBoolean(EXTRA_IS_LIVE) == true && channelId > 0 && livestreamId > 0
                viewerTracking.setSession(if (watching) com.lechixy.kick.data.player.remote.ViewerSession(
                    channelId, livestreamId, extras.getString(EXTRA_VIEWER_VOD_ID), extras.getString(EXTRA_VIEWER_CHANNEL_SLUG)
                ) else null)
                if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    com.lechixy.kick.data.common.remote.NetworkMonitor.reportPlaybackBuffering(
                        player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
                    )
                }
            }
        })
        diagnosticTracker = PlaybackDiagnosticTracker(player, liveSpeedControl, serviceScope, allocator)
        SleepTimer.attach(this, player, serviceScope)
        progressTracker = PlaybackProgressTracker(player)
        mediaSession = MediaSession.Builder(this, LiveAwarePlayer(player)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    @OptIn(UnstableApi::class)
    override fun onDestroy() {
        SleepTimer.detach(this)
        viewerTracking.close()
        com.lechixy.kick.data.common.remote.NetworkMonitor.reportPlaybackBuffering(false)
        diagnosticTracker?.close()
        diagnosticTracker = null
        progressTracker?.close()
        progressTracker = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()   // Media3: tüm player'ları durdurup servisi kapatır
    }
}

/**
 * Kick (Amazon IVS) canlı yayınlarında düşük gecikme (#EXT-X-PREFETCH / LHLS) etiketlerini
 * ExoPlayer'ın anlayacağı #EXTINF segmentine dönüştürür. Yenileme süresi listedeki
 * en uzun segmente göre belirlenir; hassas başlangıç hedefin segment başına yuvarlanmasını önler.
 */
@UnstableApi
private class KickHlsPlaylistParserFactory(
    private val isLowLatencyEnabled: () -> Boolean
) : HlsPlaylistParserFactory {

    private val delegate = DefaultHlsPlaylistParserFactory()

    override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> =
        KickHlsPlaylistParser(delegate.createPlaylistParser(), isLowLatencyEnabled)

    override fun createPlaylistParser(
        multivariantPlaylist: HlsMultivariantPlaylist,
        previousMediaPlaylist: HlsMediaPlaylist?
    ): ParsingLoadable.Parser<HlsPlaylist> =
        KickHlsPlaylistParser(
            delegate.createPlaylistParser(multivariantPlaylist, previousMediaPlaylist),
            isLowLatencyEnabled
        )
}

@UnstableApi
private class KickHlsPlaylistParser(
    private val delegate: ParsingLoadable.Parser<HlsPlaylist>,
    private val isLowLatencyEnabled: () -> Boolean
) : ParsingLoadable.Parser<HlsPlaylist> {

    override fun parse(uri: Uri, inputStream: InputStream): HlsPlaylist {
        val rawBytes = inputStream.readBytes()
        val text = rawBytes.toString(Charsets.UTF_8)

        val transformedText = transformKickLivePlaylist(text, isLowLatencyEnabled())
        if (BuildConfig.DEBUG && text.contains("#EXT-X-PREFETCH:")) {
            val durations = text.lineSequence().filter { it.startsWith("#EXTINF:") }
                .mapNotNull { it.substringAfter(':').substringBefore(',').toDoubleOrNull() }.toList()
            val target = transformedText.lineSequence().firstOrNull { it.startsWith("#EXT-X-TARGETDURATION:") }
            Log.d("KickPlaylist", "segments=${durations.size} min=${durations.minOrNull()} max=${durations.maxOrNull()} last=${durations.lastOrNull()} $target")
        }
        return delegate.parse(
            uri,
            ByteArrayInputStream(transformedText.toByteArray(Charsets.UTF_8))
        )
    }
}

const val EXTRA_RECORDING_ID = "kick_recording_id"
const val EXTRA_IS_LIVE = "kick_is_live"
const val EXTRA_START_TIME_MS = "kick_start_time_ms"
const val EXTRA_END_TIME_MS = "kick_end_time_ms"
const val EXTRA_LOW_LATENCY = "kick_low_latency"
const val EXTRA_VIEWER_CHANNEL_ID = "kick_viewer_channel_id"
const val EXTRA_VIEWER_LIVESTREAM_ID = "kick_viewer_livestream_id"
const val EXTRA_VIEWER_VOD_ID = "kick_viewer_vod_id"
const val EXTRA_VIEWER_CHANNEL_SLUG = "kick_viewer_channel_slug"

@OptIn(UnstableApi::class)
fun buildPlaybackMediaItem(
    playbackUrl: String,
    mediaId: String,
    isLive: Boolean,
    title: String,
    channelName: String,
    viewersText: String?,
    artwork: Uri,
    startTimeMs: Long = 0L,
    vodDurationMs: Long? = null,
    isLowLatency: Boolean = true,
    recordingId: String? = null,
    viewerSession: com.lechixy.kick.data.player.remote.ViewerSession? = null
): MediaItem {
    val subtitle = buildString {
        append(if (isLive) "🔴 Canlı" else "Kayıt")
        if (!viewersText.isNullOrBlank()) append(" • ").append(viewersText)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(channelName)
        .setAlbumArtist(channelName)
        .setAlbumTitle("Kick")
        .setSubtitle(subtitle)
        .setDescription(subtitle)
        .setArtworkUri(artwork)
        .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .apply {
            if (!isLive && vodDurationMs != null && vodDurationMs > 0) setDurationMs(vodDurationMs)
        }
        .setExtras(Bundle().apply {
            putBoolean(EXTRA_IS_LIVE, isLive)
            if (isLive && viewerSession != null) {
                putLong(EXTRA_VIEWER_CHANNEL_ID, viewerSession.channelId)
                putLong(EXTRA_VIEWER_LIVESTREAM_ID, viewerSession.livestreamId)
                putString(EXTRA_VIEWER_VOD_ID, viewerSession.vodId)
                putString(EXTRA_VIEWER_CHANNEL_SLUG, viewerSession.channelSlug)
            }
            if (!isLive && recordingId != null) putString(EXTRA_RECORDING_ID, recordingId)
            putLong(EXTRA_START_TIME_MS, startTimeMs)
            if (!isLive && vodDurationMs != null && vodDurationMs > 0) {
                putLong(EXTRA_END_TIME_MS, if (startTimeMs > 0) startTimeMs + vodDurationMs else vodDurationMs)
            }
            putBoolean(EXTRA_LOW_LATENCY, isLowLatency)
        })
        .build()

    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUrl.toUri())
        .setMediaMetadata(metadata)

    if (isLive) {
        // Target offset doğrudan manifest içine enjekte edilen #EXT-X-START:TIME-OFFSET
        // üzerinden yayıncının segment süresine (2sn veya 4.167sn) göre dinamik hesaplanır.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setMinPlaybackSpeed(0.95f)
            .setMaxPlaybackSpeed(if (isLowLatency) 1.10f else 1.03f)
            .build()
        builder.setLiveConfiguration(liveConfig)
    }

    return builder.build()
}

@UnstableApi
class LiveAwarePlayer(player: Player) : ForwardingPlayer(player)

