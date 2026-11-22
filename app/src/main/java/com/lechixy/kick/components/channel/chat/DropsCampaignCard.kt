package com.lechixy.kick.components.channel.chat

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveDialog
import com.lechixy.kick.components.dialogEntrance
import com.lechixy.kick.screens.channel.DropCampaignUiModel
import com.lechixy.kick.screens.channel.DropRewardUiModel
import com.lechixy.kick.screens.channel.DropsUiState
import java.text.NumberFormat
import kotlinx.coroutines.launch

@Composable
fun DropsCampaignSection(
    uiState: DropsUiState,
    onRewardClick: (campaignId: String, rewardId: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        DropsUiState.Loading -> Box(modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
        DropsUiState.Empty -> Unit
        DropsUiState.Error -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.drops_load_failed), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
        }
        is DropsUiState.Success -> DropsCampaignCarousel(uiState.campaigns, onRewardClick, modifier)
    }
}

@Composable
private fun DropsCampaignCarousel(
    campaigns: List<DropCampaignUiModel>,
    onRewardClick: (campaignId: String, rewardId: String) -> Unit,
    modifier: Modifier
) {
    if (campaigns.isEmpty()) return
    val pager = rememberPagerState { campaigns.size }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalPager(
            state = pager,
            key = { campaigns[it].id },
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
            userScrollEnabled = campaigns.size > 1
        ) { index ->
            val campaign = campaigns[index]
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                DropCampaignCard(campaign, onRewardClick = { onRewardClick(campaign.id, it) })
            }
        }
        if (campaigns.size > 1) {
            val page = pager.currentPage.coerceIn(campaigns.indices)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    enabled = page > 0, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.arrow_back_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.drops_previous_campaign), modifier = Modifier.size(18.dp))
                }
                Text(stringResource(R.string.drops_campaign_page, page + 1, campaigns.size),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                    enabled = page < campaigns.lastIndex, modifier = Modifier.size(40.dp)) {
                    Icon(painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = stringResource(R.string.drops_next_campaign), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun DropCampaignCard(
    campaign: DropCampaignUiModel,
    onRewardClick: (rewardId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val template = stringResource(R.string.drops_eligibility)
    val eligibility = remember(campaign.channelName, campaign.categoryName, colors.onSurface, template) {
        val emphasis = SpanStyle(fontWeight = FontWeight.Bold, color = colors.onSurface)
        val segments = eventTextSegments(template, eventUser(campaign.channelName, colors.onSurface),
            eventUser(campaign.categoryName, colors.onSurface))
        buildAnnotatedString {
            segments.forEach { segment ->
                val start = length
                append(segment.text)
                if (segment.isBold) addStyle(emphasis, start, length)
            }
        }
    }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        color = colors.surfaceContainer, border = BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DropsImage(campaign.organizationLogoUrl, Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)), ContentScale.Crop)
                Text(campaign.title, style = typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(eligibility, style = typography.bodyMedium, color = colors.onSurfaceVariant)
            // Fixed-width items also scroll on narrow split-screen layouts.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(campaign.rewards, key = { it.id }) { reward ->
                    DropRewardItemCard(reward, onClick = { onRewardClick(reward.id) })
                }
            }
            Text(campaign.remainingDurationText, style = typography.bodySmall, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                Text(campaign.endsAtFormatted, style = typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun DropRewardItemCard(reward: DropRewardUiModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier.width(148.dp), shape = RoundedCornerShape(8.dp),
        color = colors.surfaceContainerHigh) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surfaceContainerLowest)) {
                DropsImage(reward.fullImageUrl, Modifier.fillMaxSize().padding(8.dp), ContentScale.Fit)
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.4f))
            Column(Modifier.fillMaxWidth().background(colors.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(reward.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (reward.isUnlocked) Text(stringResource(R.string.drops_unlocked),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { reward.progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp), color = colors.primary,
                trackColor = colors.surfaceVariant, strokeCap = StrokeCap.Butt, gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
internal fun DropsImage(url: String, modifier: Modifier, contentScale: ContentScale, contentDescription: String? = null) {
    val context = LocalContext.current
    val fallback = painterResource(R.drawable.kick_drops_and_rewards)
    val request = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url.takeIf { it.isNotBlank() })
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }
    AsyncImage(request, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale,
        placeholder = fallback, fallback = fallback, error = fallback)
}

@Composable
internal fun DropsRewardsButton(uiState: DropsUiState, onClick: () -> Unit) {
    val label = stringResource(R.string.drops_and_rewards)
    val colors = MaterialTheme.colorScheme
    ChatControlTooltip(label) {
        IconButton(
            onClick = onClick,
            enabled = uiState is DropsUiState.Success && uiState.campaigns.isNotEmpty(),
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.primary,
                disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.38f)
            )
        ) {
            Icon(painterResource(R.drawable.kick_drops_and_rewards),
                contentDescription = label, modifier = Modifier.size(chatBalanceIconSize))
        }
    }
}

@Composable
internal fun DropsCampaignDialog(
    uiState: DropsUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    initialSelection: Pair<String, String>? = null
) {
    var selection by remember(initialSelection) { mutableStateOf(initialSelection) }
    val selectedReward = selection?.let { (campaignId, rewardId) ->
        (uiState as? DropsUiState.Success)?.campaigns?.firstOrNull { it.id == campaignId }
            ?.rewards?.firstOrNull { it.id == rewardId }
    }
    PredictiveDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp).dialogEntrance(),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.drops_and_rewards), style = MaterialTheme.typography.titleMedium)
                if (uiState == DropsUiState.Empty) {
                    Text(stringResource(R.string.drops_empty), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropsCampaignSection(uiState, onRewardClick = { campaignId, rewardId -> selection = campaignId to rewardId },
                    onRetry = onRetry, modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
    if (selectedReward != null) {
        val formatter = NumberFormat.getPercentInstance(LocalConfiguration.current.locales[0])
        PredictiveDialog(onDismissRequest = { selection = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(selectedReward.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (selectedReward.isUnlocked) stringResource(R.string.drops_unlocked)
                        else stringResource(R.string.drops_progress, formatter.format(selectedReward.progressFraction)),
                        style = MaterialTheme.typography.bodyMedium)
                    if (!selectedReward.isUnlocked) selectedReward.remainingDurationText?.let { remaining ->
                        Text(remaining, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Preview(name = "Sunset Drop - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Sunset Drop - Light", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun DropCampaignCardPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        DropCampaignCard(
            campaign = DropCampaignUiModel(
                id = "01M7NSNCG7V3T2W54EHBWD1J3W",
                title = "Sunset Drop",
                organizationLogoUrl = "https://ext.kick.com/drops/organization/kickdrops.png",
                channelName = "RRaenee",
                categoryName = "Grand Theft Auto VI (GTA)",
                rewards = listOf(DropRewardUiModel(
                    id = "01M7NS7WFPJJ7FSSE92S00DCGF",
                    name = "SunsetK Badge",
                    fullImageUrl = "https://ext.kick.com/drops/reward-image/01m7ns7wfpjj7fsse92s00dcgf-01m7ns7wg08mcw6tprgcbxff6e.png",
                    progressFraction = 0.058f,
                    isUnlocked = false
                )),
                remainingDurationText = "235 hr 17 min to unlock",
                endsAtFormatted = "Ends on Jan 1, 2027"
            ),
            onRewardClick = {}
        )
    }
}
