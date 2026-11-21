package com.lechixy.kick.data.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KicksGiftedEvent(
    @SerialName("gift_transaction_id") val giftTransactionId: String,
    val sender: KicksGiftSender,
    val gift: KicksGift,
    @SerialName("created_at") val createdAt: String? = null,
    val message: String = ""
)

@Serializable
data class KicksGiftSender(
    val id: Long,
    val username: String,
    @SerialName("username_color") val usernameColor: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class KicksGift(
    @SerialName("gift_id") val giftId: String,
    val name: String,
    val amount: Int,
    val type: String? = null,
    val tier: String? = null,
    @SerialName("character_limit") val characterLimit: Int = 0,
    @SerialName("pinned_time") val pinnedTime: Int = 0
)

@Serializable
data class GiftLeaderboardEntry(
    @SerialName("user_id") val userId: Long,
    val username: String,
    val quantity: Long
)

@Serializable
data class GiftsLeaderboardUpdatedEvent(
    val leaderboard: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("weekly_leaderboard") val weeklyLeaderboard: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("monthly_leaderboard") val monthlyLeaderboard: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifter_id") val gifterId: Long? = null,
    @SerialName("gifter_username") val gifterUsername: String? = null,
    @SerialName("gifted_quantity") val giftedQuantity: Int? = null
)

@Serializable
data class KicksLeaderboardUpdatedEvent(
    @SerialName("gifts_lifetime") val giftsLifetime: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_lifetime_enabled") val giftsLifetimeEnabled: Boolean = false,
    @SerialName("gifts_week") val giftsWeek: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_week_enabled") val giftsWeekEnabled: Boolean = false,
    @SerialName("gifts_month") val giftsMonth: List<GiftLeaderboardEntry> = emptyList(),
    @SerialName("gifts_month_enabled") val giftsMonthEnabled: Boolean = false
)
