package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.chat.model.recordingStartInstant

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
    val watchedAt: Long = 0,
    val fallbackThumbnail: String? = null,
)

/** Stores played media only, without playback URLs or account credentials. */
object WatchHistoryStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private var activePlaybackKey: String? = null
    private var suppressActivePlayback = false
    private val mutableEntries = MutableStateFlow<List<WatchHistoryEntry>>(emptyList())
    val entries = mutableEntries.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("watch_history", Context.MODE_PRIVATE)
        val stored = runCatching {
            json.decodeFromString<List<WatchHistoryEntry>>(prefs.getString("entries", "[]") ?: "[]")
        }.getOrDefault(emptyList())
        preferences = prefs
        val merged = normalizeWatchHistory(stored)
        if (merged == stored) mutableEntries.value = stored else persist(merged)
    }

    @Synchronized
    fun save(entry: WatchHistoryEntry, positionMs: Long, durationMs: Long) {
        if (entry.channelSlug.isBlank() || positionMs <= 0) return
        if (suppressActivePlayback && activePlaybackKey == entry.key) return
        val current = mutableEntries.value.firstOrNull()
        if (current?.key == entry.key && current.positionMs == positionMs && current.durationMs == durationMs.coerceAtLeast(0)) return
        val updated = entry.copy(positionMs = positionMs, durationMs = durationMs.coerceAtLeast(0),
            watchedAt = System.currentTimeMillis())
        persist(mergeWatchHistory(mutableEntries.value, updated))
    }

    @Synchronized
    internal fun setActivePlayback(key: String?) {
        if (activePlaybackKey == key) return
        activePlaybackKey = key
        suppressActivePlayback = false
    }

    @Synchronized
    internal fun remove(key: String) {
        if (activePlaybackKey == key) suppressActivePlayback = true
        val remaining = mutableEntries.value.filterNot { it.key == key }
        if (remaining.size != mutableEntries.value.size) persist(remaining)
    }

    @Synchronized
    internal fun clear() {
        if (activePlaybackKey != null) suppressActivePlayback = true
        if (mutableEntries.value.isNotEmpty()) persist(emptyList())
    }

    @Synchronized
    internal fun restore(entries: List<WatchHistoryEntry>) {
        persist(normalizeWatchHistory(mutableEntries.value + entries))
    }

    internal fun progressFor(entry: WatchHistoryEntry): PlaybackProgress? =
        mutableEntries.value.firstOrNull { it.matchesBroadcast(entry) }
            ?.let { PlaybackProgress(it.positionMs, it.durationMs) }

    @Synchronized
    internal fun attachRecordings(channelSlug: String, recordings: List<RecordedPlayback>) {
        val current = mutableEntries.value
        val updated = current.map { entry ->
            if (entry.recording != null || !entry.channelSlug.equals(channelSlug, ignoreCase = true)) return@map entry
            val recording = recordings.firstOrNull {
                entry.matchesBroadcast(entry.copy(key = "recording:${it.progressId}",
                    recording = it, livestreamId = null, vodId = null, startTime = it.startTime))
            } ?: return@map entry
            entry.copy(recording = recording, thumbnail = recording.thumbnail ?: entry.thumbnail,
                durationMs = recording.durationMs)
        }
        if (updated != current) persist(normalizeWatchHistory(updated))
    }

    private fun persist(entries: List<WatchHistoryEntry>) {
        mutableEntries.value = entries
        preferences?.edit()?.putString("entries", json.encodeToString(entries))?.apply()
    }
}

const val EXTRA_WATCH_HISTORY = "kick_watch_history"

private fun normalizeWatchHistory(entries: List<WatchHistoryEntry>): List<WatchHistoryEntry> =
    entries.filter { it.key.isNotBlank() && it.channelSlug.isNotBlank() }
        .sortedByDescending { it.watchedAt }.asReversed().fold(emptyList(), ::mergeWatchHistory)

internal fun mergeWatchHistory(entries: List<WatchHistoryEntry>, updated: WatchHistoryEntry): List<WatchHistoryEntry> {
    val (matches, others) = entries.partition { it.matchesBroadcast(updated) }
    val previous = matches.firstOrNull()
    val recording = updated.recording ?: previous?.recording
    val merged = updated.copy(
        recording = recording,
        livestreamId = updated.livestreamId ?: previous?.livestreamId,
        vodId = updated.vodId ?: previous?.vodId,
        startTime = updated.startTime ?: previous?.startTime,
        thumbnail = updated.recording?.thumbnail ?: recording?.thumbnail ?: updated.thumbnail ?: previous?.thumbnail,
        fallbackThumbnail = updated.fallbackThumbnail ?: previous?.fallbackThumbnail,
        durationMs = updated.durationMs.takeIf { it > 0 } ?: recording?.durationMs ?: 0,
    )
    return (listOf(merged) + others).take(20)
}

internal fun WatchHistoryEntry.matchesBroadcast(other: WatchHistoryEntry): Boolean {
    if (key == other.key) return true
    if (!channelSlug.equals(other.channelSlug, ignoreCase = true)) return false
    if (recording?.id?.startsWith("clip_") == true || other.recording?.id?.startsWith("clip_") == true) return false
    val liveId = livestreamId ?: recording?.livestreamId
    val otherLiveId = other.livestreamId ?: other.recording?.livestreamId
    if (liveId != null && otherLiveId != null) return liveId == otherLiveId
    val ids = listOfNotNull(vodId, recording?.id, recording?.vodId, recording?.progressId, recording?.videoUuid)
    val otherIds = listOfNotNull(other.vodId, other.recording?.id, other.recording?.vodId,
        other.recording?.progressId, other.recording?.videoUuid)
    if (ids.any { it.isNotBlank() && it in otherIds }) return true
    val oneIsLive = (livestreamId != null && other.recording != null) ||
        (other.livestreamId != null && recording != null)
    val start = recordingStartInstant(startTime ?: recording?.startTime)
    return oneIsLive && start != null && start == recordingStartInstant(other.startTime ?: other.recording?.startTime)
}
