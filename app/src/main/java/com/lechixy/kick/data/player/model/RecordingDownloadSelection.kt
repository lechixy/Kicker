package com.lechixy.kick.data.player.model

import kotlinx.serialization.Serializable

@Serializable
internal data class RecordingDownloadRange(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0 && endMs > startMs)
    }

    val durationMs: Long get() = endMs - startMs
}

@Serializable
internal data class RecordingDownloadSelection(
    val range: RecordingDownloadRange? = null,
    val timelineOffsetMs: Long = 0,
    val estimatedBytes: Long? = null,
    val variantIndex: Int? = null,
    val audioIndex: Int? = null,
)

@Serializable
internal data class LiveDownloadSnapshot(
    val url: String,
    val playlists: Map<String, DownloadPlaylist>,
)

@Serializable
internal data class DownloadPlaylist(val url: String, val text: String)
