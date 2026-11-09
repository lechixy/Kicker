package com.lechixy.kick.service.player

import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.lechixy.kick.data.common.remote.NetworkMonitor
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackUsageChannel
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val CHANNEL_SLUG = "kick_usage_channel_slug"
private const val CHANNEL_NAME = "kick_usage_channel_name"

internal fun Bundle.setPlaybackUsageChannel(slug: String, name: String) {
    putString(CHANNEL_SLUG, slug)
    putString(CHANNEL_NAME, name)
}

internal fun MediaItem?.usageChannel(): PlaybackUsageChannel? {
    val extras = this?.mediaMetadata?.extras ?: return null
    val slug = extras.getString(CHANNEL_SLUG)?.takeIf(String::isNotBlank) ?: return null
    return PlaybackUsageChannel(slug, extras.getString(CHANNEL_NAME).orEmpty().ifBlank { slug })
}

@OptIn(UnstableApi::class)
internal fun DataSource.Factory.trackUsage(channel: PlaybackUsageChannel?): DataSource.Factory {
    if (channel == null) return this
    val listener = object : TransferListener {
        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
            if (isNetwork) PlaybackUsageStore.add(channel, NetworkMonitor.transport.value, bytes = bytesTransferred.toLong())
        }
    }
    return DataSource.Factory { createDataSource().apply { addTransferListener(listener) } }
}

/** Tracks elapsed playback time, so seeks, buffering and pauses do not inflate totals. */
internal class PlaybackUsageTracker(
    private val player: Player,
    scope: CoroutineScope,
    private val localPlayback: Boolean = false,
) : Player.Listener {
    private val handler = Handler(player.applicationLooper)
    private var channel = player.currentMediaItem.usageChannel().takeIf { player.isPlaying }
    private var transport = currentTransport()
    private var since = SystemClock.elapsedRealtime()
    private val networkObserver = scope.launch(Dispatchers.Main.immediate) {
        if (!localPlayback) NetworkMonitor.transport.collect { sample() }
    }
    private val tick = object : Runnable {
        override fun run() {
            sample()
            PlaybackUsageStore.flush()
            handler.postDelayed(this, 5_000)
        }
    }

    init {
        player.addListener(this)
        handler.postDelayed(tick, 5_000)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            sample()
            PlaybackUsageStore.flush()
        }
    }

    private fun sample() {
        val now = SystemClock.elapsedRealtime()
        channel?.let { PlaybackUsageStore.add(it, transport, watchedMs = now - since) }
        channel = if (player.isPlaying) player.currentMediaItem.usageChannel() else null
        transport = currentTransport()
        since = now
    }

    private fun currentTransport() = if (localPlayback) ConnectionTransport.OFFLINE else NetworkMonitor.transport.value

    fun close() {
        sample()
        PlaybackUsageStore.flush()
        networkObserver.cancel()
        handler.removeCallbacks(tick)
        player.removeListener(this)
    }
}
