package com.lechixy.kick.data.channel.repository

import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.channel.model.DropCampaignProgressDto
import com.lechixy.kick.data.channel.model.DropsProgressSummaryResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

private val dropsProgressJson = Json { ignoreUnknownKeys = true }

internal suspend fun AuthRepository.getDropsProgressSummary(
    campaignIds: List<String>
): Map<String, DropCampaignProgressDto> {
    val url = "https://web.kick.com/api/v1/drops/progress/summary".toHttpUrl().newBuilder()
    campaignIds.forEach { url.addQueryParameter("campaign_id", it) }
    val response = dropsProgressJson.decodeFromJsonElement<DropsProgressSummaryResponseDto>(
        request(url.build().toString())
    )
    return response.data.associateBy { it.id }
}
