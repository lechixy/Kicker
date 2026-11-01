package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.animateContentSize

import com.lechixy.kick.components.AppLoadingIndicator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException

import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelReward
import com.lechixy.kick.data.channel.model.formatExactBalance

@Composable
internal fun ChannelPointsDialog(
    channel: ChannelDetail,
    points: Long?,
    kicks: Long?,
    onDismiss: () -> Unit,
    ownChannel: Boolean = false,
    pointsEnabled: Boolean? = null,
    lastPointUpdate: java.time.Instant? = null,
    nextPointAward: java.time.Instant? = null
) {
    var enabled by remember(channel.slug) { mutableStateOf(pointsEnabled) }
    var rewards by remember(channel.slug) { mutableStateOf<List<ChannelReward>?>(null) }
    var failed by remember(channel.slug) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(channel.slug, retry) {
        failed = false
        try {
            enabled = AccountGraph.chat.channelPointSettings(channel).enabled
            rewards = if (enabled == true) AccountGraph.chat.rewards(channel) else emptyList()
        }
        catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            failed = true
        }
    }
    ChannelPointsPanel(channel.user?.username ?: channel.slug, rewards, failed, points, kicks,
        onRetry = { retry++ }, onDismiss = onDismiss, ownChannel = ownChannel, pointsEnabled = enabled,
        lastPointUpdate = lastPointUpdate, nextPointAward = nextPointAward)
}

@Composable
internal fun ChannelPointsPanel(
    channelName: String,
    rewards: List<ChannelReward>?,
    failed: Boolean,
    points: Long?,
    kicks: Long?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    ownChannel: Boolean = false,
    pointsEnabled: Boolean? = null,
    lastPointUpdate: java.time.Instant? = null,
    nextPointAward: java.time.Instant? = null
) {
    var showInfo by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.animateContentSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.channel_points_title), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(painterResource(R.drawable.info_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            stringResource(R.string.channel_points_information), Modifier.size(22.dp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                            stringResource(R.string.account_close), Modifier.size(22.dp))
                    }
                }
                if (showInfo) Text(stringResource(R.string.channel_points_info),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    when {
                        pointsEnabled == false -> com.lechixy.kick.components.ContentState(stringResource(R.string.points_disabled))
                        failed -> Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.channel_rewards_failed), textAlign = TextAlign.Center)
                            TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
                        }
                        rewards == null -> AppLoadingIndicator(Modifier.padding(24.dp))
                        rewards.isEmpty() -> Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.channel_reward_chest), null, Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.channel_rewards_empty), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.channel_rewards_paused, channelName), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> LazyVerticalGrid(columns = GridCells.Adaptive(110.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(rewards, key = { it.id }) { RewardItemCard(it) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BalancePill(points, isKicks = false, unlimited = ownChannel, unavailable = pointsEnabled == false)
                    BalancePill(kicks, isKicks = true)
                    if (!ownChannel && pointsEnabled != false) PointsUpdateTime(lastPointUpdate, nextPointAward, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RewardItemCard(reward: ChannelReward) {
    val background = remember(reward.backgroundColor) {
        runCatching { Color(android.graphics.Color.parseColor(reward.backgroundColor)) }.getOrDefault(Color(0xFF53FC18))
    }
    val status = when {
        !reward.isEnabled -> stringResource(R.string.channel_reward_unavailable)
        reward.isPaused -> stringResource(R.string.channel_reward_paused)
        else -> ""
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { stateDescription = status }) {
        Box(Modifier.size(110.dp).alpha(if (reward.isEnabled && !reward.isPaused) 1f else 0.6f)
            .background(background, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.channel_reward_chest), null, Modifier.size(48.dp).padding(bottom = 10.dp),
                tint = Color(0xFF23272E))
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                shape = RoundedCornerShape(50), color = Color(0xFF2A2F38), contentColor = Color.White) {
                Text(formatExactBalance(reward.cost), Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        Text(reward.title, Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BalanceTooltip(balance: Long?, isKicks: Boolean = false, unlimited: Boolean = false, unavailable: Boolean = false, content: @Composable () -> Unit) {
    val exactBalance = remember(balance) { balance?.let(::formatExactBalance) ?: "—" }
    val label = if (unavailable) stringResource(R.string.points_disabled) else if (unlimited) stringResource(R.string.points_unlimited) else stringResource(if (isKicks) R.string.channel_kicks_exact else R.string.channel_points_exact,
        exactBalance)
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) { content() }
}

@Composable
private fun BalancePill(balance: Long?, isKicks: Boolean, unlimited: Boolean = false, unavailable: Boolean = false) {
    BalanceTooltip(balance, isKicks, unlimited, unavailable) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(if (isKicks) R.drawable.channel_kicks_badge else R.drawable.bubble_chart_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                    stringResource(if (isKicks) R.string.chat_kicks_balance else R.string.chat_channel_points),
                    Modifier.size(22.dp), tint = if (isKicks) Color.Unspecified else MaterialTheme.colorScheme.primary)
                if (unavailable) Icon(painterResource(R.drawable.points_unavailable), stringResource(R.string.points_disabled),
                    Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (unlimited) Icon(painterResource(R.drawable.all_inclusive_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    stringResource(R.string.points_unlimited), Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                else Text(balance?.let(::formatExactBalance) ?: "—", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PointsUpdateTime(lastUpdate: java.time.Instant?, nextAward: java.time.Instant?, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(java.time.Instant.now()) }
    LaunchedEffect(nextAward) {
        while (nextAward != null && now.isBefore(nextAward)) {
            kotlinx.coroutines.delay(1000)
            now = java.time.Instant.now()
        }
    }
    val formatted = remember(lastUpdate) {
        lastUpdate?.let { java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            .withLocale(java.util.Locale.getDefault()).withZone(java.time.ZoneId.systemDefault()).format(it) } ?: "—"
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        Text(stringResource(R.string.points_last_update, formatted), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        nextAward?.let {
            val remaining = java.time.Duration.between(now, it).seconds
            if (remaining > 0) Text(stringResource(R.string.points_next_estimate, (remaining + 59) / 60),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
