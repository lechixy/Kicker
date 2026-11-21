package com.lechixy.kick.screens.you

import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackContentType
import com.lechixy.kick.util.FormatUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun UsagePeriodButtons(report: UsageReport, onSelect: (UsagePeriod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UsagePeriod.entries.forEach { option ->
                FilterChip(selected = report.period == option, onClick = { onSelect(option) },
                    label = { Text(stringResource(option.label)) })
            }
        }
        if (report.period == UsagePeriod.CUSTOM) {
            val range = requireNotNull(report.customDays)
            val formatter = rememberUsageDateFormat()
            Text(stringResource(R.string.you_usage_range_value,
                LocalDate.ofEpochDay(range.first).format(formatter), LocalDate.ofEpochDay(range.last).format(formatter)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun UsageOverviewCard(report: UsageReport, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val watch = report.metric == UsageMetric.WATCH_TIME
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (watch) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (watch) colors.onPrimaryContainer else colors.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(report.metric.icon), null, Modifier.size(22.dp))
                Text(stringResource(report.metric.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                ChannelChevron()
            }
            if (report.total > 0) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.you_usage_total), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(usageValue(report.metric, report.total), style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                }
                UsageContentBreakdown(report)
                HorizontalDivider(color = colors.onSurface.copy(alpha = 0.14f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (watch) {
                        UsageStat(stringResource(R.string.you_usage_top_channel), report.channels.first().name, Modifier.weight(1f))
                        if (report.byDay.isNotEmpty()) {
                            UsageStat(stringResource(R.string.you_usage_active_days), report.byDay.size.toString(), Modifier.weight(1f))
                        }
                    } else {
                        UsageStat(stringResource(R.string.you_data_usage_wifi),
                            usageValue(report.metric, report.networks[ConnectionTransport.WIFI] ?: 0L), Modifier.weight(1f))
                        UsageStat(stringResource(R.string.you_data_usage_mobile),
                            usageValue(report.metric, report.networks[ConnectionTransport.MOBILE] ?: 0L), Modifier.weight(1f))
                    }
                }
            } else {
                Text(stringResource(R.string.you_usage_period_empty), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun UsageContentBreakdown(report: UsageReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UsageContentRow(report, PlaybackContentType.LIVE, R.string.you_usage_live)
        UsageContentRow(report, PlaybackContentType.RECORDING, R.string.you_usage_vod_clips)
        if ((report.byContentType[PlaybackContentType.UNCLASSIFIED] ?: 0L) > 0) {
            UsageContentRow(report, PlaybackContentType.UNCLASSIFIED, R.string.you_usage_unknown_content)
        }
    }
}

@Composable
private fun UsageContentRow(report: UsageReport, type: PlaybackContentType, @StringRes label: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(usageValue(report.metric, report.byContentType[type] ?: 0L), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun UsageStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DailyUsageChart(report: UsageReport) {
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = rememberUsageDateFormat()
    val dayFormat = remember(locale) { DateTimeFormatter.ofPattern("d", locale) }
    val peak = report.byDay.values.maxOrNull() ?: return
    val dayCount = (report.chartEnd - report.chartStart + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    var selectedDay by rememberSaveable(report.chartStart, report.chartEnd) { mutableLongStateOf(report.chartEnd) }
    val selectedDate = LocalDate.ofEpochDay(selectedDay).format(dateFormat)
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.you_usage_daily), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.you_usage_date_value, selectedDate, usageValue(report.metric, report.byDay[selectedDay] ?: 0L)),
            style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dayWidth = (maxWidth / minOf(7, dayCount)).coerceIn(42.dp, 64.dp)
            LazyRow(Modifier.fillMaxWidth().selectableGroup(), reverseLayout = true) {
                items(dayCount, key = { report.chartEnd - it }) { index ->
                    val day = report.chartEnd - index
                    val date = LocalDate.ofEpochDay(day)
                    val value = report.byDay[day] ?: 0L
                    val selected = day == selectedDay
                    val description = stringResource(R.string.you_usage_date_value, date.format(dateFormat), usageValue(report.metric, value))
                    Column(
                        Modifier.width(dayWidth).clip(MaterialTheme.shapes.small)
                            .selectable(selected, role = Role.Button) { selectedDay = day }
                            .semantics { contentDescription = description }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.height(80.dp).width(14.dp).background(colors.surfaceContainerHighest, BarShape),
                            contentAlignment = Alignment.BottomCenter) {
                            if (value > 0) {
                                Box(Modifier.width(14.dp).height((80.dp * (value.toFloat() / peak)).coerceAtLeast(3.dp))
                                    .background(if (selected) colors.primary else colors.primary.copy(alpha = 0.58f), BarShape))
                            }
                        }
                        Text(date.format(dayFormat), style = MaterialTheme.typography.labelSmall,
                            color = if (selected) colors.primary else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val BarShape = RoundedCornerShape(6.dp)

@Composable
private fun rememberUsageDateFormat(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}

@Composable
internal fun usageValue(metric: UsageMetric, value: Long): String = when (metric) {
    UsageMetric.WATCH_TIME -> FormatUtils.formatDuration(value)
    UsageMetric.DATA -> Formatter.formatFileSize(LocalContext.current, value)
}

@Composable
internal fun ChannelChevron() {
    Icon(painterResource(R.drawable.arrow_forward_ios_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        null, Modifier.size(14.dp))
}
