package com.lechixy.kick.service.player

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.lechixy.kick.service.EXTRA_LIVE_DVR

/** Applies to overlay, mini player, PiP, notification, and headset resumes alike. */
@UnstableApi
internal class LiveResumeListener(private val player: Player) : Player.Listener {
    private var wasPlayWhenReady = player.playWhenReady
    private var rewound = false
    private var dvr = false

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        rewound = false
        dvr = mediaItem?.mediaMetadata?.extras?.getBoolean(EXTRA_LIVE_DVR) == true
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            rewound = player.distanceBehindLiveTargetMs() > 1_500L
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val resumed = playWhenReady && !wasPlayWhenReady
        wasPlayWhenReady = playWhenReady
        if (resumed && !dvr && player.isCurrentMediaItemLive &&
            !(rewound && player.distanceBehindLiveTargetMs() > 1_500L)
        ) player.seekToDefaultPosition()
    }
}

/** Extra distance behind the manifest's safe live target, not camera-to-screen latency. */
@UnstableApi
internal fun Player.distanceBehindLiveTargetMs(): Long {
    val timeline = currentTimeline
    val index = currentMediaItemIndex
    if (!isCurrentMediaItemLive || index !in 0 until timeline.windowCount) return C.TIME_UNSET
    val target = timeline.getWindow(index, Timeline.Window()).defaultPositionMs
    if (target == C.TIME_UNSET) return C.TIME_UNSET
    return (target - currentPosition).coerceAtLeast(0L)
}

internal fun isNearLiveTarget(distanceMs: Long, wasNear: Boolean): Boolean =
    distanceMs >= 0L && distanceMs <= if (wasNear) 3_000L else 1_500L
