package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class DropsCampaignsResponseDto(
    val data: List<DropCampaignDto>,
    val message: String = ""
)

@Serializable
data class DropCampaignDto(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: Int,
    @SerialName("organization_logo_url") val organizationLogoUrl: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    @SerialName("rule_id") val ruleId: Int,
    val rewards: List<DropRewardDto>
)

@Serializable
data class DropRewardDto(
    val id: String,
    val name: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("required_units") val requiredUnits: Int
)

@Serializable
internal data class DropsProgressSummaryResponseDto(val data: List<DropCampaignProgressDto>)

@Serializable
internal data class DropCampaignProgressDto(
    val id: String,
    @SerialName("progress_units") val progressUnits: Long,
    val rewards: List<DropRewardProgressDto>
)

@Serializable
internal data class DropRewardProgressDto(
    val id: String,
    val progress: Float,
    val claimed: Boolean
)

private val dropsCdn = "https://ext.kick.com/".toHttpUrl()

internal fun resolveDropsImageUrl(url: String): String =
    url.takeIf { it.isNotBlank() }?.let { dropsCdn.resolve(it)?.toString() }.orEmpty()
