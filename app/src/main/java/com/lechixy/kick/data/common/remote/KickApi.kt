package com.lechixy.kick.data.common.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

import com.lechixy.kick.data.browse.model.SearchResponse
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEventResponse
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.ClipDetailResponse
import com.lechixy.kick.data.channel.model.ClipResponse
import com.lechixy.kick.data.channel.model.VideoDetailResponse
import com.lechixy.kick.data.chat.model.ChatHistoryResponse
import com.lechixy.kick.data.chat.model.RealtimeAuthRequest
import com.lechixy.kick.data.chat.model.RealtimeAuthResponse
import com.lechixy.kick.data.chat.model.RealtimeConnectionRequest
import com.lechixy.kick.data.chat.model.RealtimeConnectionResponse
import com.lechixy.kick.data.common.model.LiveTotalsResponse
import com.lechixy.kick.data.common.model.LivestreamResponse
import com.lechixy.kick.data.common.model.RecommendedLivestreamResponse
import com.lechixy.kick.data.player.model.CurrentViewersResponse
import com.lechixy.kick.data.player.model.LivePlaybackDetails
import com.lechixy.kick.data.player.model.PlaybackRequest
import com.lechixy.kick.data.player.model.PlaybackResponse

interface KickApi {
    @GET("https://web.kick.com/api/v1/channels/{channelId}/clips")
    suspend fun getPopularChannelClips(@Path("channelId") channelId: Long,
        @Query("sort") sort: String = "views", @Query("time") time: String = "all",
        @Query("cursor") cursor: String? = null
    ): com.lechixy.kick.data.channel.model.PopularClipsResponse

    @GET("https://web.kick.com/api/v1/channels/{channelId}/recent-categories")
    suspend fun getRecentChannelCategories(@Path("channelId") channelId: Long
    ): com.lechixy.kick.data.browse.model.RecentCategoriesResponse

    @GET("https://kick.com/api/v1/subcategories")
    suspend fun getCategories(@Query("page") page: Int = 1, @Query("limit") limit: Int = 32): com.lechixy.kick.data.browse.model.CategoriesResponse

    @GET("https://kick.com/emotes/{slug}")
    suspend fun getEmotes(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("https://web.kick.com/api/v1/channels/{channelId}/chat/active-chatters")
    suspend fun getActiveChatters(@Path("channelId") channelId: Long): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v2/channels/{slug}/users/{username}")
    suspend fun getChatter(@Path("slug") slug: String, @Path("username") username: String): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v2/channels/{slug}/info")
    suspend fun getChatterChannel(@Path("slug") slug: String): kotlinx.serialization.json.JsonObject

    @GET("https://web.kick.com/api/v1/gamification/users/{userId}/streak")
    suspend fun getChatterStreak(@Path("userId") userId: Long): kotlinx.serialization.json.JsonObject

    @GET("https://kick.com/api/v1/channels/{slug}/links")
    suspend fun getChannelLinks(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("https://kick.com/api/v2/channels/{slug}/goals")
    suspend fun getChannelGoals(@Path("slug") slug: String): kotlinx.serialization.json.JsonElement

    @GET("api/v1/analytics/leaderboard")
    suspend fun getGiftedSubsLeaderboard(
        @Query("start_time") startTime: String,
        @Query("type") type: String = "gifted_subs",
        @Query("limit") limit: Int = 50
    ): com.lechixy.kick.data.channel.model.GiftedSubsLeaderboardResponse

    @GET("https://web.kick.com/api/v1/recommendations/livestreams/list")
    suspend fun getRecommendedLivestreams(
        @Query("limit") limit: Int = 14,
        @Query("language") language: String
    ): RecommendedLivestreamResponse

    @GET("https://web.kick.com/api/v1/livestreams/live-totals")
    suspend fun getLiveTotals(): LiveTotalsResponse

    @GET("https://kick.com/api/v1/video/{uuid}")
    suspend fun getVideoDetail(@Path("uuid") uuid: String): VideoDetailResponse

    @POST("api/v1/stream/{id}/playback")
    suspend fun getRecordedPlayback(
        @Path("id") id: String,
        @Body request: PlaybackRequest
    ): PlaybackResponse

    @GET("api/v1/channels/{slug}/livestream")
    suspend fun getLivePlaybackDetails(@Path("slug") slug: String): LivePlaybackDetails

    @GET("https://kick.com/api/v2/clips/{id}")
    suspend fun getClip(@Path("id") id: String): ClipResponse

    @GET("https://web.kick.com/api/v1/clips/{id}")
    suspend fun getClipDetail(@Path("id") id: String): ClipDetailResponse

    @GET("api/v1/livestreams/featured")
    suspend fun getLivestreams(
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "viewer_count_desc",
        @Query("language") language: String = "tr"
    ): LivestreamResponse

    @GET
    suspend fun getChannel(
        @Url url: String
    ): ChannelDetail

    @GET("api/v1/channels/{channelId}/event")
    suspend fun getChannelEvent(
        @Path("channelId") channelId: Long
    ): ChannelEventResponse

    @GET
    suspend fun getChannelVideos(
        @Url url: String
    ): List<ChannelVideo>

    @GET
    suspend fun search(
        @Url url: String
    ): SearchResponse

    @GET
    suspend fun getCurrentViewers(
        @Url url: String
    ): List<CurrentViewersResponse>

    @GET("api/v1/chat/{channelId}/history")
    suspend fun getChatHistory(
        @Path("channelId") channelId: Long,
        @Query("cursor") cursor: String? = null
    ): ChatHistoryResponse

    @GET("https://web.kick.com/api/v1/chat/{channelId}/history")
    suspend fun getRecordedChatHistory(
        @Path("channelId") channelId: Long,
        @Query("start_time") startTime: String
    ): ChatHistoryResponse

    @POST("api/v1/realtime/connection")
    suspend fun getRealtimeConnection(
        @Body request: RealtimeConnectionRequest
    ): RealtimeConnectionResponse

    @POST("api/v1/realtime/auth/connection")
    suspend fun getRealtimeAuth(
        @Body request: RealtimeAuthRequest
    ): RealtimeAuthResponse

    @POST("api/v1/realtime/channels/{channelId}/chat/connection")
    suspend fun getChannelChatConnection(
        @Path("channelId") channelId: Long,
        @Body request: RealtimeConnectionRequest
    ): RealtimeConnectionResponse
}
