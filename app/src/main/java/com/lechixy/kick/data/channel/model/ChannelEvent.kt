package com.lechixy.kick.data.channel.model

import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.common.model.Channel
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.model.Thumbnail
import com.lechixy.kick.util.getResponsiveImageUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelEventResponse(
    val data: ChannelEvent? = null,
    val message: String? = null
)

@Serializable
data class EventsBrowseResponse(val data: EventsBrowseData, val message: String? = null)

@Serializable
data class EventsBrowseData(val events: List<ChannelEvent> = emptyList())

@Serializable
data class EventStreamsResponse(val data: EventStreamsData)

@Serializable
data class EventStreamsData(val streams: List<EventStream> = emptyList())

@Serializable
data class EventStream(
    val channel: EventOrganiserChannel,
    val livestream: EventLivestream,
    val user: EventOrganiserUser? = null
) {
    fun toLivestream(category: ChannelEventCategory?): Livestream? {
        val slug = channel.slug?.takeIf(String::isNotBlank) ?: return null
        val id = livestream.id.takeIf(String::isNotBlank) ?: return null
        return Livestream(
            id = id,
            title = livestream.title,
            category = category?.toCategory(),
            channel = Channel(channel.id ?: 0, user?.profilePictureUrl, slug, user?.username ?: slug),
            language = livestream.language,
            isMature = livestream.isMature,
            showViewCount = livestream.showViewCount,
            viewerCount = livestream.viewCount,
            thumbnail = livestream.thumbnail?.let {
                it.copy(src = getResponsiveImageUrl(it.srcset, it.src))
            }
        )
    }
}

@Serializable
data class EventLivestream(
    val id: String,
    val title: String? = null,
    val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("show_view_count") val showViewCount: Boolean = true,
    @SerialName("view_count") val viewCount: Int = 0,
    val thumbnail: Thumbnail? = null
)

@Serializable
data class EventClipsResponse(val data: EventClipsData)

@Serializable
data class EventClipsData(val clips: List<EventClip> = emptyList())

@Serializable
data class EventClip(
    val id: String,
    val title: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("views_count") val views: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChannelEvent(
    val id: String? = null,
    val slug: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("default_language") val defaultLanguage: String? = null,
    @SerialName("is_draft") val isDraft: Boolean = false,
    @SerialName("category_id") val categoryId: Long? = null,
    val category: ChannelEventCategory? = null,
    val assets: ChannelEventAssets? = null,
    val palette: ChannelEventPalette? = null,
    @SerialName("participant_role") val participantRole: String? = null,
    @SerialName("num_livestreams") val numLivestreams: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    val keywords: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val phase: String? = null,
    val organiser: EventOrganiser? = null,
    @SerialName("default_streams") val defaultStreams: List<EventStream> = emptyList(),
    @SerialName("total_participants") val totalParticipants: Int? = null
)

@Serializable
data class EventOrganiser(
    val role: String? = null,
    val user: EventOrganiserUser? = null,
    val channel: EventOrganiserChannel? = null,
    @SerialName("channel_id") val channelId: Long? = null
)

@Serializable
data class EventOrganiserUser(
    val id: Long? = null,
    val username: String? = null,
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null
)

@Serializable
data class EventOrganiserChannel(
    val id: Long? = null,
    val slug: String? = null,
    val verified: Boolean = false
)

@Serializable
data class ChannelEventAssets(
    val banner: String? = null,
    @SerialName("promo_banner") val promoBanner: String? = null,
    @SerialName("promo_poster") val promoPoster: String? = null,
    val thumbnail: String? = null,
    val background: String? = null
)

@Serializable
data class ChannelEventCategory(
    val id: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val name: String? = null,
    val slug: String? = null
) {
    internal fun toCategory(): Category? =
        if (id != null && !name.isNullOrBlank() && !slug.isNullOrBlank()) Category(id, name, slug) else null
}

@Serializable
data class ChannelEventPalette(
    @SerialName("accent_promo_poster") val accentPromoPoster: String? = null
)
