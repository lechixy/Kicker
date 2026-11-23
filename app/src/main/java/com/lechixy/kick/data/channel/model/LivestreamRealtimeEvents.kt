package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class LivestreamUpdatedEvent(val livestream: LivestreamUpdate)

@Serializable
data class LivestreamUpdate(
    val id: Long,
    @SerialName("channel_id") val channelId: Long,
    val slug: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean? = null,
    @SerialName("start_time") val startTime: String? = null,
    val source: String? = null,
    val duration: Long? = null,
    val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null,
    val tags: List<String>? = null,
    @SerialName("lang_iso") val langIso: String? = null,
    val categories: List<RecentCategory>? = null,
) {
    internal fun applyTo(stream: ChannelLivestream): ChannelLivestream = stream.copy(
        slug = slug ?: stream.slug,
        channelId = channelId,
        createdAt = createdAt ?: stream.createdAt,
        sessionTitle = sessionTitle ?: stream.sessionTitle,
        isLive = isLive ?: stream.isLive,
        startTime = startTime ?: stream.startTime,
        source = source ?: stream.source,
        duration = duration ?: stream.duration,
        language = language ?: stream.language,
        isMature = isMature ?: stream.isMature,
        isMaturityKnown = isMature != null || stream.isMaturityKnown,
        viewerCount = viewerCount?.takeIf { it >= 0 } ?: stream.viewerCount,
        tags = tags ?: stream.tags,
        langIso = langIso ?: stream.langIso,
        categories = categories ?: stream.categories,
    )
}

@Serializable
data class ViewerCountUpdatedEvent(
    @SerialName("channel_id") val channelId: Long,
    @SerialName("viewer_count") val viewerCount: Int,
    @SerialName("measured_at") val measuredAt: Long,
    val seq: Long,
)
