package com.lechixy.kick.service.player

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.service.EXTRA_RECORDING_ID

/** Runs on the player's looper, including background playback and notification controls. */
@OptIn(UnstableApi::class)
internal class PlaybackProgressTracker(private val player: Player) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private val tick = object : Runnable {
        override fun run() {
            saveCurrent()
            handler.postDelayed(this, 15_000)
        }
    }

    init {
        player.addListener(this)
        handler.postDelayed(tick, 15_000)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) saveCurrent()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Save the outgoing recording before a new recording/live item replaces it or the playlist is cleared.
        if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
            val oldItem = oldPosition.mediaItem ?: return
            val id = oldItem.mediaMetadata.extras?.getString(EXTRA_RECORDING_ID) ?: return
            PlaybackProgressStore.save(id, oldPosition.positionMs, oldItem.mediaMetadata.durationMs ?: 0)
        }
    }

    private fun saveCurrent() {
        if (player.playbackState == Player.STATE_IDLE) return
        val item = player.currentMediaItem ?: return
        val id = item.mediaMetadata.extras?.getString(EXTRA_RECORDING_ID) ?: return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: item.mediaMetadata.durationMs ?: 0
        PlaybackProgressStore.save(id, player.currentPosition, duration)
    }

    fun close() {
        saveCurrent()
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }
}
