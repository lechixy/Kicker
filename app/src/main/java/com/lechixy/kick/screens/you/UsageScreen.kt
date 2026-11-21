package com.lechixy.kick.screens.you

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.settings.SettingsCardSurface
import com.lechixy.kick.components.settings.SettingsContentWidth
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.components.settings.settingsOutlineColor
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackUsageStore

@Composable
internal fun UsageScreen(
    report: UsageReport,
    onPeriodChange: (UsagePeriod) -> Unit,
    hasHistory: Boolean,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
) {
    var clearAll by rememberSaveable { mutableStateOf(false) }
    var channelToClear by rememberSaveable { mutableStateOf<String?>(null) }
    val watch = report.metric == UsageMetric.WATCH_TIME
    Scaffold(topBar = { SettingsTopBar(stringResource(report.metric.title), onBack) }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = SettingsContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item("period") { UsagePeriodButtons(report, onPeriodChange) }
                item("summary") {
                    SettingsCardSurface(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(report.metric.icon), null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(if (watch) R.string.you_usage_total_watch_time else R.string.you_usage_total_transferred),
                                    style = MaterialTheme.typography.titleMedium)
                            }
                            Text(usageValue(report.metric, report.total), style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold)
                            UsageContentBreakdown(report)
                            if (report.byDay.isNotEmpty()) {
                                HorizontalDivider(color = settingsOutlineColor)
                                DailyUsageChart(report)
                            }
                            if (report.total == 0L) {
                                Text(stringResource(R.string.you_usage_period_empty), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (watch && report.total > 0L) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    UsageStat(stringResource(R.string.you_usage_channel_count), report.channels.size.toString(), Modifier.weight(1f))
                                    if (report.byDay.isNotEmpty()) {
                                        UsageStat(stringResource(R.string.you_usage_active_days), report.byDay.size.toString(), Modifier.weight(1f))
                                        UsageStat(stringResource(R.string.you_usage_active_day_average),
                                            usageValue(report.metric, report.byDay.values.sum() / report.byDay.size), Modifier.weight(1f))
                                    }
                                }
                            }
                            if (report.undated) UsageNote(R.string.you_usage_undated)
                            if (!watch) UsageNote(R.string.you_data_usage_explanation)
                        }
                    }
                }
                if (!watch && report.networks.isNotEmpty()) {
                    item("networks") {
                        SectionHeader(stringResource(R.string.you_data_usage_networks),
                            leadingIcon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
                        SettingsCardSurface {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                report.networks.entries.sortedByDescending { it.value }.forEach { (network, bytes) ->
                                    NetworkUsageRow(network, bytes)
                                }
                            }
                        }
                    }
                }
                if (report.channels.isNotEmpty()) {
                    item("channels") {
                        SectionHeader(stringResource(R.string.you_data_usage_channels),
                            leadingIcon = R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
                    }
                    items(report.channels, key = { "usage-${it.slug}" }) { channel ->
                        ChannelUsageCard(channel, report.metric, report.total,
                            onOpen = { onChannelClick(channel.slug) }, onClear = { channelToClear = channel.slug })
                    }
                }
                if (hasHistory) {
                    item("clear") {
                        TextButton(onClick = { clearAll = true }) {
                            Text(stringResource(R.string.you_data_usage_clear_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (clearAll || channelToClear != null) {
        val slug = channelToClear
        val channelName = report.channels.firstOrNull { it.slug == slug }?.name ?: slug
        val dismiss = {
            clearAll = false
            channelToClear = null
        }
        PredictiveAlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.you_data_usage_clear_title)) },
            text = {
                Text(if (channelName == null) stringResource(R.string.you_data_usage_clear_all_message)
                    else stringResource(R.string.you_data_usage_clear_channel_message, channelName))
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    PlaybackUsageStore.clear(slug)
                    dismiss()
                }) { Text(stringResource(R.string.you_data_usage_clear_title), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun ChannelUsageCard(
    channel: UsageChannelTotal,
    metric: UsageMetric,
    total: Long,
    onOpen: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsCardSurface {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.you_data_usage_open_channel), onClick = onOpen)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UsageChannelAvatar(channel.slug, channel.name, channel.avatar, Modifier.size(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(usageValue(metric, channel.total), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ChannelChevron()
                }
                IconButton(onClick = onClear) {
                    Icon(painterResource(R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(R.string.you_data_usage_clear_channel, channel.name))
                }
            }
            val shape = MaterialTheme.shapes.small
            Box(Modifier.fillMaxWidth().height(4.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth((channel.total.toFloat() / total).coerceIn(0f, 1f)).height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, shape))
            }
            if (metric == UsageMetric.DATA) {
                channel.networks.entries.sortedByDescending { it.value }.forEach { (network, bytes) ->
                    NetworkUsageRow(network, bytes)
                }
            }
        }
    }
}

@Composable
private fun NetworkUsageRow(network: ConnectionTransport, bytes: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(network.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(usageValue(UsageMetric.DATA, bytes), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun UsageNote(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@get:StringRes
private val ConnectionTransport.label: Int
    get() = when (this) {
        ConnectionTransport.WIFI -> R.string.you_data_usage_wifi
        ConnectionTransport.MOBILE -> R.string.you_data_usage_mobile
        ConnectionTransport.ETHERNET -> R.string.you_data_usage_ethernet
        ConnectionTransport.VPN -> R.string.you_data_usage_vpn
        ConnectionTransport.OTHER -> R.string.you_data_usage_other
        ConnectionTransport.OFFLINE -> R.string.you_data_usage_offline
    }
