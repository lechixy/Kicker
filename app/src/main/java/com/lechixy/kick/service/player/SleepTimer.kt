package com.lechixy.kick.service.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.lechixy.kick.service.EXTRA_LIVE_DVR
import com.lechixy.kick.service.EXTRA_VIEWER_CHANNEL_ID
import com.lechixy.kick.service.EXTRA_VIEWER_LIVESTREAM_ID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.util.player.NotificationUtils

internal fun sleepTimerRemainingSeconds(deadlineMs: Long, nowMs: Long): Int =
    ((deadlineMs - nowMs).coerceAtLeast(0L).let { (it + 999L) / 1000L }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun sleepTimerDisplayMinutes(seconds: Int): Int = ((seconds.coerceAtLeast(0).toLong() + 59) / 60).toInt()

data class SleepTimerState(
    val selectedMinutes: Int = 0,
    val remainingSeconds: Int = 0,
    val atMediaEnd: Boolean = false,
) {
    val active get() = selectedMinutes > 0 || atMediaEnd
}

/** Owned by playback, never by a screen or by the notification permission callback. */
object SleepTimer {
    private val mutableState = MutableStateFlow(SleepTimerState())
    val state = mutableState.asStateFlow()
    private val deadlines = MutableStateFlow<Long?>(null)
    private var runner: Job? = null
    private var player: Player? = null
    private var endMediaId: String? = null
    private var endLivestreamId: Long? = null
    private var blockedRaidChannelId: Long? = null
    private var stoppedMediaId: String? = null
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null && !stopsAutomaticPlayback(mediaItem.mediaId)) {
                blockedRaidChannelId = null
                stoppedMediaId = null
            }
            if (mediaItem?.mediaId != endMediaId &&
                (endLivestreamId == null ||
                    mediaItem?.mediaMetadata?.extras?.getLong(EXTRA_VIEWER_LIVESTREAM_ID) != endLivestreamId)
            ) {
                if (mutableState.value.atMediaEnd) start(0)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED && mutableState.value.atMediaEnd &&
                player?.currentMediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_LIVE_DVR) != true
            ) finish()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) stoppedMediaId = null
        }
    }

    fun start(minutes: Int) {
        require(minutes in 0..1440)
        endMediaId = null
        endLivestreamId = null
        blockedRaidChannelId = null
        stoppedMediaId = null
        mutableState.value = SleepTimerState(minutes, minutes * 60)
        deadlines.value = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else null
    }

    fun startAtMediaEnd(mediaId: String) {
        val currentPlayer = player ?: return
        val item = currentPlayer.currentMediaItem?.takeIf { it.mediaId == mediaId } ?: return
        start(0)
        endMediaId = item.mediaId
        endLivestreamId = item.mediaMetadata.extras?.getLong(EXTRA_VIEWER_LIVESTREAM_ID)?.takeIf { it > 0 }
        mutableState.value = SleepTimerState(atMediaEnd = true)
        listener.onPlaybackStateChanged(currentPlayer.playbackState)
    }

    internal fun suppressesAutomaticRaid(channelId: Long): Boolean =
        mutableState.value.active || blockedRaidChannelId == channelId

    internal fun stopsAutomaticPlayback(mediaId: String): Boolean =
        stoppedMediaId == mediaId.removeSuffix("_dvr")

    internal fun onLivestreamEnded(channelId: Long, livestreamId: Long) {
        val extras = player?.currentMediaItem?.mediaMetadata?.extras
        if (extras?.getLong(EXTRA_VIEWER_CHANNEL_ID) != channelId ||
            extras?.getLong(EXTRA_VIEWER_LIVESTREAM_ID) != livestreamId
        ) return
        if (!mutableState.value.active) return
        blockAutomaticPlayback()
        if (endLivestreamId == livestreamId) finish() else player?.pause()
    }

    private fun blockAutomaticPlayback() {
        val item = player?.currentMediaItem
        stoppedMediaId = item?.mediaId?.removeSuffix("_dvr") ?: stoppedMediaId
        blockedRaidChannelId = item?.mediaMetadata?.extras
            ?.getLong(EXTRA_VIEWER_CHANNEL_ID)?.takeIf { it > 0 } ?: blockedRaidChannelId
    }

    private fun finish(pausePlayback: Boolean = true) {
        if (!mutableState.value.active) return
        blockAutomaticPlayback()
        endMediaId = null
        endLivestreamId = null
        deadlines.value = null
        mutableState.value = SleepTimerState()
        if (pausePlayback) player?.pause()
    }

    fun attach(context: Context, player: Player, scope: CoroutineScope) {
        runner?.cancel()
        this.player?.removeListener(listener)
        this.player = player
        player.addListener(listener)
        val appContext = context.applicationContext
        runner = scope.launch(Dispatchers.Main.immediate) {
            deadlines.collectLatest { deadline ->
                if (deadline == null) {
                    NotificationUtils.cancelTimerNotification(appContext)
                    return@collectLatest
                }
                var lastDisplayedMinutes = -1
                runSleepTimerCountdown(player, deadline, onTick = { remaining ->
                    mutableState.update { it.copy(remainingSeconds = remaining) }
                    val displayedMinutes = sleepTimerDisplayMinutes(remaining)
                    if (remaining > 0 && displayedMinutes != lastDisplayedMinutes) {
                        NotificationUtils.updateTimerNotification(appContext, remaining)
                        lastDisplayedMinutes = displayedMinutes
                    }
                }, onFinished = {
                    finish(pausePlayback = false)
                    NotificationUtils.showTimerEndedNotification(appContext)
                })
            }
        }
    }

    fun detach(context: Context) {
        runner?.cancel()
        runner = null
        player?.removeListener(listener)
        player = null
        start(0)
        NotificationUtils.cancelTimerNotification(context)
    }
}

/** Uses an elapsed-time deadline; paused playback and skipped ticks cannot stop or extend it. */
internal suspend fun runSleepTimerCountdown(
    player: Player,
    deadlineMs: Long,
    nowMs: () -> Long = SystemClock::elapsedRealtime,
    onTick: (Int) -> Unit,
    onFinished: () -> Unit,
    waitNextTick: suspend () -> Unit = { delay(1000.milliseconds) }
) {
    while (true) {
        currentCoroutineContext().ensureActive()
        val remaining = sleepTimerRemainingSeconds(deadlineMs, nowMs())
        onTick(remaining)
        if (remaining == 0) {
            // Do this before resetting UI state or attempting the completion notification.
            player.pause()
            onFinished()
            return
        }
        waitNextTick()
    }
}
