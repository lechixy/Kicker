package com.lechixy.kick.integration

import com.lechixy.kick.data.channel.model.toLivestream

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.*
import org.junit.Test
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

import com.lechixy.kick.data.account.support.signedInAuth
import com.lechixy.kick.data.common.model.FollowedLivestream
import com.lechixy.kick.data.common.model.LiveTotalsResponse
import com.lechixy.kick.data.common.model.RecommendedLivestreamResponse
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository

class HomeFeedTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun followedArrayUsesAuthenticatedRequestAndMapsFullStreamCard() = runBlocking {
        val auth = signedInAuth { request ->
            assertEquals("https://kick.com/api/v1/user/livestreams", request.url.toString())
            assertEquals("Bearer access", request.header("Authorization"))
            json.parseToJsonElement("""[{
                "id":134687912,"session_title":"CS MESELESİ","is_live":true,
                "viewer_count":121,"show_view_count":false,"is_mature":false,
                "thumbnail":{"src":"https://images.kick.com/720.webp"},
                "channel":{"id":1743035,"slug":"panky","playback_url":"ignored",
                    "user":{"username":"panky","profilepic":"https://files.kick.com/avatar.webp"}},
                "categories":[{"id":15,"name":"Just Chatting","slug":"just-chatting","tags":["IRL"]}]
            }]""")
        }
        val stream = json.decodeFromJsonElement<List<FollowedLivestream>>(
            auth.requestArray("https://kick.com/api/v1/user/livestreams")
        ).single().toLivestream()
        assertEquals("134687912", stream.id)
        assertEquals("CS MESELESİ", stream.title)
        assertEquals("panky", stream.channel?.slug)
        assertEquals("https://files.kick.com/avatar.webp", stream.channel?.profilePic)
        assertEquals("https://images.kick.com/720.webp", stream.thumbnail?.src)
        assertEquals("Just Chatting", stream.category?.name)
        assertEquals(121, stream.viewerCount)
        assertFalse(stream.showViewCount)
    }

    @Test fun emptyFollowingArrayIsValidAndRequiresLogin() = runBlocking {
        val auth = signedInAuth { json.parseToJsonElement("[]") }
        assertTrue(auth.requestArray("https://kick.com/api/v1/user/livestreams").isEmpty())
        auth.signOut()
        assertTrue(runCatching { auth.requestArray("https://kick.com/api/v1/user/livestreams") }.isFailure)
    }

    @Test fun homeResponsesUseSeparateRecommendationAndTotalsShapes() {
        val recommended = json.decodeFromString<RecommendedLivestreamResponse>(
            """{"livestreams":[{"id":"stream-uuid","title":"Live","viewer_count":50}],"pagination":{"next_cursor":"next"}}"""
        )
        assertEquals("stream-uuid", recommended.livestreams.single().id)
        val totals = json.decodeFromString<LiveTotalsResponse>(
            """{"data":{"total_streamers":7640,"total_viewers":494581},"message":"Success"}"""
        ).data
        assertEquals(7640, totals.totalStreamers)
        assertEquals(494581, totals.totalViewers)
    }

    @Test fun recommendationsRequestFourteenStreamsInSelectedLanguage() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("web.kick.com", request.url.host)
            assertEquals("/api/v1/recommendations/livestreams/list", request.url.encodedPath)
            assertEquals("14", request.url.queryParameter("limit"))
            assertEquals("tr", request.url.queryParameter("language"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"livestreams":[{"id":"live-id","title":"Recommended"}]}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://web.kick.com/").client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(KickApi::class.java)
        assertEquals("live-id", KickRepository(api).getRecommendedLivestreams("tr").single().id)
    }

    @Test fun deniedRecommendationsFallBackToPublicFeedWithSameLanguageAndLimit() = runBlocking {
        for (status in listOf(401, 403)) {
            val paths = mutableListOf<String>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                paths += path
                assertEquals("14", request.url.queryParameter("limit"))
                assertEquals("tr", request.url.queryParameter("language"))
                val fallback = path == "/api/v1/livestreams/featured"
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(if (fallback) 200 else status).message("Response")
                    .body((if (fallback) """{"data":{"livestreams":[{"id":"public-live"}]}}"""
                        else """{"message":"Forbidden"}""").toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://web.kick.com/").client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(KickApi::class.java)
            assertEquals("public-live", KickRepository(api).getRecommendedLivestreams("tr").single().id)
            assertEquals(listOf("/api/v1/recommendations/livestreams/list", "/api/v1/livestreams/featured"), paths)
        }
    }

    @Test fun serverErrorsDoNotTriggerPublicFeedFallback() = runBlocking {
        var calls = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(500).message("Error")
                .body("{}".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://web.kick.com/").client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(KickApi::class.java)
        val failure = runCatching { KickRepository(api).getRecommendedLivestreams("en") }.exceptionOrNull()
        assertEquals(500, (failure as retrofit2.HttpException).code())
        assertEquals(1, calls)
    }
}
