package com.lechixy.kick.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.player.model.RecordingDownloadRange
import com.lechixy.kick.service.PreparedRecordingDownload
import com.lechixy.kick.service.kickPlaybackAudioAttributes
import com.lechixy.kick.service.recordingDownloadDataSource
import com.lechixy.kick.service.recordingDownloadMediaItem
import kotlinx.coroutines.delay

@Composable
internal fun DownloadPreviewFrame(thumbnail: String?, frameWidth: Dp, content: @Composable () -> Unit) {
    Surface(
        Modifier.widthIn(max = frameWidth).fillMaxWidth().aspectRatio(16f / 9),
        shape = MaterialTheme.shapes.medium,
        color = Color.Black,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (thumbnail != null) {
                AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun RecordingDownloadPreview(
    source: PreparedRecordingDownload,
    thumbnail: String?,
    range: RecordingDownloadRange?,
    partial: Boolean,
    showEnd: Boolean,
    onDuration: (Long) -> Unit,
    onBitrate: (Long) -> Unit,
    frameWidth: Dp,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportDuration by rememberUpdatedState(onDuration)
    val reportBitrate by rememberUpdatedState(onBitrate)
    val seekToEnd by rememberUpdatedState(showEnd)
    val player = remember(source) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(recordingDownloadDataSource(source.snapshot)))
            .build().apply {
                volume = 0f
                setAudioAttributes(kickPlaybackAudioAttributes, false)
                setMediaItem(recordingDownloadMediaItem(source.url, source.selection, source.snapshot, clip = false))
            }
    }
    var loadedRange by remember(player) { mutableStateOf<RecordingDownloadRange?>(null) }
    var ready by remember(player) { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(false) }
    var failed by remember(player) { mutableStateOf(false) }
    var muted by remember(player) { mutableStateOf(true) }
    val requestedRange = range.takeIf { partial }
    val updatingRange = range != null && loadedRange != requestedRange
    val controlsEnabled = ready && range != null && !failed && !updatingRange

    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_READY) return
                ready = true
                if (loadedRange == null && player.duration > 0) reportDuration(player.duration)
            }

            override fun onTracksChanged(tracks: Tracks) {
                var bitrate = 0L
                for (group in tracks.groups) {
                    for (index in 0 until group.length) {
                        if (group.isTrackSelected(index)) bitrate += group.getTrackFormat(index).bitrate.coerceAtLeast(0)
                    }
                }
                if (bitrate > 0) reportBitrate(bitrate)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                ready = false
                failed = true
                player.pause()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        player.prepare()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, range, partial) {
        if (range == null) {
            player.pause()
        } else if (loadedRange != requestedRange) {
            player.pause()
            // The range slider can publish several changes while the thumb is being dragged.
            delay(220)
            ready = false
            failed = false
            loadedRange = requestedRange
            val mediaItem = recordingDownloadMediaItem(
                source.url, source.selection.copy(range = requestedRange), source.snapshot, clip = true,
            )
            val position = if (seekToEnd && requestedRange != null) previewEnd(requestedRange.durationMs) else 0L
            player.setMediaItem(mediaItem, position)
            player.prepare()
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(if (partial) R.string.recording_download_selected_preview else R.string.recording_download_preview),
            modifier = Modifier.align(Alignment.Start), style = MaterialTheme.typography.titleSmall)
        DownloadPreviewFrame(thumbnail.takeUnless { ready }, frameWidth) {
            if (!failed) {
                PlayerVideoSurface(player, playing, Modifier.fillMaxSize(), showControls = controlsEnabled)
                if (controlsEnabled) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                        FilledIconButton(
                            onClick = {
                                muted = !muted
                                player.volume = if (muted) 0f else 1f
                                player.setAudioAttributes(kickPlaybackAudioAttributes, !muted)
                            },
                            modifier = Modifier.padding(6.dp).size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.Black.copy(alpha = .68f), contentColor = Color.White,
                            ),
                        ) {
                            Icon(
                                painterResource(if (muted) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                                    else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                stringResource(if (muted) R.string.recording_download_unmute_preview
                                    else R.string.recording_download_mute_preview),
                            )
                        }
                    }
                }
            }
            if (failed && !updatingRange) {
                Surface(shape = MaterialTheme.shapes.large) {
                    TextButton(onClick = {
                        failed = false
                        player.prepare()
                    }) { Text(stringResource(R.string.clip_preview_retry)) }
                }
            } else if (!ready || updatingRange) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Color.White)
            }
        }
        if (partial) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    enabled = controlsEnabled,
                    onClick = {
                        player.pause()
                        player.seekTo(0L)
                    },
                    label = { Text(stringResource(R.string.recording_download_preview_start)) },
                )
                AssistChip(
                    enabled = controlsEnabled,
                    onClick = {
                        player.pause()
                        range?.let { player.seekTo(previewEnd(it.durationMs)) }
                    },
                    label = { Text(stringResource(R.string.recording_download_preview_end)) },
                )
            }
        }
    }
}

private fun previewEnd(durationMs: Long): Long = (durationMs - 200L).coerceAtLeast(0L)
