package com.lechixy.kick.data.chat.model

import com.lechixy.kick.data.common.model.Channel
import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.model.Thumbnail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelRaid(
    val channel: RaidSource,
    val slug: String,
    val hosted: RaidTarget
) {
    val targetSlug: String get() = hosted.slug.ifBlank { slug }
    fun toLivestream() = Livestream(
        id = hosted.id.toString(),
        channel = Channel(hosted.id, hosted.profilePic, targetSlug, hosted.username),
        title = hosted.username,
        viewerCount = hosted.viewersCount,
        thumbnail = hosted.previewThumbnail,
        category = hosted.category?.let { Category(0, it, "") }
    )
}

@Serializable
data class RaidSource(val id: Long)

@Serializable
data class RaidTarget(
    val id: Long,
    val username: String,
    val slug: String = "",
    @SerialName("viewers_count") val viewersCount: Int = 0,
    @SerialName("profile_pic") val profilePic: String? = null,
    val category: String? = null,
    @SerialName("preview_thumbnail") val previewThumbnail: Thumbnail? = null
)
