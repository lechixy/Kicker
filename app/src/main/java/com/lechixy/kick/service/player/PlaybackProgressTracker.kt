package com.lechixy.kick.service.player

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.local.EXTRA_WATCH_HISTORY
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.data.player.local.WatchHistoryStore
import com.lechixy.kick.service.EXTRA_LIVE_DVR
import com.lechixy.kick.service.EXTRA_RECORDING_ID
import com.lechixy.kick.service.EXTRA_START_TIME_MS
import kotlinx.serialization.json.Json

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
            if (watchedCurrent && oldItem.mediaId == lastMediaId) oldItem.watchHistoryEntry()?.let {
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
    private fun saveCurrent() {
        val item = player.currentMediaItem
        val entry = item?.watchHistoryEntry()
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
            // HLS positions are relative to a sliding window; persist time since broadcast start.
            val offset = player.liveHistoryPositionOffsetMs(item) ?: return
            livePositionOffsetMs = offset
            (offset + player.currentPosition).coerceAtLeast(0L)
        } else player.currentPosition
        WatchHistoryStore.save(entry, position,
            if (entry.livestreamId != null) 0 else duration)
    }

    fun close() {
        saveCurrent()
        WatchHistoryStore.setActivePlayback(null)
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }
}

private val historyJson = Json { ignoreUnknownKeys = true }

internal fun MediaItem.watchHistoryEntry(): WatchHistoryEntry? =
    mediaMetadata.extras?.getString(EXTRA_WATCH_HISTORY)?.let {
        runCatching { historyJson.decodeFromString<WatchHistoryEntry>(it) }.getOrNull()
    }

@androidx.annotation.OptIn(UnstableApi::class)
internal fun Player.liveHistoryPositionOffsetMs(item: MediaItem): Long? {
    val extras = item.mediaMetadata.extras ?: return null
    if (extras.getBoolean(EXTRA_LIVE_DVR)) return 0L
    val start = extras.getLong(EXTRA_START_TIME_MS)
    val timeline = currentTimeline
    val index = currentMediaItemIndex
    if (start <= 0 || index !in 0 until timeline.windowCount) return null
    val window = timeline.getWindow(index, Timeline.Window())
    return window.windowStartTimeMs.takeUnless { it == C.TIME_UNSET }?.minus(start)
}

/** Returns a VOD-relative position, never a position relative to the moving live window. */
internal fun broadcastPositionMs(windowStartMs: Long, positionMs: Long, broadcastStartMs: Long): Long? {
    if (windowStartMs == C.TIME_UNSET || broadcastStartMs <= 0 || positionMs < 0) return null
    return (windowStartMs + positionMs - broadcastStartMs).coerceAtLeast(0)
}
