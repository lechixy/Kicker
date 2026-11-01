package com.lechixy.kick.data.player.model

import com.lechixy.kick.data.account.remote.string

import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

import com.lechixy.kick.data.channel.model.VideoDetailResponse
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository

class RecordedPlaybackRepositoryTest {
    private val videoUuid = "cb18022c-aa57-49ff-9d2a-843f02e34d52"
    private val vodId = "01a173e5-f220-78ca-9ddd-d29e8cc11441"
    private val playback = PlaybackResponse(PlaybackUrls(vod = "https://example.com/vod.m3u8"))
    // A dedicated detail DTO must ignore legacy livestream fields, including its string thumbnail.
    private val detail = Json { ignoreUnknownKeys = true }.decodeFromString<VideoDetailResponse>(
        """{"uuid":"cb18022c-aa57-49ff-9d2a-843f02e34d52","livestream":{"id":134227291,"thumbnail":"https://example.com/image.jpg","vod_id":"01a173e5-f220-78ca-9ddd-d29e8cc11441"}}"""
    )

    private fun api(handler: (String, Array<out Any?>) -> Any): KickApi = Proxy.newProxyInstance(
        KickApi::class.java.classLoader, arrayOf(KickApi::class.java)
    ) { _, method, args -> handler(method.name, args ?: emptyArray()) } as KickApi

    @Test fun legacyVideoUuidIsResolvedBeforePostingPlayback() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = KickRepository(api { method, args ->
            calls += "$method:${args[0]}"
            when (method) {
                "getVideoDetail" -> detail
                "getRecordedPlayback" -> playback
                else -> error("Unexpected request: $method")
            }
        })
        assertEquals(playback, repository.getRecordedPlayback(videoUuid, videoUuid))
        assertEquals(listOf("getVideoDetail:$videoUuid", "getRecordedPlayback:$vodId"), calls)
    }

    @Test fun directVodIdDoesNotNeedLegacyLookup() = runBlocking {
        val repository = KickRepository(api { method, args ->
            assertEquals("getRecordedPlayback", method)
            assertEquals(vodId, args[0])
            playback
        })
        assertEquals(playback, repository.getRecordedPlayback(vodId))
    }

    @Test fun oldSavedUuidRecoversFrom404WithResolvedVodId() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = KickRepository(api { method, args ->
            calls += "$method:${args[0]}"
            when {
                method == "getVideoDetail" -> detail
                method == "getRecordedPlayback" && args[0] == videoUuid ->
                    throw HttpException(Response.error<PlaybackResponse>(404, "Missing".toResponseBody()))
                method == "getRecordedPlayback" && args[0] == vodId -> playback
                else -> error("Unexpected request: $method")
            }
        })
        assertEquals(playback, repository.getRecordedPlayback(videoUuid))
        assertEquals(listOf("getRecordedPlayback:$videoUuid", "getVideoDetail:$videoUuid", "getRecordedPlayback:$vodId"), calls)
    }

    @Test fun permissionErrorsDoNotTriggerLegacyFallback() = runBlocking {
        var requests = 0
        val repository = KickRepository(api { method, _ ->
            requests++
            assertEquals("getRecordedPlayback", method)
            throw HttpException(Response.error<PlaybackResponse>(403, "Forbidden".toResponseBody()))
        })
        val error = runCatching { repository.getRecordedPlayback(vodId) }.exceptionOrNull()
        assertEquals(403, (error as HttpException).code())
        assertEquals(1, requests)
    }
}
