package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.lechixy.kick.data.player.model.RecordedPlayback

@Serializable
data class WatchHistoryEntry(
    val key: String,
    val channelSlug: String,
    val channelName: String,
    val title: String,
    val thumbnail: String? = null,
    val recording: RecordedPlayback? = null,
    val livestreamId: Long? = null,
    val vodId: String? = null,
    val startTime: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watchedAt: Long = 0
)

/** Stores played media only, without playback URLs or account credentials. */
object WatchHistoryStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private val mutableEntries = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        mutableEntries.value = runCatching {
            json.decodeFromString<List<WatchHistoryEntry>>(prefs.getString("entries", "[]") ?: "[]")
        }.getOrDefault(emptyList())
        preferences = prefs
    }

    @Synchronized
    fun save(entry: WatchHistoryEntry, positionMs: Long, durationMs: Long) {
        if (entry.channelSlug.isBlank() || positionMs <= 0) return
        val current = mutableEntries.value.firstOrNull()
        if (current?.key == entry.key && current.positionMs == positionMs && current.durationMs == durationMs.coerceAtLeast(0)) return
        val updated = entry.copy(positionMs = positionMs, durationMs = durationMs.coerceAtLeast(0),
            watchedAt = System.currentTimeMillis())
        val entries = mergeWatchHistory(mutableEntries.value, updated)
        mutableEntries.value = entries
        preferences?.edit()?.putString("entries", json.encodeToString(entries))?.apply()
    }
}

const val EXTRA_WATCH_HISTORY = "kick_watch_history"

internal fun mergeWatchHistory(entries: List<WatchHistoryEntry>, updated: WatchHistoryEntry): List<WatchHistoryEntry> =
    (listOf(updated) + entries.filter { existing ->
        existing.key != updated.key && !(updated.recording != null &&
            (existing.recording?.progressId == updated.recording.progressId ||
                existing.vodId == updated.recording.id))
    }).take(20)
