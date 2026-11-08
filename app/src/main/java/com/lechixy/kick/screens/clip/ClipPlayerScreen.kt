package com.lechixy.kick.screens.clip

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.ClipChannel
import com.lechixy.kick.data.channel.model.ClipCreator
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.channel.model.ClipVideo
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.theme.KickTheme
import com.lechixy.kick.theme.Theme
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest
import kotlinx.coroutines.delay

@Composable
internal fun ClipPlayerScreen(
    uiState: ClipPlayerUiState,
    playback: ClipPlaybackState,
    fullscreen: Boolean,
    showChat: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onMuteToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onChatVisibilityToggle: () -> Unit,
    onCloseClick: () -> Unit,
    onRetryPlayback: () -> Unit,
    onRetryChat: () -> Unit,
    onWatchLivestream: (ClipDetail) -> Unit,
    onWatchFullVideo: (ClipDetail) -> Unit,
    video: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val videoHeight = minOf(maxWidth * (9f / 16f), maxHeight * .55f)
        val split = maxWidth >= 760.dp || (maxWidth >= 600.dp && maxWidth > maxHeight)
        val videoPane: @Composable (Modifier) -> Unit = { paneModifier ->
            ClipVideoPane(
                uiState, playback, fullscreen, onPlayPauseToggle, onSeekTo, onMuteToggle,
                onFullscreenToggle, onCloseClick, onRetryPlayback, video, paneModifier,
            )
        }
        val panel: @Composable (Modifier) -> Unit = { panelModifier ->
            ClipReplayPanel(
                uiState, playback.positionMs, showChat, onChatVisibilityToggle, onCloseClick,
                onRetryChat, onWatchLivestream, onWatchFullVideo, panelModifier,
            )
        }
        when {
            fullscreen -> videoPane(Modifier.fillMaxSize())
            split -> Row(Modifier.fillMaxSize()) {
                videoPane(Modifier.weight(1f).fillMaxHeight())
                if (showChat) {
                    panel(Modifier.width(340.dp).fillMaxHeight())
                } else {
                    Column(
                        Modifier.width(52.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ClipControlButton(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_close, onCloseClick)
                        ClipControlButton(R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_show_chat, onChatVisibilityToggle)
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                videoPane(Modifier.fillMaxWidth().height(videoHeight))
                panel(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun ClipVideoPane(
    uiState: ClipPlayerUiState,
    playback: ClipPlaybackState,
    fullscreen: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onMuteToggle: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onCloseClick: () -> Unit,
    onRetryPlayback: () -> Unit,
    video: @Composable () -> Unit,
    modifier: Modifier,
) {
    val clip = uiState.clip
    var controlsVisible by remember(clip?.id) { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val dragging = scrubbing != null
    LaunchedEffect(controlsVisible, playback.playing, interaction, dragging, fullscreen) {
        if (controlsVisible && playback.playing && !dragging) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        video()
        if (clip != null && !playback.renderedFrame) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(context, clip.thumbnailUrl) {
                    buildOptimizedImageRequest(context, clip.thumbnailUrl, 960, 540)
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (clip != null) {
            Box(Modifier.matchParentSize().pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            })
            if (controlsVisible || !playback.playing) {
                ClipTitleOverlay(clip, fullscreen, onCloseClick)
                ClipControls(
                    playback, fullscreen, scrubbing,
                    onScrub = { scrubbing = it },
                    onSeek = {
                        scrubbing?.let { onSeekTo((it * playback.durationMs).toLong()) }
                        scrubbing = null
                        interaction++
                    },
                    onPlayPause = { interaction++; controlsVisible = true; onPlayPauseToggle() },
                    onMute = { interaction++; onMuteToggle() },
                    onFullscreen = { interaction++; onFullscreenToggle() },
                )
            }
        }
        when {
            uiState.loading || (playback.buffering && !playback.failed && clip != null) ->
                CircularProgressIndicator()
            clip == null || playback.failed -> Column(
                Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.clip_playback_failed), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRetryPlayback) { Text(stringResource(R.string.chat_retry)) }
            }
        }
    }
}

@Composable
private fun BoxScope.ClipTitleOverlay(clip: ClipDetail, fullscreen: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val relativeTime = remember(context, locale, clip.createdAt) { FormatUtils.formatLastLive(context, clip.createdAt) }
    val views = remember(clip.views, locale) { java.text.NumberFormat.getIntegerInstance(locale).format(clip.views) }
    val title = clip.title.trim().ifEmpty { stringResource(R.string.clip_preview_label) }
    Row(
        Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .88f), Color.Transparent)))
            .padding(start = 14.dp, top = 10.dp, end = 8.dp, bottom = 18.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                clip.category?.let { "$title • ${it.name}" } ?: title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.clip_metadata, clip.creator.username, views, relativeTime),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .84f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (fullscreen) ClipControlButton(
            R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_close, onClose, Color.White,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BoxScope.ClipControls(
    playback: ClipPlaybackState,
    fullscreen: Boolean,
    scrubbing: Float?,
    onScrub: (Float) -> Unit,
    onSeek: () -> Unit,
    onPlayPause: () -> Unit,
    onMute: () -> Unit,
    onFullscreen: () -> Unit,
) {
    val duration = playback.durationMs
    val position = scrubbing?.let { (it * duration).toLong() } ?: playback.positionMs
    val slider = remember { SliderState() }
    slider.value = scrubbing ?: if (duration > 0) position.toFloat() / duration else 0f
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .92f))))
            .padding(horizontal = 8.dp),
    ) {
        Slider(
            state = slider,
            onValueChange = onScrub,
            onValueChangeFinished = onSeek,
            enabled = duration > 0 && !playback.failed,
            colors = SliderDefaults.colors(inactiveTrackColor = Color.White.copy(alpha = .28f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ClipControlButton(
                if (playback.playRequested) R.drawable.pause_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                else R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
                if (playback.playRequested) R.string.clip_pause else R.string.clip_play,
                onPlayPause, Color.White,
            )
            ClipControlButton(
                if (playback.muted) R.drawable.volume_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                else R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                if (playback.muted) R.string.clip_unmute else R.string.clip_mute,
                onMute, Color.White,
            )
            Text(
                "${FormatUtils.formatDuration(position)} / ${FormatUtils.formatDuration(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ClipControlButton(
                if (fullscreen) R.drawable.fullscreen_exit_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                else R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                if (fullscreen) R.string.clip_exit_fullscreen else R.string.clip_fullscreen,
                onFullscreen, Color.White,
            )
        }
    }
}

@Composable
internal fun ClipControlButton(icon: Int, label: Int, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onSurface) {
    IconButton(onClick = onClick) {
        Icon(painterResource(icon), stringResource(label), tint = tint)
    }
}

@Preview(name = "Clip Player - Landscape Dark", widthDp = 960, heightDp = 540, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Clip Player - Portrait Dark", widthDp = 412, heightDp = 915, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ClipPlayerScreenPreview() {
    val clip = ClipDetail(
        id = "clip_01M6W0BW1D4E268AVEWPMVP7TA", title = "iriiisss", duration = 12.0, playbackUrl = "",
        startedAt = "2026-11-07T21:58:54Z", createdAt = "2026-11-07T21:59:31.199206Z", views = 8,
        category = Category(8, "Grand Theft Auto V (GTA)", "grand-theft-auto-v"),
        channel = ClipChannel(26239555, "RRaenee", "rraenee", isLive = true, isVerified = true),
        creator = ClipCreator(28967976, "KurabiyeDeniz", "kurabiyedeniz"),
        video = ClipVideo("01a1b5fc-f7a8-72a3-9fb8-d27e4a809c11", "gel kankam yetiştim! EightbornV", 34119),
    )
    val messages = listOf(
        ChatMessage(id = "1", content = "Geldi!", recordingOffsetMs = 1000, sender = ChatSender(username = "Ece", identity = ChatIdentity("#F2A6FF"))),
        ChatMessage(id = "2", content = "Tam zamanında", recordingOffsetMs = 1800, sender = ChatSender(username = "Deniz", identity = ChatIdentity("#86DFFF"))),
    )
    KickTheme(theme = Theme.DARK, dynamicColor = false) {
        ClipPlayerScreen(
            ClipPlayerUiState(clip, loading = false, chatMessages = messages, chatLoading = false, chatComplete = true),
            ClipPlaybackState(positionMs = 2000, durationMs = clip.durationMs, buffering = false, renderedFrame = true),
            fullscreen = false, showChat = true, onPlayPauseToggle = {}, onSeekTo = {}, onMuteToggle = {},
            onFullscreenToggle = {}, onChatVisibilityToggle = {}, onCloseClick = {}, onRetryPlayback = {},
            onRetryChat = {}, onWatchLivestream = {}, onWatchFullVideo = {}, video = {},
        )
    }
}
