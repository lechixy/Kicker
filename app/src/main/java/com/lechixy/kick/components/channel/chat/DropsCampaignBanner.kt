package com.lechixy.kick.components.channel.chat

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.screens.channel.DropBannerUiModel
import com.lechixy.kick.screens.channel.DropRewardThumbnailUiModel
import com.lechixy.kick.theme.KickDarkColorScheme
import kotlin.time.Duration.Companion.seconds

private val dropsBannerDisplayTime = 20.seconds

private data class VisibleDropBanner(val banner: DropBannerUiModel, val previewId: Long?) {
    val key get() = banner.id to previewId
}

@Composable
fun DropsCampaignBanner(
    banner: DropBannerUiModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
    onViewClick: () -> Unit,
    onRewardClick: (rewardId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    bottomProgress: () -> Float = { banner.overallProgressFraction }
) {
    ChatOverlayCard(modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        val colors = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography
        val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "drops chevron")
        val description = stringResource(if (isExpanded) R.string.settings_expanded else R.string.settings_collapsed)
        val toggleLabel = stringResource(if (isExpanded) R.string.navigation_collapse else R.string.navigation_expand)

        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .semantics { stateDescription = description }
                    .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DropsImage(banner.organizationLogoUrl, Modifier.size(36.dp).clip(CircleShape), ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(banner.title, style = typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(banner.rewardCountSubtitle, style = typography.bodySmall, color = colors.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null, modifier = Modifier.rotate(rotation), tint = colors.onSurface)
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = chatOverlaySizeSpring, expandFrom = Alignment.Top),
                exit = shrinkVertically(animationSpec = chatOverlaySizeSpring, shrinkTowards = Alignment.Top),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.drops_how_to_earn), style = typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = colors.onSurface)
                        Text(banner.eligibilityDescription, style = typography.bodyMedium, color = colors.onSurface)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(banner.rewards, key = { it.id }) { reward ->
                            DropRewardThumbnail(reward, onClick = { onRewardClick(reward.id) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(painterResource(R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                        Text(banner.endsAtFormatted, style = typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = colors.surfaceVariant.copy(alpha = 0.7f),
                                contentColor = colors.onSurfaceVariant
                            )
                        ) {
                            Text(stringResource(R.string.action_dismiss), style = typography.labelLarge,
                                fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onViewClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.action_view), style = typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            DropsBannerProgress(bottomProgress, Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

@Composable
internal fun DropsChatContent(
    banner: DropBannerUiModel?,
    previewId: Long?,
    onDismiss: (String) -> Unit,
    onOpen: (campaignId: String, rewardId: String?) -> Unit,
    chatActive: Boolean = true,
    chatContent: @Composable () -> Unit
) {
    var hiddenCampaigns by remember(chatActive, previewId) { mutableStateOf(emptySet<String>()) }
    val visibleBanner = banner?.takeIf { chatActive && it.id !in hiddenCampaigns }
        ?.let { VisibleDropBanner(it, previewId) }
    val bannerState = remember { MutableTransitionState<VisibleDropBanner?>(null) }
    bannerState.targetState = visibleBanner
    val transition = rememberTransition(bannerState, label = "drops banner")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxBannerHeight = maxHeight * 0.7f
        Column(Modifier.fillMaxSize()) {
            transition.AnimatedContent(
                contentKey = { it?.key },
                transitionSpec = {
                    (chatOverlayFadeIn + scaleIn(animationSpec = chatOverlayScaleSpring, initialScale = 0f))
                        .togetherWith(chatOverlayExit())
                        .using(SizeTransform(clip = false) { _, _ -> chatOverlaySizeSpring })
                }
            ) { entry ->
                if (entry == null) return@AnimatedContent
                val activeBanner = entry.banner
                val active = entry.key == visibleBanner?.key
                var expanded by rememberSaveable { mutableStateOf(true) }
                val countdown = remember { mutableFloatStateOf(1f) }
                LaunchedEffect(active) {
                    if (!active) return@LaunchedEffect
                    val start = withFrameNanos { it }
                    do {
                        val elapsed = withFrameNanos { it - start }
                        countdown.floatValue = (1f - elapsed.toFloat() / dropsBannerDisplayTime.inWholeNanoseconds)
                            .coerceAtLeast(0f)
                    } while (countdown.floatValue > 0f)
                    hiddenCampaigns = hiddenCampaigns + activeBanner.id
                }
                DropsCampaignBanner(
                    banner = activeBanner,
                    isExpanded = expanded,
                    onToggleExpand = { if (active) expanded = !expanded },
                    onDismiss = {
                        if (active) {
                            hiddenCampaigns = hiddenCampaigns + activeBanner.id
                            onDismiss(activeBanner.id)
                        }
                    },
                    onViewClick = {
                        if (active) {
                            hiddenCampaigns = hiddenCampaigns + activeBanner.id
                            onOpen(activeBanner.id, null)
                        }
                    },
                    onRewardClick = { rewardId ->
                        if (active) {
                            hiddenCampaigns = hiddenCampaigns + activeBanner.id
                            onOpen(activeBanner.id, rewardId)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        .heightIn(max = maxBannerHeight),
                    bottomProgress = { countdown.floatValue }
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { chatContent() }
        }
    }
}

@Composable
private fun DropRewardThumbnail(
    reward: DropRewardThumbnailUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f))
    ) {
        Box {
            DropsImage(reward.fullImageUrl, Modifier.fillMaxSize().padding(6.dp), ContentScale.Fit, reward.name)
            DropsBannerProgress({ reward.progressFraction }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.5.dp))
        }
    }
}

@Composable
private fun DropsBannerProgress(progress: () -> Float, modifier: Modifier) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Butt,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

@Preview(name = "Drops Banner Expanded - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360)
@Composable
private fun DropsCampaignBannerExpandedPreview() = DropsCampaignBannerPreview(initiallyExpanded = true)

@Preview(name = "Drops Banner Collapsed - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, widthDp = 360)
@Composable
private fun DropsCampaignBannerCollapsedPreview() = DropsCampaignBannerPreview(initiallyExpanded = false)

@Composable
private fun DropsCampaignBannerPreview(initiallyExpanded: Boolean) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    MaterialTheme(colorScheme = KickDarkColorScheme) {
        DropsCampaignBanner(
            banner = DropBannerUiModel(
                id = "sunset-drop",
                title = "Sunset Drop",
                rewardCountSubtitle = "1 reward to earn",
                organizationLogoUrl = "https://ext.kick.com/drops/organization/kickdrops.png",
                eligibilityDescription = "Get rewards by watching RRaenee while they are live in Grand Theft Auto VI (GTA).",
                rewards = listOf(DropRewardThumbnailUiModel(
                    id = "sunset-badge",
                    name = "SunsetK Badge",
                    fullImageUrl = "https://ext.kick.com/drops/reward-image/01m7ns7wfpjj7fsse92s00dcgf-01m7ns7wg08mcw6tprgcbxff6e.png",
                    progressFraction = 0.06f
                )),
                endsAtFormatted = "Ends on Jan 1, 2027",
                overallProgressFraction = 0.06f
            ),
            isExpanded = expanded,
            onToggleExpand = { expanded = !expanded },
            onDismiss = {},
            onViewClick = {}
        )
    }
}
