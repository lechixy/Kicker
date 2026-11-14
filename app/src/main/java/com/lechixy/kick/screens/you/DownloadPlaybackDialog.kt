package com.lechixy.kick.screens.you

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveDialog
import com.lechixy.kick.data.player.local.RecordingDownload
import com.lechixy.kick.service.ClipAudioHandoff
import com.lechixy.kick.service.kickPlaybackAudioAttributes
import com.lechixy.kick.service.player.PlaybackUsageTracker
import com.lechixy.kick.service.player.setPlaybackUsageChannel

@OptIn(UnstableApi::class)
@Composable
internal fun DownloadPlaybackDialog(recording: RecordingDownload, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val player = remember(recording.uri) {
        ExoPlayer.Builder(context.applicationContext)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }
    var failed by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val usage = PlaybackUsageTracker(player, scope, localPlayback = true)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { failed = player.playerError != null }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ClipAudioHandoff.update(true)
                    player.setAudioAttributes(kickPlaybackAudioAttributes, true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                    player.setAudioAttributes(kickPlaybackAudioAttributes, false)
                    ClipAudioHandoff.update(false)
                }
                else -> Unit
            }
        }
        player.addListener(listener)
        val extras = android.os.Bundle().apply {
            recording.metadata?.let { metadata ->
                metadata.channelSlug?.let { setPlaybackUsageChannel(it, metadata.channelName ?: it, metadata.channelAvatar) }
            }
        }
        player.setMediaItem(MediaItem.Builder().setUri(recording.uri)
            .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build())
        player.prepare()
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.play()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            usage.close()
            player.release()
            ClipAudioHandoff.update(false)
        }
    }
    PredictiveDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 840.dp).fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recording.name, style = MaterialTheme.typography.titleMedium)
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player } },
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                if (failed) Text(stringResource(R.string.you_download_open_failed))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.account_close))
                }
            }
        }
    }
}
