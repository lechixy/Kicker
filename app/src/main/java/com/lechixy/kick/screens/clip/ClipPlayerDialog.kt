package com.lechixy.kick.screens.clip

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.session.MediaSession
import com.lechixy.kick.components.PlayerVideoSurface
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.service.ClipAudioHandoff
import com.lechixy.kick.service.kickPlaybackAudioAttributes
import com.lechixy.kick.service.playbackHttpDataSourceFactory
import com.lechixy.kick.data.player.local.PlaybackUsageChannel
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import com.lechixy.kick.service.player.PlaybackUsageTracker
import com.lechixy.kick.service.player.PlaybackMemoryMonitor
import com.lechixy.kick.service.player.setPlaybackUsageChannel
import com.lechixy.kick.service.player.trackUsage
import androidx.core.net.toUri
import kotlinx.coroutines.delay

@Composable
internal fun ClipPlayerDialog(
    clipId: String,
    onClose: () -> Unit,
    onWatchLivestream: (ClipDetail) -> Unit,
    onWatchFullVideo: (ClipDetail) -> Unit,
) {
    val owner = remember(clipId) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    val model = viewModel(viewModelStoreOwner = owner) { ClipPlayerViewModel(clipId) }
    val state by model.state.collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable(clipId) { mutableStateOf(false) }
    var showChat by rememberSaveable(clipId) { mutableStateOf(true) }
    var videoTexture by remember(clipId) { mutableStateOf<TextureView?>(null) }

    val playback = state.clip?.let { rememberClipPlayback(it) }
    val video = remember(playback) {
        movableContentOf {
            if (playback != null) {
                PlayerVideoSurface(playback.player, playback.state.playing, Modifier.fillMaxSize()) {
                    videoTexture = it
                }
            }
        }
    }
    val chatVisible = showChat && !fullscreen
    LaunchedEffect(model, playback, chatVisible) {
        if (playback != null && chatVisible) {
            snapshotFlow { playback.state.positionMs }.collect(model::updatePosition)
        } else {
            model.pauseChat()
        }
    }

    Dialog(
        onDismissRequest = { if (fullscreen) fullscreen = false else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val floating = !fullscreen && minOf(maxWidth, maxHeight) >= 600.dp
            if (floating) {
                Box(Modifier.matchParentSize().pointerInput(onClose) { detectTapGestures { onClose() } })
            }
            Surface(
                modifier = if (floating) {
                    Modifier.widthIn(max = 1120.dp).heightIn(max = 760.dp).fillMaxWidth(.94f).fillMaxHeight(.90f)
                } else Modifier.fillMaxSize(),
                shape = RoundedCornerShape(if (floating) 20.dp else 0.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                ClipPlayerScreen(
                    uiState = state,
                    playback = playback?.state ?: ClipPlaybackState(),
                    fullscreen = fullscreen,
                    showChat = showChat,
                    onPlayPauseToggle = { playback?.togglePlayback() },
                    onSeekTo = { playback?.player?.seekTo(it) },
                    onMuteToggle = { playback?.toggleMute() },
                    onFullscreenToggle = { fullscreen = !fullscreen },
                    onChatVisibilityToggle = { showChat = !showChat },
                    onCloseClick = onClose,
                    onRetryPlayback = {
                        if (playback == null) model.load()
                        else playback.retry()
                    },
                    onRetryChat = { model.retryChat(playback?.state?.positionMs ?: 0L) },
                    onWatchLivestream = onWatchLivestream,
                    onWatchFullVideo = onWatchFullVideo,
                    video = video,
                    videoTexture = videoTexture,
                )
            }
        }
    }
}

internal data class ClipPlaybackState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val playing: Boolean = false,
    val playRequested: Boolean = false,
    val muted: Boolean = false,
    val buffering: Boolean = true,
    val renderedFrame: Boolean = false,
    val failed: Boolean = false,
)

@OptIn(UnstableApi::class)
private class ClipPlayback(val player: ExoPlayer, private val durationMs: Long, private val memoryMonitor: PlaybackMemoryMonitor) : Player.Listener {
    var state by mutableStateOf(ClipPlaybackState(durationMs = durationMs))
        private set

    override fun onEvents(player: Player, events: Player.Events) = refresh()

    override fun onRenderedFirstFrame() {
        state = state.copy(renderedFrame = true)
    }

    fun refresh() {
        if (player.currentMediaItem != null) memoryMonitor.sample("clip")
        val duration = player.duration.takeIf { it > 0 } ?: durationMs
        state = state.copy(
            positionMs = player.currentPosition.coerceIn(0, duration),
            durationMs = duration,
            playing = player.isPlaying,
            playRequested = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
            muted = player.volume == 0f,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            failed = player.playerError != null,
        )
    }

    fun togglePlayback() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) player.pause()
        else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun toggleMute() { player.volume = if (state.muted) 1f else 0f }

    fun retry() {
        player.prepare()
        player.play()
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun rememberClipPlayback(clip: ClipDetail): ClipPlayback {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val playback = remember(context, clip.id, clip.playbackUrl) {
        val source = playbackHttpDataSourceFactory().trackUsage(PlaybackUsageChannel(clip.channel.slug, clip.channel.username))
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(source))
            .setLoadControl(DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setBufferDurationsMsForStreaming(2500, 10000, 600, 1200)
                .setTargetBufferBytes(12 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholdsForStreaming(false)
                .build())
            .setHandleAudioBecomingNoisy(true)
            .build()
        ClipPlayback(player, clip.durationMs, PlaybackMemoryMonitor(allocator))
    }
    DisposableEffect(playback, lifecycle) {
        val player = playback.player
        val usage = PlaybackUsageTracker(player, scope)
        var session: MediaSession? = null
        var started = false
        player.addListener(playback)
        player.setMediaItem(
            MediaItem.Builder()
                .setMediaId(clip.id)
                .setUri(clip.playbackUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(clip.title.trim())
                        .setArtist(clip.channel.username)
                        .setArtworkUri(clip.thumbnailUrl?.toUri())
                        .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                .setExtras(android.os.Bundle().apply {
                    setPlaybackUsageChannel(clip.channel.slug, clip.channel.username, clip.channel.profilePicture)
                })
                        .build()
                )
                .build()
        )

        fun relinquishAudio() {
            if (session == null) return
            player.pause()
            player.setAudioAttributes(kickPlaybackAudioAttributes, false)
            session?.release()
            session = null
            ClipAudioHandoff.update(false)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ClipAudioHandoff.update(true)
                    player.setAudioAttributes(kickPlaybackAudioAttributes, true)
                    session = MediaSession.Builder(context, player).setId("clip:${clip.id}").build()
                    if (!started) {
                        started = true
                        player.prepare()
                        player.play()
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> relinquishAudio()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            relinquishAudio()
            player.removeListener(playback)
            usage.close()
            player.release()
            PlaybackUsageStore.flush()
        }
    }
    LaunchedEffect(playback, playback.state.playing) {
        while (playback.state.playing) {
            delay(100)
            playback.refresh()
        }
    }
    return playback
}
