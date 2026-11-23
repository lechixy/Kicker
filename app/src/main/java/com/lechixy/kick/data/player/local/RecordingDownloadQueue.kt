package com.lechixy.kick.data.player.local

import android.content.Context
import android.content.SharedPreferences
import com.lechixy.kick.data.player.model.LiveDownloadSnapshot
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class RecordingDownloadTask(
    val id: String,
    val recording: RecordedPlayback,
    val failed: Boolean = false,
    val selection: RecordingDownloadSelection? = null,
    val liveSnapshot: LiveDownloadSnapshot? = null,
)

private fun RecordingDownloadTask.sameDownload(other: RecordingDownloadTask): Boolean =
    recording.progressId == other.recording.progressId &&
        selection?.range == other.selection?.range &&
        (selection?.timelineOffsetMs ?: 0L) == (other.selection?.timelineOffsetMs ?: 0L) &&
        liveSnapshot == other.liveSnapshot

internal data class RecordingDownloadProgress(
    val id: String,
    val percent: Int? = null,
    val saving: Boolean = false,
    val writtenBytes: Long = 0,
)

/** Accessed on the main thread by the foreground service and the downloads screen. */
internal object RecordingDownloadQueue {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var preferences: SharedPreferences
    private val mutableTasks = MutableStateFlow<List<RecordingDownloadTask>>(emptyList())
    private val mutableProgress = MutableStateFlow<RecordingDownloadProgress?>(null)
    val tasks = mutableTasks.asStateFlow()
    val progress = mutableProgress.asStateFlow()

    fun init(context: Context) {
        preferences = context.getSharedPreferences("recording_download_queue", Context.MODE_PRIVATE)
        mutableTasks.value = runCatching {
            json.decodeFromString<List<RecordingDownloadTask>>(preferences.getString("tasks", "[]") ?: "[]")
        }.getOrDefault(emptyList())
    }

    fun enqueue(
        recording: RecordedPlayback,
        selection: RecordingDownloadSelection?,
        liveSnapshot: LiveDownloadSnapshot?,
    ): Boolean {
        val entry = RecordingDownloadTask(UUID.randomUUID().toString(), recording,
            selection = selection, liveSnapshot = liveSnapshot)
        if (tasks.value.any { !it.failed && it.sameDownload(entry) }) return false
        val entries = tasks.value.filterNot { it.sameDownload(entry) }
        update(entries + entry)
        return true
    }

    fun retry(id: String) {
        val task = tasks.value.firstOrNull { it.id == id && it.failed } ?: return
        update(tasks.value.filterNot { it.id == id } + task.copy(failed = false))
    }

    fun fail(id: String) = update(tasks.value.map { if (it.id == id) it.copy(failed = true) else it })

    fun remove(id: String) = update(tasks.value.filterNot { it.id == id })

    fun updateProgress(progress: RecordingDownloadProgress?) { mutableProgress.value = progress }

    fun restore(entries: List<RecordingDownloadTask>) {
        val valid = entries.filter {
            it.id.isNotBlank() && it.recording.id.isNotBlank() && it.recording.directUrl == null && it.liveSnapshot == null
        }
        update((tasks.value + valid).distinctBy { it.id }.fold(emptyList()) { result, task ->
            if (result.any { it.sameDownload(task) }) result else result + task
        })
    }

    private fun update(entries: List<RecordingDownloadTask>) {
        preferences.edit().putString("tasks", json.encodeToString(entries)).apply()
        mutableTasks.value = entries
    }
}
