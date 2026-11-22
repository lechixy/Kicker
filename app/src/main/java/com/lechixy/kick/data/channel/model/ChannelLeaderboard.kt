package com.lechixy.kick.data.channel.model

import com.lechixy.kick.data.chat.model.GiftLeaderboardEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardResponse<T>(val data: T)

@Serializable
data class SubscriptionLeaderboardData(
    val gifts: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_enabled") val giftsEnabled: Boolean = false,
    @SerialName("gifts_month") val giftsMonth: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_month_enabled") val giftsMonthEnabled: Boolean = false,
    @SerialName("gifts_week") val giftsWeek: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_week_enabled") val giftsWeekEnabled: Boolean = false,
)

@Serializable
data class KicksLeaderboardData(
    @SerialName("kicks_gifts_lifetime") val lifetime: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("kicks_gifts_lifetime_enabled") val lifetimeEnabled: Boolean = false,
    @SerialName("kicks_gifts_month") val month: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("kicks_gifts_month_enabled") val monthEnabled: Boolean = false,
    @SerialName("kicks_gifts_week") val week: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("kicks_gifts_week_enabled") val weekEnabled: Boolean = false,
)

internal enum class LeaderboardCategory { GIFTERS, KICKERS }
internal enum class LeaderboardPeriod { WEEKLY, MONTHLY, LIFETIME }

internal enum class LeaderboardBoardType(val category: LeaderboardCategory, val period: LeaderboardPeriod) {
    MONTHLY_GIFTERS(LeaderboardCategory.GIFTERS, LeaderboardPeriod.MONTHLY),
    LIFETIME_GIFTERS(LeaderboardCategory.GIFTERS, LeaderboardPeriod.LIFETIME),
    WEEKLY_KICKERS(LeaderboardCategory.KICKERS, LeaderboardPeriod.WEEKLY),
    MONTHLY_KICKERS(LeaderboardCategory.KICKERS, LeaderboardPeriod.MONTHLY),
    LIFETIME_KICKERS(LeaderboardCategory.KICKERS, LeaderboardPeriod.LIFETIME),
    WEEKLY_GIFTERS(LeaderboardCategory.GIFTERS, LeaderboardPeriod.WEEKLY),
}

internal data class LeaderboardBoard(
    val type: LeaderboardBoardType,
    val entries: List<GiftLeaderboardEntry>,
) {
    val targetRank get() = (entries.size + 1).coerceAtMost(10)
    val targetQuantity get() = if (entries.size < 10) 1L else entries[9].quantity.coerceAtMost(Long.MAX_VALUE - 1) + 1
}

internal data class ChannelLeaderboardUiState(
    val channelId: Long? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val boards: List<LeaderboardBoard> = emptyList(),
)

internal fun channelLeaderboardBoards(subs: SubscriptionLeaderboardData?, kicks: KicksLeaderboardData?) =
    LeaderboardBoardType.entries.mapNotNull { type ->
        val entries = when (type) {
            LeaderboardBoardType.MONTHLY_GIFTERS -> subs?.giftsMonth?.takeIf { subs.giftsMonthEnabled }
            LeaderboardBoardType.LIFETIME_GIFTERS -> subs?.gifts?.takeIf { subs.giftsEnabled }
            LeaderboardBoardType.WEEKLY_KICKERS -> kicks?.week?.takeIf { kicks.weekEnabled }
            LeaderboardBoardType.MONTHLY_KICKERS -> kicks?.month?.takeIf { kicks.monthEnabled }
            LeaderboardBoardType.LIFETIME_KICKERS -> kicks?.lifetime?.takeIf { kicks.lifetimeEnabled }
            LeaderboardBoardType.WEEKLY_GIFTERS -> subs?.giftsWeek?.takeIf { subs.giftsWeekEnabled }
        }
        entries?.let { LeaderboardBoard(type, it.distinctBy(GiftLeaderboardEntry::userId).take(10)) }
    }
