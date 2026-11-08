package com.lechixy.kick.screens.clip

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lechixy.kick.R
import com.lechixy.kick.components.PlayerVideoSurface
import com.lechixy.kick.service.ClipAudioHandoff
import com.lechixy.kick.service.kickPlaybackAudioAttributes
import com.lechixy.kick.service.playbackHttpDataSourceFactory
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

@Composable
internal fun ClipPreviewFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.fillMaxWidth().aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp)).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClipDraftPreview(
    previewUrl: String,
    trim: ClipTrim,
    isPlaying: Boolean,
    isMuted: Boolean,
    onTogglePlayback: () -> Unit,
    onToggleMute: () -> Unit,
    onPause: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnPause by rememberUpdatedState(onPause)
    val player = remember(context, previewUrl) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(playbackHttpDataSourceFactory()))
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var position by remember(player) { mutableFloatStateOf(trim.start.toFloat()) }
    var isForeground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var failed by remember(player) { mutableStateOf(false) }
    var buffering by remember(player) { mutableStateOf(true) }
    val sliderRange = trim.start.toFloat()..trim.end.toFloat()
    val sliderState = remember(trim) { SliderState(value = trim.start.toFloat(), trackRange = sliderRange) }
    sliderState.value = position.coerceIn(sliderRange)

    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                failed = player.playerError != null
                buffering = player.playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) {
                    currentOnPause()
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ClipAudioHandoff.update(true)
                    player.setAudioAttributes(kickPlaybackAudioAttributes, true)
                    isForeground = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                    currentOnPause()
                    isForeground = false
                    player.setAudioAttributes(kickPlaybackAudioAttributes, false)
                    ClipAudioHandoff.update(false)
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        player.setMediaItem(
            MediaItem.Builder().setUri(previewUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
            trim.start * 1000L,
        )
        player.prepare()
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
            if (isForeground) ClipAudioHandoff.update(false)
        }
    }
    LaunchedEffect(player, trim) {
        position = trim.start.toFloat()
        player.seekTo(trim.start * 1000L)
    }
    LaunchedEffect(player, isMuted) { player.volume = if (isMuted) 0f else 1f }
    LaunchedEffect(player, isPlaying, isForeground, failed, trim) {
        val shouldPlay = isPlaying && isForeground && !failed
        player.playWhenReady = shouldPlay
        if (!shouldPlay) return@LaunchedEffect
        val startMs = trim.start * 1000L
        val endMs = trim.end * 1000L
        while (true) {
            val current = player.currentPosition
            if (player.playbackState == Player.STATE_ENDED || current >= endMs) {
                player.seekTo(startMs)
                position = trim.start.toFloat()
            } else {
                position = (current / 1000f).coerceIn(trim.start.toFloat(), trim.end.toFloat())
            }
            delay(50)
        }
    }

    ClipPreviewFrame {
        PlayerVideoSurface(player, isPlaying && isForeground, Modifier.fillMaxSize())
        if (failed) {
            TextButton(
                onClick = {
                    player.seekTo(trim.start * 1000L)
                    player.prepare()
                },
            ) { Text(stringResource(R.string.clip_preview_retry)) }
        } else if (buffering) {
            CircularProgressIndicator()
        }
        if (!failed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .72f))
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ClipIconButton(
                        if (isPlaying) R.drawable.pause_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                        else R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
                        if (isPlaying) R.string.clip_pause else R.string.clip_play,
                        onTogglePlayback,
                        tint = Color.White,
                    )
                    Slider(
                        state = sliderState,
                        onValueChange = {
                            position = it
                            player.seekTo((it * 1000).roundToLong().coerceAtMost(trim.end * 1000L - 1))
                        },
                        colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = .35f)),
                        modifier = Modifier.weight(1f),
                    )
                    ClipIconButton(
                        if (isMuted) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                        else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        if (isMuted) R.string.clip_unmute else R.string.clip_mute,
                        onToggleMute,
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
