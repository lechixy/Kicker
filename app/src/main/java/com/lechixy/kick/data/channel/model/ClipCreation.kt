package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CreateClipRequest(
    @SerialName("video_id") val videoId: String,
    @SerialName("position") val positionSeconds: Int,
)

@Serializable
internal data class ClipDraft(
    val id: String,
    @SerialName("preview_url") val previewUrl: String,
    @SerialName("source_duration") val sourceDurationSeconds: Int,
)

@Serializable
internal data class CreateClipResponse(val data: ClipDraft)

@Serializable
internal data class FinalizeClipRequest(
    val title: String,
    @SerialName("trim_duration") val trimDurationSeconds: Int,
    @SerialName("trim_start") val trimStartSeconds: Int,
)
