package com.lechixy.kick.screens.you

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.settings.SettingsCardSurface
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.components.settings.settingsOutlineColor
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackUsage
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import com.lechixy.kick.util.FormatUtils

@Composable
internal fun DataUsageScreen(onBack: () -> Unit, onChannelClick: (String) -> Unit) {
    val entries by PlaybackUsageStore.entries.collectAsStateWithLifecycle()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var channelToClear by rememberSaveable { mutableStateOf<String?>(null) }
    val channels = remember(entries) {
        entries.groupBy { it.channelSlug }.values.sortedByDescending { channel -> channel.sumOf { it.watchedMs } }
    }
    val networks = remember(entries) {
        entries.groupBy { it.transport }.toSortedMap().map { (transport, uses) ->
            uses.first().copy(transport = transport, watchedMs = uses.sumOf { it.watchedMs }, bytes = uses.sumOf { it.bytes })
        }
    }

    Scaffold(topBar = { SettingsTopBar(stringResource(R.string.you_data_usage), onBack) }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item("summary") {
                    SettingsCardSurface {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(painterResource(R.drawable.data_saver_on_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.you_data_usage_total), style = MaterialTheme.typography.titleMedium)
                            }
                            UsageTotals(entries.sumOf { it.watchedMs }, entries.sumOf { it.bytes })
                            Text(stringResource(R.string.you_data_usage_explanation),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entries.isNotEmpty()) {
                                HorizontalDivider(color = settingsOutlineColor)
                                TextButton(onClick = {
                                    channelToClear = null
                                    showClearDialog = true
                                }) {
                                    Icon(painterResource(R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null,
                                        Modifier.padding(end = 8.dp), tint = MaterialTheme.colorScheme.error)
                                    Text(stringResource(R.string.you_data_usage_clear_all), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item("empty") { YouSectionMessage(stringResource(R.string.you_data_usage_empty)) }
                } else {
                    item("networks") {
                        SectionHeader(stringResource(R.string.you_data_usage_networks),
                            leadingIcon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
                    }
                    item("network-totals") {
                        SettingsCardSurface {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                networks.forEachIndexed { index, usage ->
                                    if (index > 0) HorizontalDivider(color = settingsOutlineColor)
                                    UsageRow(usage)
                                }
                            }
                        }
                    }
                    item("channels") {
                        SectionHeader(stringResource(R.string.you_data_usage_channels),
                            leadingIcon = R.drawable.group_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
                    }
                    items(channels, key = { "channel-${it.first().channelSlug}" }) { channel ->
                        val info = channel.first()
                        SettingsCardSurface {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Row(
                                        Modifier.weight(1f)
                                            .clickable(onClickLabel = stringResource(R.string.you_data_usage_open_channel)) {
                                                onChannelClick(info.channelSlug)
                                            }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        UsageChannelAvatar(info.channelSlug, info.channelName,
                                            channel.firstNotNullOfOrNull { it.channelAvatar }, Modifier.size(38.dp))
                                        Text(info.channelName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Icon(painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                            null, Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                                    }
                                    IconButton(onClick = {
                                        channelToClear = info.channelSlug
                                        showClearDialog = true
                                    }) {
                                        Icon(painterResource(R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                            stringResource(R.string.you_data_usage_clear_channel, info.channelName),
                                            tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                UsageTotals(channel.sumOf { it.watchedMs }, channel.sumOf { it.bytes })
                                HorizontalDivider(color = settingsOutlineColor)
                                channel.sortedBy { it.transport }.forEach { UsageRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showClearDialog) {
        val channelName = entries.firstOrNull { it.channelSlug == channelToClear }?.channelName ?: channelToClear
        PredictiveAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.you_data_usage_clear_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(if (channelName == null) stringResource(R.string.you_data_usage_clear_all_message)
                    else stringResource(R.string.you_data_usage_clear_channel_message, channelName),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    PlaybackUsageStore.clear(channelToClear)
                    showClearDialog = false
                }) { Text(stringResource(R.string.you_data_usage_clear_title), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun UsageRow(usage: PlaybackUsage) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(usage.transport.icon), null, Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(usage.transport.label), style = MaterialTheme.typography.labelLarge)
            Text(usageSummary(usage.watchedMs, usage.bytes), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UsageTotals(watchedMs: Long, bytes: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(FormatUtils.formatDuration(watchedMs), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.you_data_usage_watch_time), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Formatter.formatFileSize(LocalContext.current, bytes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.you_data_usage_transferred), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun usageSummary(watchedMs: Long, bytes: Long): String = stringResource(
    R.string.you_data_usage_summary, FormatUtils.formatDuration(watchedMs),
    Formatter.formatFileSize(LocalContext.current, bytes),
)

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

private val ConnectionTransport.icon: Int
    get() = when (this) {
        ConnectionTransport.WIFI -> R.drawable.wifi_24dp_e3e3e3_fill0_wght400_grad0_opsz24
        ConnectionTransport.MOBILE -> R.drawable.signal_cellular_alt_24dp_e3e3e3_fill0_wght400_grad0_opsz24
        ConnectionTransport.ETHERNET -> R.drawable.ic_usage_ethernet
        ConnectionTransport.VPN -> R.drawable.lock_24dp_e3e3e3_fill0_wght400_grad0_opsz24
        ConnectionTransport.OTHER -> R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24
        ConnectionTransport.OFFLINE -> R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24
    }
