package com.lechixy.kick.data.browse.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelLivestream
import com.lechixy.kick.data.channel.model.ChannelUser

@kotlinx.serialization.Serializable
data class SearchResponse(
    val data: SearchData,
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class SearchData(
    val channels: List<SearchChannel> = emptyList(),
    val categories: List<SearchCategory> = emptyList(),
    val livestreams: List<SearchLivestream> = emptyList()
)

@kotlinx.serialization.Serializable
data class SearchChannel(
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("profile_picture") val profilePicture: String? = null,
    val slug: String,
    val username: String
)

fun SearchChannel.toChannelDetail(): ChannelDetail {
    return ChannelDetail(
        id = 0,
        livestream = if (isLive) ChannelLivestream(id = 0, slug = slug, isLive = true) else null,
        verified = isVerified,
        user = ChannelUser(
            id = 0,
            profilePic = profilePicture,
            username = username
        ),
        slug = slug
    )
}

@Serializable
data class SearchCategory(
    @SerialName("is_mature") val isMature: Boolean = false,
    val name: String,
    val slug: String,
    val thumbnail: SearchThumbnail? = null
)

@kotlinx.serialization.Serializable
data class SearchLivestream(
    @SerialName("is_mature") val isMature: Boolean = false,
    val language: String? = null,
    val slug: String,
    val thumbnail: SearchThumbnail? = null,
    val title: String? = null
)

@kotlinx.serialization.Serializable
data class SearchThumbnail(
    val src: String? = null,
    val srcset: String? = null
)