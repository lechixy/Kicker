package com.lechixy.kick.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

internal val kickPlaybackAudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

/** Both players and the modal are confined to the application main thread. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object ClipAudioHandoff {
    private var stream: StreamSessionPlayer? = null
    var active by mutableStateOf(false)
        private set

    fun attach(player: StreamSessionPlayer) {
        stream = player
        player.setClipAudioActive(active)
    }

    fun detach(player: StreamSessionPlayer) {
        if (stream === player) stream = null
    }

    fun update(active: Boolean) {
        if (this.active == active) return
        this.active = active
        stream?.setClipAudioActive(active)
    }
}

/** Keep controller volume changes separate from the modal's temporary silence. */
@UnstableApi
internal class StreamSessionPlayer(private val stream: ExoPlayer) : ForwardingPlayer(stream) {
    private var requestedVolume = stream.volume
    private var clipAudioActive = false

    override fun setVolume(volume: Float) {
        requestedVolume = volume.coerceIn(0f, 1f)
        if (!clipAudioActive) stream.volume = requestedVolume
    }

    fun setClipAudioActive(active: Boolean) {
        if (clipAudioActive == active) return
        clipAudioActive = active
        if (active) {
            stream.volume = 0f
            stream.setAudioAttributes(kickPlaybackAudioAttributes, false)
        } else {
            stream.setAudioAttributes(kickPlaybackAudioAttributes, true)
            stream.volume = requestedVolume
        }
    }
}
