package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelPointsSettings(
    @SerialName("channel_points_enabled") val enabled: Boolean,
    @SerialName("web_watch_event_interval_seconds") val webIntervalSeconds: Long? = null,
    @SerialName("mobile_watch_event_interval_seconds") val mobileIntervalSeconds: Long? = null,
    @SerialName("tv_watch_event_interval_seconds") val tvIntervalSeconds: Long? = null
)
