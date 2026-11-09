package com.lechixy.kick.data.common.repository

import retrofit2.HttpException
import okhttp3.HttpUrl.Companion.toHttpUrl

import com.lechixy.kick.data.browse.model.SearchData
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.chat.model.ChatHistoryData
import com.lechixy.kick.data.chat.model.withSelectedBadgesOnly
import com.lechixy.kick.data.chat.remote.KickChatWebSocketClient
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.player.model.CurrentViewersResponse
import com.lechixy.kick.data.player.model.PlaybackRequest
import com.lechixy.kick.data.player.model.PlaybackResponse
import com.lechixy.kick.data.player.model.RecordedPlayback

class KickRepository(
    private val api: KickApi
) {
    suspend fun getCategories(page: Int = 1) = api.getCategories(page = page)

    suspend fun getEmotes(slug: String) = com.lechixy.kick.data.chat.model.parseEmoteCategories(api.getEmotes(slug))
    suspend fun getChannelLinks(slug: String) = com.lechixy.kick.data.channel.model.parseChannelLinks(api.getChannelLinks(slug))
    suspend fun getChannelGoals(slug: String) = com.lechixy.kick.data.channel.model.parseChannelGoals(api.getChannelGoals(slug))

    suspend fun getLastHourGiftedSubsRank(channelId: Long): Int? =
        api.getGiftedSubsLeaderboard(com.lechixy.kick.data.channel.model.hourlyLeaderboardStart()).rankFor(channelId)

    suspend fun resolveViewerSession(session: com.lechixy.kick.data.player.remote.ViewerSession): com.lechixy.kick.data.player.remote.ViewerSession {
        if (!session.vodId.isNullOrBlank()) return session
        val slug = session.channelSlug?.takeIf { it.isNotBlank() } ?: error("Missing viewer channel slug")
        val video = getChannelVideos(slug).firstOrNull {
            it.id == session.livestreamId && (it.channelId == null || it.channelId == session.channelId)
        } ?: error("Live video metadata is not available yet")
        val directId = video.vodId?.takeIf { it.isNotBlank() }
        if (directId != null) return session.copy(vodId = directId)
        val uuid = video.video?.uuid?.takeIf { it.isNotBlank() } ?: video.uuid?.takeIf { it.isNotBlank() }
            ?: error("Live video UUID is not available yet")
        val detail = api.getVideoDetail(uuid).livestream ?: error("Missing live video details")
        check(detail.id == session.livestreamId) { "Live video session changed" }
        val vodId = detail.vodId?.takeIf { it.isNotBlank() } ?: error("Live VOD ID is not available yet")
        return session.copy(vodId = vodId)
    }

    suspend fun getRecordedPlayback(id: String, videoUuid: String? = null): PlaybackResponse =
        resolveRecordedPlayback(id, videoUuid).second

    suspend fun getLiveDvrUrl(slug: String, vodId: String?): String? {
        val id = vodId?.takeIf(String::isNotBlank)
            ?: api.getLivePlaybackDetails(slug).data.livestream?.id
            ?: return null
        return api.getRecordedPlayback(id, PlaybackRequest()).playbackUrl.vod.takeIf(String::isNotBlank)
    }

    private suspend fun resolveRecordedPlayback(id: String, videoUuid: String?): Pair<String, PlaybackResponse> {
        if (videoUuid != null) {
            val vodId = resolveVodId(videoUuid)
            return vodId to api.getRecordedPlayback(vodId, PlaybackRequest())
        }
        return try {
            id to api.getRecordedPlayback(id, PlaybackRequest())
        } catch (error: HttpException) {
            if (error.code() != 404) throw error
            // Sessions saved before video UUIDs and stream IDs were separated may contain a UUID here.
            val vodId = resolveVodId(id)
            if (vodId == id) throw error
            vodId to api.getRecordedPlayback(vodId, PlaybackRequest())
        }
    }

    private suspend fun resolveVodId(videoUuid: String): String {
        return api.getVideoDetail(videoUuid).livestream?.vodId?.takeIf { it.isNotBlank() }
            ?: error("Kayıt için VOD kimliği bulunamadı")
    }

    suspend fun getPopularChannelClips(channelId: Long) = api.getPopularChannelClips(channelId).data.clips
    suspend fun getRecentChannelCategories(channelId: Long) = api.getRecentChannelCategories(channelId).data

    suspend fun getClip(id: String) = api.getClip(id).clip

    suspend fun resolveRecording(recording: RecordedPlayback): RecordedPlayback {
        if (!recording.directUrl.isNullOrBlank()) return recording
        if (recording.id.startsWith("clip_")) {
            val clip = getClip(recording.id)
            check(!clip.clipUrl.isNullOrBlank())
            return recording.copy(
                directUrl = clip.clipUrl,
                title = clip.title.ifBlank { recording.title },
                durationMs = (clip.duration * 1000).toLong().takeIf { it > 0 } ?: recording.durationMs
            )
        }
        val (vodId, response) = resolveRecordedPlayback(
            recording.vodId ?: recording.id,
            recording.videoUuid.takeIf { recording.vodId == null },
        )
        check(response.playbackUrl.vod.isNotBlank())
        return recording.copy(
            directUrl = response.playbackUrl.vod,
            title = response.videoSession.title.ifBlank { recording.title },
            durationMs = (response.videoSession.durationSeconds * 1000).takeIf { it > 0 } ?: recording.durationMs,
            vodId = vodId,
        )
    }

    suspend fun getChannelClips(channelId: Long, sort: String, time: String, cursor: String?) =
        api.getPopularChannelClips(channelId, sort, time, cursor).data

    val chatWebSocketClient: KickChatWebSocketClient by lazy {
        KickChatWebSocketClient(api)
    }

    suspend fun getLivestreams(language: String): List<Livestream> {
        return api
            .getLivestreams(language = language )
            .data
            .livestreams
    }

    suspend fun getRecommendedLivestreams(language: String): List<Livestream> {
        return try {
            api.getRecommendedLivestreams(language = language).livestreams
        } catch (error: HttpException) {
            if (error.code() !in setOf(401, 403)) throw error
            // Kick can reject anonymous recommendations; retain the working public home feed.
            api.getLivestreams(limit = 14, language = language).data.livestreams
        }
    }

    suspend fun getLiveTotals() = api.getLiveTotals().data

    suspend fun getChannel(slug: String): ChannelDetail {
        val fullUrl = "https://kick.com/api/v2/channels/$slug"
        return api.getChannel(fullUrl)
    }

    suspend fun getChannelEvent(channelId: Long): ChannelEvent? {
        return api.getChannelEvent(channelId).data
    }

    suspend fun getChannelVideos(slug: String): List<ChannelVideo> {
        val fullUrl = "https://kick.com/api/v2/channels/$slug/videos"
        return api.getChannelVideos(fullUrl)
    }

    suspend fun search(query: String): SearchData {
        val fullUrl = "https://search.kick.com/api/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("query", query).build().toString()
        return api.search(fullUrl).data
    }

    suspend fun getCurrentViewers(livestreamIds: List<Long>): List<CurrentViewersResponse> {
        if (livestreamIds.isEmpty()) return emptyList()
        val idsQuery = livestreamIds.joinToString("&") { "ids[]=$it" }
        val fullUrl = "https://kick.com/current-viewers?$idsQuery"
        return api.getCurrentViewers(fullUrl)
    }

    suspend fun getCurrentViewers(livestreamId: Long): CurrentViewersResponse? {
        val list = getCurrentViewers(listOf(livestreamId))
        return list.firstOrNull { it.livestreamId == livestreamId } ?: list.firstOrNull()
    }

    suspend fun getChatHistory(channelId: Long, cursor: String? = null): ChatHistoryData? {
        return api.getChatHistory(channelId = channelId, cursor = cursor).data?.withSelectedBadgesOnly()
    }
    suspend fun getRecordedChatHistory(channelId: Long, startTime: String): ChatHistoryData? =
        api.getRecordedChatHistory(channelId, startTime).data?.withSelectedBadgesOnly()

}
