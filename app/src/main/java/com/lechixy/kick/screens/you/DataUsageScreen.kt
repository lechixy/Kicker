package com.lechixy.kick.screens.you

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackUsage
import com.lechixy.kick.data.player.local.PlaybackUsageStore
import com.lechixy.kick.util.FormatUtils

@Composable
internal fun DataUsageScreen(onBack: () -> Unit, onChannelClick: (String) -> Unit) {
    val entries by PlaybackUsageStore.entries.collectAsStateWithLifecycle()
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
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.you_data_usage_total), style = MaterialTheme.typography.titleMedium)
                            Text(usageSummary(entries.sumOf { it.watchedMs }, entries.sumOf { it.bytes }))
                            Text(stringResource(R.string.you_data_usage_explanation),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (entries.isEmpty()) {
                    item("empty") { YouSectionMessage(stringResource(R.string.you_data_usage_empty)) }
                } else {
                    item("networks") { SectionHeader(stringResource(R.string.you_data_usage_networks)) }
                    items(networks, key = { "network-${it.transport.name}" }) { UsageRow(it) }
                    item("channels") { SectionHeader(stringResource(R.string.you_data_usage_channels)) }
                    items(channels, key = { "channel-${it.first().channelSlug}" }) { channel ->
                        val info = channel.first()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                SectionHeader(info.channelName, onMore = { onChannelClick(info.channelSlug) })
                                YouSectionMessage(usageSummary(channel.sumOf { it.watchedMs }, channel.sumOf { it.bytes }))
                                channel.sortedBy { it.transport }.forEach { UsageRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(usage: PlaybackUsage) {
    ListItem(supportingContent = { Text(usageSummary(usage.watchedMs, usage.bytes)) }) {
        Text(stringResource(usage.transport.label))
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
