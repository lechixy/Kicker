package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.player.model.RecordedPlayback

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class ChannelClip(
    val id: String,
    val title: String = "",
    @kotlinx.serialization.json.JsonNames("playback_url", "video_url")
    @SerialName("clip_url") val clipUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    val duration: Double = 0.0,
    @kotlinx.serialization.json.JsonNames("views_count", "view_count")
    val views: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val category: Category? = null
) {
    fun toRecording() = RecordedPlayback(
        id = id, title = title, durationMs = (duration * 1000).toLong(),
        thumbnail = thumbnailUrl, startTime = createdAt, views = views,
        categories = listOfNotNull(category), directUrl = clipUrl
    )
}

@Serializable
data class ClipResponse(val clip: ChannelClip)

