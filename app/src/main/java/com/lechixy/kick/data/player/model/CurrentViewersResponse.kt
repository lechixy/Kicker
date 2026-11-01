package com.lechixy.kick.data.player.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentViewersResponse(
    @SerialName("livestream_id") val livestreamId: Long,
    val viewers: Int = 0,
    @SerialName("show_view_count") val showViewCount: Boolean = true
)
