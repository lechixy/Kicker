package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlaybackProgress(val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f) else 0f

    fun resumePosition(currentDurationMs: Long = durationMs): Long = when {
        positionMs <= 0 -> 0
        currentDurationMs > 0 && positionMs >= currentDurationMs - 1_000 -> 0
        else -> positionMs
    }
}

/** Shared by the service and cards, with the same lifetime as the application. */
object PlaybackProgressStore {
    private val json = Json { ignoreUnknownKeys = true }
    private var preferences: SharedPreferences? = null
    private val mutableProgress = MutableStateFlow<Map<String, PlaybackProgress>>(emptyMap())
    val progress = mutableProgress.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("recording_progress", Context.MODE_PRIVATE)
        mutableProgress.value = runCatching {
            json.decodeFromString<Map<String, PlaybackProgress>>(prefs.getString("progress", "{}") ?: "{}")
        }.getOrDefault(emptyMap())
        preferences = prefs
    }

    @Synchronized
    fun save(id: String, positionMs: Long, durationMs: Long) {
        if (id.isBlank() || positionMs < 0) return
        val knownDuration = durationMs.takeIf { it > 0 } ?: mutableProgress.value[id]?.durationMs ?: 0
        val position = if (knownDuration > 0) positionMs.coerceAtMost(knownDuration) else positionMs
        val updated = PlaybackProgress(position, knownDuration)
        if (mutableProgress.value[id] == updated) return
        val entries = mutableProgress.value + (id to updated)
        mutableProgress.value = entries
        preferences?.edit()?.putString("progress", json.encodeToString(entries))?.apply()
    }

    fun get(id: String): PlaybackProgress? = mutableProgress.value[id]
}
