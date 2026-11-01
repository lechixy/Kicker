package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Serializable
data class GiftedSubsLeaderboardResponse(
    val data: GiftedSubsLeaderboardData? = null,
    val leaderboards: List<GiftedSubsLeaderboard> = emptyList()
) {
    fun rankFor(channelId: Long): Int? = (data?.leaderboards ?: leaderboards)
        .filter { it.interval == "hourly" && it.metric == "gifted_subs" }
        .flatMap { it.values }
        .filter { it.channelId.toLongOrNull() == channelId && it.rank > 0 }
        .minOfOrNull { it.rank }
}

@Serializable
data class GiftedSubsLeaderboardData(val leaderboards: List<GiftedSubsLeaderboard> = emptyList())

@Serializable
data class GiftedSubsLeaderboard(
    val interval: String,
    val metric: String,
    val values: List<GiftedSubsRank> = emptyList()
)

@Serializable
data class GiftedSubsRank(@SerialName("channel_id") val channelId: String, val rank: Int)

/** Kick's hourly leaderboard selects a UTC clock-hour bucket, as the web client does. */
fun hourlyLeaderboardStart(now: Instant = Instant.now()): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
        .format(now.truncatedTo(ChronoUnit.HOURS))
