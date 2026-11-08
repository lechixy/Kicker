package com.lechixy.kick.integration

import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.browse.model.RecentCategoriesResponse

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.channel.model.PopularClipsData
import com.lechixy.kick.data.channel.model.PopularClipsResponse
import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.model.toRecording

class ChannelHomeDataTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun popularClipsDecodeNestedResponseAndRetainPlaybackMetadata() {
        val response = json.decodeFromString<PopularClipsResponse>("""{
            "data":{"clips":[{"id":"clip_1","title":"Popular clip","duration":170,
            "playback_url":"https://clips.kick.com/playlist.m3u8","views_count":195891,
            "thumbnail_url":"https://clips.kick.com/thumbnail.webp",
            "category":{"id":15,"name":"Just Chatting","slug":"just-chatting"}}],"cursor":"next"},
            "message":"success"} """)
        val recording = response.data.clips.single().toRecording()
        assertEquals("next", response.data.cursor)
        assertEquals("https://clips.kick.com/playlist.m3u8", recording.directUrl)
        assertEquals(170000L, recording.durationMs)
        assertEquals(195891L, recording.views)
        assertEquals("just-chatting", recording.categories.single().slug)
    }

    @Test fun recentCategoryBannerSupportsNewAndExistingFieldNames() {
        for (banner in listOf("""{"src":"poster","srcset":"responsive"}""",
            """{"url":"poster","responsive":"responsive"}""")) {
            val response = json.decodeFromString<RecentCategoriesResponse>("""{"data":[{
                "id":15,"name":"Just Chatting","slug":"just-chatting","tags":["IRL"],
                "viewers":283053,"banner":$banner}],"message":"success"}""")
            val category = response.data.single()
            assertEquals("poster", category.banner?.url)
            assertEquals("responsive", category.banner?.responsive)
            assertEquals(283053, category.viewers)
            assertEquals(listOf("IRL"), category.tags)
        }
    }

    @Test fun channelHomeRequestsUseChannelIdAndAllTimeViewSorting() = runBlocking {
        val calls = mutableListOf<String>()
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, args ->
            calls += method.name
            assertEquals(26239555L, args[0])
            when (method.name) {
                "getPopularChannelClips" -> {
                    assertEquals("views", args[1])
                    assertEquals("all", args[2])
                    PopularClipsResponse(PopularClipsData(listOf(ChannelClip("popular"))))
                }
                "getRecentChannelCategories" -> RecentCategoriesResponse(listOf(BrowseCategory(name = "Category", slug = "category")))
                else -> error(method.name)
            }
        } as KickApi
        val repository = KickRepository(api)
        assertEquals("popular", repository.getPopularChannelClips(26239555).single().id)
        assertEquals("category", repository.getRecentChannelCategories(26239555).single().slug)
        assertEquals(listOf("getPopularChannelClips", "getRecentChannelCategories"), calls)
    }

    @Test fun filteredClipsForwardSortTimeAndCursor() = runBlocking {
        val api = Proxy.newProxyInstance(KickApi::class.java.classLoader, arrayOf(KickApi::class.java)) { _, method, args ->
            assertEquals("getPopularChannelClips", method.name)
            assertEquals(listOf(26239555L, "date", "week", "previous"), args.take(4))
            PopularClipsResponse(PopularClipsData(listOf(ChannelClip("filtered")), cursor = "next"))
        } as KickApi

        val page = KickRepository(api).getChannelClips(26239555L, "date", "week", "previous")
        assertEquals("filtered", page.clips.single().id)
        assertEquals("next", page.cursor)
    }
}
