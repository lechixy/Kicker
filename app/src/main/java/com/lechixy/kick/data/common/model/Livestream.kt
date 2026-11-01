package com.lechixy.kick.data.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.lechixy.kick.data.channel.model.toLivestream

@Serializable
data class LivestreamResponse(
    val data: LivestreamData
)

@Serializable
data class RecommendedLivestreamResponse(val livestreams: List<Livestream> = emptyList())

@Serializable
data class LiveTotalsResponse(val data: LiveTotals)

@Serializable
data class LiveTotals(
    @SerialName("total_streamers") val totalStreamers: Int,
    @SerialName("total_viewers") val totalViewers: Int
)

@Serializable
data class FollowedLivestream(
    val id: Long,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = true,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null,
    val viewers: Int = 0,
    @SerialName("show_view_count") val showViewCount: Boolean = true,
    val language: String? = null,
    val thumbnail: Thumbnail? = null,
    val channel: FollowedLivestreamChannel,
    val categories: List<Category> = emptyList()
) {
    fun toLivestream() = Livestream(
        id = id.toString(), title = sessionTitle, category = categories.firstOrNull(),
        channel = Channel(channel.id, channel.user.profilepic, channel.slug, channel.user.username),
        thumbnail = thumbnail, viewerCount = viewerCount ?: viewers, showViewCount = showViewCount,
        isMature = isMature, language = language, startTime = startTime
    )
}

@Serializable
data class FollowedLivestreamChannel(val id: Long, val slug: String, val user: FollowedLivestreamUser)

@Serializable
data class FollowedLivestreamUser(val username: String, val profilepic: String? = null)

@Serializable
data class LivestreamData(
    val livestreams: List<Livestream> = emptyList()
)

@Serializable
data class Livestream(
    val category: Category? = null,
    val channel: Channel? = null,

    @SerialName("chatroom_id")
    val chatroomId: Long? = null,

    val id: String,

    @SerialName("is_mature")
    val isMature: Boolean = false,

    val language: String? = null,

    @SerialName("show_view_count")
    val showViewCount: Boolean = true,

    @SerialName("start_time")
    val startTime: String? = null,

    val tags: List<String> = emptyList(),

    val thumbnail: Thumbnail? = null,

    val title: String? = null,

    @SerialName("viewer_count")
    val viewerCount: Int = 0
)

@Serializable
data class Category(
    val id: Long,
    val name: String,
    val slug: String
)

@Serializable
data class Channel(
    val id: Long,

    @SerialName("profile_pic")
    val profilePic: String? = null,

    val slug: String,
    val username: String
)

@Serializable
data class Thumbnail(
    val src: String? = null,
    val srcset: String? = null
)
