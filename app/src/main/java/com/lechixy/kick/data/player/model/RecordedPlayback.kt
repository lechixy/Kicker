package com.lechixy.kick.data.player.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.playbackId
import com.lechixy.kick.data.common.model.Category

@Serializable
data class PlaybackRequest(
    @SerialName("video_player") val videoPlayer: PlaybackPlayer = PlaybackPlayer(),
    @SerialName("video_session") val videoSession: JsonObject = JsonObject(emptyMap()),
    @SerialName("user_session") val userSession: PlaybackUser = PlaybackUser()
)
@Serializable
data class PlaybackPlayer(val player: JsonObject = JsonObject(emptyMap()))
@Serializable
data class PlaybackUser(@SerialName("non_personalised_ads") val nonPersonalisedAds: Boolean = true)
@Serializable
data class PlaybackResponse(
    @SerialName("playback_url") val playbackUrl: PlaybackUrls,
    @SerialName("video_session") val videoSession: PlaybackVideoSession = PlaybackVideoSession()
)
@Serializable
data class PlaybackUrls(val vod: String = "")
@Serializable
data class PlaybackVideoSession(
    @SerialName("video_duration") val durationSeconds: Long = 0,
    @SerialName("video_title") val title: String = ""
)
@Serializable
data class RecordedPlayback(
    val id: String,
    val title: String,
    val durationMs: Long,
    val thumbnail: String? = null,
    val startTime: String? = null,
    val views: Long = 0,
    val categories: List<Category> = emptyList(),
    val language: String? = null,
    val directUrl: String? = null,
    // The v2 video UUID must be resolved to livestream.vod_id before stream playback.
    val videoUuid: String? = null,
    val progressId: String = id,
    val vodId: String? = null,
)
fun ChannelVideo.toRecording(): RecordedPlayback? = playbackId?.let { id ->
    RecordedPlayback(
        id = id, title = sessionTitle.orEmpty(), durationMs = duration,
        thumbnail = thumbnail?.src, startTime = startTime ?: createdAt,
        views = views, categories = categories, language = language,
        videoUuid = if (vodId.isNullOrBlank()) id else null,
        progressId = video?.uuid ?: uuid ?: id,
        vodId = vodId?.takeIf(String::isNotBlank),
    )
}

