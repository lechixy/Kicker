package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import com.lechixy.kick.data.common.remote.ConnectionTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class PlaybackUsageChannel(val slug: String, val name: String)

@Serializable
internal data class PlaybackUsage(
    val channelSlug: String,
    val channelName: String,
    val transport: ConnectionTransport,
    val watchedMs: Long = 0,
    val bytes: Long = 0,
)

/** Buffers small transfer updates and persists per-channel totals in batches. */
internal object PlaybackUsageStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private val pending = mutableMapOf<Pair<String, ConnectionTransport>, PlaybackUsage>()
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
        pending[key] = existing.copy(watchedMs = existing.watchedMs + watchedMs, bytes = existing.bytes + bytes)
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
            )
        }
        pending.clear()
        val entries = combined.values.toList()
        preferences?.edit()?.putString("entries", json.encodeToString(entries))?.apply()
        mutableEntries.value = entries
    }
}
