package com.lechixy.kick.data.account.model

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
data class AccountChannelInfo(
    val slug: String,
    val user: AccountChannelUser,
    @SerialName("banner_image") val banner: AccountImage? = null,
    @SerialName("offline_banner_image") val offlineBanner: AccountImage? = null
)
@Serializable
data class AccountImage(val src: String? = null)
@Serializable
data class AccountChannelUser(val username: String, @SerialName("profile_pic") val avatar: String? = null)
@Serializable
data class AccountStreakResponse(val data: AccountStreak)
@Serializable
data class AccountStreak(@SerialName("length_days") val days: Int)
@Serializable
data class AccountLevelResponse(val data: AccountLevel)
@Serializable
data class AccountLevel(
    val level: Int,
    @SerialName("progress_xp") val progressXp: Long,
    @SerialName("xp_to_next_level") val xpToNextLevel: Long,
    val badge: AccountLevelBadge? = null
) {
    val progress: Float get() {
        val earned = progressXp.coerceAtLeast(0).toDouble()
        val remaining = xpToNextLevel.coerceAtLeast(0).toDouble()
        return if (earned + remaining == 0.0) 0f else (earned / (earned + remaining)).toFloat()
    }
    val tierBackground: String get() = "https://static.kick.com/tier/background/level-${((level / 10) * 10).coerceIn(20, 90)}.webp"
}
@Serializable
data class AccountLevelBadge(
    @SerialName("animated_image_url") val animatedImage: String? = null,
    @SerialName("image_url") val image: String? = null
)
enum class AccountDashboardSection { Channel, Level, Streak }
data class AccountDashboardState(
    val channel: AccountChannelInfo? = null,
    val level: AccountLevel? = null,
    val streak: AccountStreak? = null,
    val loading: Boolean = false,
    val failures: Set<AccountDashboardSection> = emptySet()
)
