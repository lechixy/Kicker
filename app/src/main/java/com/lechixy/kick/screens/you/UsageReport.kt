package com.lechixy.kick.screens.you

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.lechixy.kick.R
import com.lechixy.kick.data.common.remote.ConnectionTransport
import com.lechixy.kick.data.player.local.PlaybackContentType
import com.lechixy.kick.data.player.local.PlaybackUsage
import com.lechixy.kick.data.player.local.last24UsageMinutes
import com.lechixy.kick.data.player.local.usageMinute
import com.lechixy.kick.data.player.local.usageMinuteInstant
import java.time.ZonedDateTime

internal enum class UsagePeriod(@StringRes val label: Int) {
    LAST_24_HOURS(R.string.you_usage_last_24_hours),
    WEEK(R.string.you_usage_week),
    MONTH(R.string.you_usage_month),
    ALL(R.string.you_usage_all),
    CUSTOM(R.string.you_usage_custom),
}

internal enum class UsageMetric(@StringRes val title: Int, @DrawableRes val icon: Int) {
    WATCH_TIME(R.string.you_data_usage_watch_time, R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    DATA(R.string.you_data_usage, R.drawable.data_saver_on_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
    ;

    fun value(entry: PlaybackUsage) = when (this) {
        WATCH_TIME -> entry.watchedMs
        DATA -> entry.bytes
    }
}

internal class UsageReport(
    entries: List<PlaybackUsage>,
    val metric: UsageMetric,
    val period: UsagePeriod,
    now: ZonedDateTime,
    val customDays: LongRange? = null,
) {
    private val today = now.toLocalDate().toEpochDay()
    private val recent = last24UsageMinutes(usageMinute(now.toInstant().toEpochMilli()))
    private val days = when (period) {
        UsagePeriod.LAST_24_HOURS -> usageMinuteInstant(recent.first).atZone(now.zone).toLocalDate().toEpochDay()..today
        UsagePeriod.WEEK -> (today - 6)..today
        UsagePeriod.MONTH -> (today - 29)..today
        UsagePeriod.ALL -> null
        UsagePeriod.CUSTOM -> requireNotNull(customDays)
    }
    private val matching = if (period == UsagePeriod.LAST_24_HOURS) {
        entries.mapNotNull { entry ->
            if (entry.recentMinutes.isEmpty()) return@mapNotNull null
            var watched = 0L
            var bytes = 0L
            entry.recentMinutes.forEach { (minute, sample) ->
                if (minute in recent) {
                    watched += sample.watchedMs
                    bytes += sample.bytes
                }
            }
            entry.copy(watchedMs = watched, bytes = bytes).takeIf { metric.value(it) > 0 }
        }
    } else entries.filter { entry ->
        metric.value(entry) > 0 && (days == null || entry.epochDay?.let { it in days } == true)
    }
    val byContentType = matching.groupingBy { it.contentType }.fold(0L) { sum, entry -> sum + metric.value(entry) }
    val total = byContentType.values.sum()
    val byDay = matching.filter { it.epochDay != null }.groupingBy { requireNotNull(it.epochDay) }
        .fold(0L) { sum, entry -> sum + metric.value(entry) }
    val undated = matching.any { it.epochDay == null }
    private val latestDay = byDay.keys.maxOrNull()
    private val earliestDay = byDay.keys.minOrNull()
    val chartEnd = days?.last?.let { maxOf(it, latestDay ?: it) } ?: latestDay ?: today
    val chartStart = days?.first?.let { minOf(it, earliestDay ?: it) } ?: earliestDay ?: chartEnd
    val channels = matching.groupBy { it.channelSlug }.values.map { channel ->
        val info = channel.maxBy { it.epochDay ?: Long.MIN_VALUE }
        UsageChannelTotal(info.channelSlug, info.channelName, channel.firstNotNullOfOrNull { it.channelAvatar },
            channel.sumOf(metric::value), channel.totalsByNetwork(metric))
    }.sortedWith(compareByDescending<UsageChannelTotal> { it.total }.thenBy { it.name })
    val networks = matching.totalsByNetwork(metric)
}

internal data class UsageChannelTotal(
    val slug: String,
    val name: String,
    val avatar: String?,
    val total: Long,
    val networks: Map<ConnectionTransport, Long>,
)

private fun List<PlaybackUsage>.totalsByNetwork(metric: UsageMetric) =
    groupingBy { it.transport }.fold(0L) { sum, entry -> sum + metric.value(entry) }
