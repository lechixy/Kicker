package com.lechixy.kick.integration

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.GiftedSubsLeaderboardResponse
import com.lechixy.kick.data.channel.model.VideoDetailLivestream
import com.lechixy.kick.data.channel.model.VideoDetailResponse
import com.lechixy.kick.data.channel.model.VideoIdentity
import com.lechixy.kick.data.channel.model.hourlyLeaderboardStart
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.data.chat.model.toRecipientMessages
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.remote.ViewerSession

class ChannelEnhancementsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun api(handler: (String, Array<out Any?>) -> Any): KickApi = Proxy.newProxyInstance(
        KickApi::class.java.classLoader, arrayOf(KickApi::class.java)
    ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as KickApi

    @Test fun hourlyWindowUsesCurrentUtcHourRatherThanRollingPreviousHour() {
        assertEquals("2026-10-28T00:00:00.000Z", hourlyLeaderboardStart(Instant.parse("2026-10-28T00:30:00.123Z")))
        assertEquals("2026-10-28T17:00:00.000Z", hourlyLeaderboardStart(Instant.parse("2026-10-28T17:56:06.912Z")))
        assertEquals("2026-10-28T18:00:00.000Z", hourlyLeaderboardStart(Instant.parse("2026-10-28T18:00:00Z")))
    }

    @Test fun leaderboardMatchesChannelIdAndHourlyMetricInBothResponseShapes() {
        val payload = """{"leaderboards":[{"interval":"hourly","metric":"gifted_subs","values":[{"channel_id":"44781513","rank":23,"count":37}]},{"interval":"daily","metric":"gifted_subs","values":[{"channel_id":"44781513","rank":1}]}]}"""
        for (body in listOf(payload, "{\"data\":$payload}")) {
            val response = json.decodeFromString<GiftedSubsLeaderboardResponse>(body)
            assertEquals(23, response.rankFor(44781513))
            assertNull(response.rankFor(7))
        }
    }

    @Test fun liveVodIsResolvedFromMatchingVideoInsteadOfTreatingVideoUuidAsVodId() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = KickRepository(api { method, args ->
            calls += method
            when (method) {
                "getChannelVideos" -> listOf(ChannelVideo(id = 4, channelId = 9, video = VideoIdentity("unrelated")),
                    ChannelVideo(id = 5, channelId = 9, video = VideoIdentity("live-video-uuid")))
                "getVideoDetail" -> {
                    assertEquals("live-video-uuid", args[0])
                    VideoDetailResponse(VideoDetailLivestream(vodId = "real-vod-id", id = 5))
                }
                else -> error("Unexpected request: $method")
            }
        })
        val resolved = repository.resolveViewerSession(ViewerSession(9, 5, null, "channel"))
        assertEquals("real-vod-id", resolved.vodId)
        assertEquals(listOf("getChannelVideos", "getVideoDetail"), calls)
    }

    @Test fun missingOrChangedLiveVideoNeverUsesOtherBroadcastsVod() = runBlocking {
        for (returnedId in listOf(4L, 5L)) {
            val repository = KickRepository(api { method, _ -> when (method) {
                "getChannelVideos" -> listOf(ChannelVideo(id = returnedId, channelId = 9, video = VideoIdentity("video-uuid")))
                "getVideoDetail" -> VideoDetailResponse(VideoDetailLivestream(vodId = "wrong-vod", id = 6))
                else -> error(method)
            } })
            assertTrue(runCatching { repository.resolveViewerSession(ViewerSession(9, 5, null, "channel")) }.isFailure)
        }
    }

    @Test fun bulkGiftsProduceOneSingleGiftChatMessagePerRecipientWithStableIds() {
        val event = GiftedSubscriptionsEvent(correlationId = "batch", gifterUsername = "USEYNZOL",
            giftedUsernames = listOf("karbonatszz", "second", "", "karbonatszz"), giftedTotal = 2)
        val messages = event.toRecipientMessages()
        assertEquals(2, messages.size)
        assertEquals(listOf("karbonatszz", "second"), messages.map { it.giftedSubscriptions!!.giftedUsernames.single() })
        assertTrue(messages.all { it.giftedSubscriptions!!.giftedTotal == 1 && it.giftedSubscriptions.gifterUsername == "USEYNZOL" })
        assertEquals(messages.map { it.id }, event.toRecipientMessages().map { it.id })
        assertEquals(2, event.giftedTotal)
    }
}
