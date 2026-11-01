package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VideoDetailResponse(val livestream: VideoDetailLivestream? = null)

@Serializable
data class VideoDetailLivestream(@SerialName("vod_id") val vodId: String? = null, val id: Long? = null)

@Serializable
data class PopularClipsResponse(val data: PopularClipsData = PopularClipsData())

@Serializable
data class PopularClipsData(val clips: List<ChannelClip> = emptyList(), val cursor: String? = null)
