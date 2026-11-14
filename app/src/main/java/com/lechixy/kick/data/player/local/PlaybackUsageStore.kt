package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.lechixy.kick.data.common.remote.ConnectionTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class PlaybackUsageChannel(val slug: String, val name: String, val avatar: String? = null)

@Serializable
internal data class PlaybackUsage(
    val channelSlug: String,
    val channelName: String,
    val transport: ConnectionTransport,
    val watchedMs: Long = 0,
    val bytes: Long = 0,
    val channelAvatar: String? = null,
)

/** Buffers small transfer updates and persists per-channel totals in batches. */
internal object PlaybackUsageStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private val pending = mutableMapOf<Pair<String, ConnectionTransport>, PlaybackUsage>()
    private var clearedAt = 0L
    private val channelClearedAt = mutableMapOf<String, Long>()
    private val mutableEntries = MutableStateFlow<List<PlaybackUsage>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.getSharedPreferences("playback_usage", Context.MODE_PRIVATE)
        mutableEntries.value = runCatching {
            json.decodeFromString<List<PlaybackUsage>>(prefs.getString("entries", "[]") ?: "[]")
        }.getOrDefault(emptyList())
        preferences = prefs
    }

    @Synchronized
    fun add(channel: PlaybackUsageChannel, transport: ConnectionTransport, watchedMs: Long = 0, bytes: Long = 0) {
        if (watchedMs == 0L && bytes == 0L) return
        val key = channel.slug to transport
        val existing = pending[key] ?: PlaybackUsage(channel.slug, channel.name, transport)
        pending[key] = existing.copy(
            watchedMs = existing.watchedMs + watchedMs,
            bytes = existing.bytes + bytes,
            channelAvatar = channel.avatar ?: existing.channelAvatar,
        )
    }

    @Synchronized
    fun addWatchInterval(channel: PlaybackUsageChannel, transport: ConnectionTransport, since: Long, now: Long) {
        val start = maxOf(since, clearedAt, channelClearedAt[channel.slug] ?: 0L)
        add(channel, transport, watchedMs = (now - start).coerceAtLeast(0L))
    }

    @Synchronized
    fun clear(channelSlug: String? = null) {
        val now = SystemClock.elapsedRealtime()
        if (channelSlug == null) {
            clearedAt = now
            channelClearedAt.clear()
            pending.clear()
            persist(emptyList())
        } else {
            channelClearedAt[channelSlug] = now
            pending.keys.removeAll { it.first == channelSlug }
            persist(mutableEntries.value.filterNot { it.channelSlug == channelSlug })
        }
    }

    @Synchronized
    fun flush() {
        if (pending.isEmpty()) return
        val combined = mutableEntries.value.associateBy { it.channelSlug to it.transport }.toMutableMap()
        pending.forEach { (key, usage) ->
            val previous = combined[key]
            combined[key] = usage.copy(
                watchedMs = usage.watchedMs + (previous?.watchedMs ?: 0L),
                bytes = usage.bytes + (previous?.bytes ?: 0L),
                channelAvatar = usage.channelAvatar ?: previous?.channelAvatar,
            )
        }
        pending.clear()
        persist(combined.values.toList())
    }

    @Synchronized
    fun restore(entries: List<PlaybackUsage>) {
        flush()
        val combined = mutableEntries.value.associateBy { it.channelSlug to it.transport }.toMutableMap()
        entries.filter { it.channelSlug.isNotBlank() && it.watchedMs >= 0 && it.bytes >= 0 }.forEach { entry ->
            val key = entry.channelSlug to entry.transport
            val current = combined[key]
            combined[key] = entry.copy(
                channelName = current?.channelName ?: entry.channelName,
                watchedMs = maxOf(entry.watchedMs, current?.watchedMs ?: 0),
                bytes = maxOf(entry.bytes, current?.bytes ?: 0),
                channelAvatar = current?.channelAvatar ?: entry.channelAvatar,
            )
        }
        persist(combined.values.toList())
    }

    private fun persist(entries: List<PlaybackUsage>) {
        preferences?.edit()?.putString("entries", json.encodeToString(entries))?.apply()
        mutableEntries.value = entries
    }
}
