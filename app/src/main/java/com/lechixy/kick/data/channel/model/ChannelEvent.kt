package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelEventResponse(
    val data: ChannelEvent? = null,
    val message: String? = null
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
    val languages: List<String> = emptyList()
)

@Serializable
data class ChannelEventAssets(
    val banner: String? = null,
    @SerialName("promo_banner") val promoBanner: String? = null,
    @SerialName("promo_poster") val promoPoster: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class ChannelEventCategory(
    val id: Long? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val name: String? = null,
    val slug: String? = null
)

@Serializable
data class ChannelEventPalette(
    @SerialName("accent_promo_poster") val accentPromoPoster: String? = null
)
