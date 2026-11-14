package com.lechixy.kick.data.settings.local

import com.lechixy.kick.data.favorites.local.FavoriteUser
import com.lechixy.kick.data.favorites.local.FavoriteUsersStore
import com.lechixy.kick.data.player.local.PlaybackProgress
import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.local.PlaybackUsage
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import com.lechixy.kick.data.player.local.RecordingDownloadQueue
import com.lechixy.kick.data.player.local.RecordingDownloadTask
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.data.player.local.WatchHistoryStore
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable

@Serializable
internal data class PlaybackBackupData(
    val history: List<WatchHistoryEntry> = emptyList(),
    val progress: Map<String, PlaybackProgress> = emptyMap(),
    val usage: List<PlaybackUsage> = emptyList(),
    val favorites: List<FavoriteUser> = emptyList(),
    val downloadQueue: List<RecordingDownloadTask> = emptyList(),
) {
    fun restore() {
        WatchHistoryStore.restore(history)
        PlaybackProgressStore.restore(progress)
        PlaybackUsageStore.restore(usage)
        FavoriteUsersStore.restore(favorites)
        RecordingDownloadQueue.restore(downloadQueue)
    }

    fun withoutPlaybackUrls() = copy(
        history = history.map { it.copy(recording = it.recording?.copy(directUrl = null)) },
        downloadQueue = downloadQueue
            .filter { it.recording.directUrl == null || it.recording.id.startsWith("clip_") }
            .map { it.copy(recording = it.recording.copy(directUrl = null)) },
    )
}

internal fun playbackBackupSnapshot() = PlaybackBackupData(
    WatchHistoryStore.entries.value,
    PlaybackProgressStore.progress.value,
    PlaybackUsageStore.entries.value,
    FavoriteUsersStore.favorites.value.users,
    RecordingDownloadQueue.tasks.value,
).withoutPlaybackUrls()

internal val playbackBackupChanges = combine(
    WatchHistoryStore.entries, PlaybackProgressStore.progress, PlaybackUsageStore.entries,
    FavoriteUsersStore.favorites, RecordingDownloadQueue.tasks,
) { _, _, _, _, _ -> Unit }
