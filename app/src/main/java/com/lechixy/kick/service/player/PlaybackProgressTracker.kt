package com.lechixy.kick.service.player

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.local.WatchHistoryStore
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
                Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION)) saveCurrent()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // Save the outgoing recording before a new recording/live item replaces it or the playlist is cleared.
        if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
            val oldItem = oldPosition.mediaItem ?: return
            val duration = oldItem.mediaMetadata.durationMs ?: 0
            oldItem.mediaMetadata.extras?.getString(EXTRA_RECORDING_ID)?.let {
                PlaybackProgressStore.save(it, oldPosition.positionMs, duration)
            }
            if (watchedCurrent && oldItem.mediaId == lastMediaId) historyEntry(oldItem)?.let {
                val position = if (it.livestreamId == null) oldPosition.positionMs
                    else livePositionOffsetMs?.plus(oldPosition.positionMs)
                if (position != null) WatchHistoryStore.save(
                    it, position, if (it.livestreamId == null) duration else 0)
            }
        }
    }

    private var lastMediaId: String? = null
    private var watchedCurrent = false
    private var livePositionOffsetMs: Long? = null
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun saveCurrent() {
        val item = player.currentMediaItem
        val entry = item?.let(::historyEntry)
        if (lastMediaId != item?.mediaId) {
            lastMediaId = item?.mediaId
            WatchHistoryStore.setActivePlayback(entry?.key)
            watchedCurrent = false
            livePositionOffsetMs = null
        }
        if (item == null) return
        if (player.isPlaying) watchedCurrent = true
        if (player.playbackState == Player.STATE_IDLE || !watchedCurrent) return
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: item.mediaMetadata.durationMs ?: 0
        item.mediaMetadata.extras?.getString(EXTRA_RECORDING_ID)?.let {
            PlaybackProgressStore.save(it, player.currentPosition, duration)
        }
        if (entry == null) return
        val position = if (entry.livestreamId != null) {
            val timeline = player.currentTimeline
            if (player.currentMediaItemIndex !in 0 until timeline.windowCount) return
            val window = timeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            val start = item.mediaMetadata.extras?.getLong(com.lechixy.kick.service.EXTRA_START_TIME_MS) ?: 0L
            // HLS positions are relative to a sliding window; persist time since broadcast start.
            if (window.windowStartTimeMs == C.TIME_UNSET || start <= 0) return
            livePositionOffsetMs = window.windowStartTimeMs - start
            broadcastPositionMs(window.windowStartTimeMs, player.currentPosition, start) ?: return
        } else player.currentPosition
        WatchHistoryStore.save(entry, position,
            if (entry.livestreamId != null) 0 else duration)
    }

    private fun historyEntry(item: MediaItem): com.lechixy.kick.data.player.local.WatchHistoryEntry? =
        item.mediaMetadata.extras?.getString(com.lechixy.kick.data.player.local.EXTRA_WATCH_HISTORY)?.let {
            runCatching { json.decodeFromString<com.lechixy.kick.data.player.local.WatchHistoryEntry>(it) }.getOrNull()
        }

    fun close() {
        saveCurrent()
        WatchHistoryStore.setActivePlayback(null)
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }
}

/** Returns a VOD-relative position, never a position relative to the moving live window. */
internal fun broadcastPositionMs(windowStartMs: Long, positionMs: Long, broadcastStartMs: Long): Long? {
    if (windowStartMs == C.TIME_UNSET || broadcastStartMs <= 0 || positionMs < 0) return null
    return (windowStartMs + positionMs - broadcastStartMs).coerceAtLeast(0)
}
