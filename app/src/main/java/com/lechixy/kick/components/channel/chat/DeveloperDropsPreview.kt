package com.lechixy.kick.components.channel.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lechixy.kick.R
import com.lechixy.kick.screens.channel.DropBannerUiModel
import com.lechixy.kick.screens.channel.DropCampaignUiModel
import com.lechixy.kick.screens.channel.DropRewardThumbnailUiModel
import com.lechixy.kick.screens.channel.DropRewardUiModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DeveloperDropsPreview {
    const val campaignId = "developer-drops-preview"
    private val request = MutableStateFlow<Long?>(null)
    val requestId = request.asStateFlow()

    fun show() { request.value = System.nanoTime() }
    fun hide() { request.value = null }
}

@Composable
internal fun developerDropsCampaign(channelName: String, categoryName: String): DropCampaignUiModel {
    val title = stringResource(R.string.developer_drops_sample_title)
    val locale = LocalConfiguration.current.locales[0]
    val date = LocalDate.of(2027, 1, 1).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    val expiration = stringResource(R.string.drops_ends_on, date)
    val rewards = (1..5).map { index ->
        DropRewardUiModel("preview-$index", stringResource(R.string.developer_drops_sample_reward, index),
            "", index / 10f, isUnlocked = false)
    }
    return DropCampaignUiModel(
        id = DeveloperDropsPreview.campaignId,
        title = title,
        organizationLogoUrl = "",
        channelName = channelName,
        categoryName = categoryName,
        rewards = rewards,
        remainingDurationText = stringResource(R.string.drops_unlock_hours, 2, 17),
        endsAtFormatted = expiration,
        banner = DropBannerUiModel(
            id = DeveloperDropsPreview.campaignId,
            title = title,
            rewardCountSubtitle = pluralStringResource(R.plurals.drops_rewards_count, rewards.size, rewards.size),
            organizationLogoUrl = "",
            eligibilityDescription = stringResource(R.string.drops_eligibility, channelName, categoryName),
            rewards = rewards.map { DropRewardThumbnailUiModel(it.id, it.name, it.fullImageUrl, it.progressFraction) },
            endsAtFormatted = expiration,
            overallProgressFraction = 0.1f
        )
    )
}
