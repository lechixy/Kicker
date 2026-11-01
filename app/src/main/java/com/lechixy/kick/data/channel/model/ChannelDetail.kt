package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.lechixy.kick.data.common.model.Channel
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.model.Thumbnail
import com.lechixy.kick.util.getResponsiveImageUrl

@Serializable
data class ChannelDetail(
    val id: Long,
    @SerialName("user_id") val userId: Long? = null,
    val slug: String,
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("vod_enabled") val vodEnabled: Boolean = false,
    @SerialName("subscription_enabled") val subscriptionEnabled: Boolean = false,
    @SerialName("is_affiliate") val isAffiliate: Boolean = false,
    @SerialName("followers_count") val followersCount: String? = null,
    @SerialName("subscriber_badges") val subscriberBadges: List<SubscriberBadge> = emptyList(),
    @SerialName("banner_image") val bannerImage: BannerImage? = null,
    @SerialName("offline_banner_image") val offlineBannerImage: OfflineBannerImage? = null,
    val verified: Boolean = false,
    val livestream: ChannelLivestream? = null,
    val user: ChannelUser? = null,
    val chatroom: ChatroomDetail? = null,
    @SerialName("recent_categories") val recentCategories: List<RecentCategory> = emptyList()
)

@Serializable
data class SubscriberBadge(
    val id: Long,
    @SerialName("channel_id") val channelId: Long? = null,
    val months: Int = 1,
    @SerialName("badge_image") val badgeImage: SubscriberBadgeImage? = null
)

@Serializable
data class SubscriberBadgeImage(
    val srcset: String? = null,
    val src: String? = null
)

fun ChannelDetail.toLivestream(): Livestream {
    return Livestream(
        channel = Channel(
            id = this.id,
            profilePic = this.user?.profilePic,
            slug = this.slug,
            username = this.user?.username ?: ""
        ),
        chatroomId = this.chatroom?.id,
        id = this.id.toString(),
        isMature = this.livestream?.isMature ?: false,
        language = this.livestream?.language,
        showViewCount = this.livestream?.viewerCount != null,
        tags = this.livestream?.tags ?: emptyList(),
        thumbnail = Thumbnail(
            src = this.livestream?.thumbnail?.url ?: this.bannerImage?.url,
            srcset = null
        ),
        title = this.livestream?.sessionTitle,
        viewerCount = this.livestream?.viewerCount ?: 0
    )
}

@Serializable
data class ChannelLivestream(
    val id: Long,
    @SerialName("vod_id") val vodId: String? = null,
    val slug: String? = null,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("start_time") val startTime: String? = null,
    val source: String? = null,
    val duration: Long = 0L,
    val language: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("lang_iso") val langIso: String? = null,
    val thumbnail: BannerImage? = null,
    val tags: List<String> = emptyList(),
    val categories: List<RecentCategory> = emptyList()
)

@kotlinx.serialization.Serializable
data class BannerImage(
    val url: String? = null,
    val src: String? = null,
    val srcset: String? = null,
    val responsive: String? = null
)

@kotlinx.serialization.Serializable
data class OfflineBannerImage(
    val src: String? = null,
    val srcset: String? = null,
    val url: String? = null,
    val responsive: String? = null
)

fun ChannelDetail.getBannerUrl(targetWidthPx: Int = 1080): String? {
    val offlineUrl = getResponsiveImageUrl(
        srcset = offlineBannerImage?.srcset,
        src = offlineBannerImage?.src,
        url = offlineBannerImage?.url,
        responsive = offlineBannerImage?.responsive,
        targetWidthPx = targetWidthPx
    )
    if (!offlineUrl.isNullOrBlank()) return offlineUrl

    return getResponsiveImageUrl(
        srcset = bannerImage?.srcset,
        src = bannerImage?.src,
        url = bannerImage?.url,
        responsive = bannerImage?.responsive,
        targetWidthPx = targetWidthPx
    )
}

@kotlinx.serialization.Serializable
data class ChannelUser(
    val id: Long,
    val username: String,
    val bio: String? = null,
    val instagram: String? = null,
    val twitter: String? = null,
    val youtube: String? = null,
    val discord: String? = null,
    val tiktok: String? = null,
    val facebook: String? = null,
    @SerialName("profile_pic") val profilePic: String? = null
)

@kotlinx.serialization.Serializable
data class ChatroomDetail(
    val id: Long,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("chat_mode") val chatMode: String? = null,
    @SerialName("slow_mode") val slowMode: Boolean = false,
    @SerialName("message_interval") val messageInterval: Int = 0
)

@Serializable
data class RecentCategory(
    val id: Long,
    val name: String,
    val slug: String,
    val tags: List<String> = emptyList()
)
