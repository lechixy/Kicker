package com.lechixy.kick.data.channel.repository

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth
import com.lechixy.kick.data.channel.model.CreateClipRequest
import com.lechixy.kick.data.channel.model.FinalizeClipRequest
import com.lechixy.kick.screens.clip.ClipTrim
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipCreationTest {
    @Test
    fun createsAndFinalizesTheSameDraftUsingSeconds() = runBlocking {
        val calls = mutableListOf<String>()
        val auth = signedInAuth { request ->
            assertEquals("POST", request.method)
            assertEquals("web.kick.com", request.url.host)
            assertEquals("Bearer access", request.header("Authorization"))
            val payload = objectJson(Buffer().apply { checkNotNull(request.body).writeTo(this) }.readUtf8())
            val path = request.url.encodedPath
            calls += path
            when (path) {
                "/api/v1/clips" -> {
                    assertEquals(objectJson("""{"video_id":"vod-id","position":22408}"""), payload)
                    objectJson(
                        """{"data":{"id":"clip_test","preview_url":"https://example.com/preview.m3u8","source_duration":180},"message":"success"}"""
                    )
                }
                "/api/v1/clips/clip_test/finalize" -> {
                    assertEquals(objectJson("""{"title":"Highlight","trim_duration":30,"trim_start":150}"""), payload)
                    objectJson(
                        """{
                            "data":{
                                "id":"clip_test","title":"Highlight","duration":30,
                                "playback_url":"https://example.com/published.m3u8",
                                "thumbnail_url":"https://example.com/thumbnail.webp",
                                "created_at":"2026-11-07T10:19:25Z","started_at":"2026-11-05T22:46:37Z",
                                "channel":{"id":1,"slug":"streamer","username":"Streamer"},
                                "creator":{"id":2,"slug":"creator","username":"Creator"},
                                "video":{"id":"vod-id","offset":22560,"title":"Stream"}
                            },
                            "message":"success"
                        }"""
                    )
                }
                else -> error("Unexpected request to $path")
            }
        }

        val draft = ClipRepository.create(auth, CreateClipRequest("vod-id", 22_408))
        assertEquals("https://example.com/preview.m3u8", draft.previewUrl)
        val trim = ClipTrim.initial(draft.sourceDurationSeconds)
        val clip = ClipRepository.finalize(auth, draft.id, FinalizeClipRequest("Highlight", trim.duration, trim.start))
        assertEquals("https://kick.com/streamer/clips/clip_test", clip.shareUrl)
        assertEquals(22_560, clip.video?.offset)
        assertEquals(listOf("/api/v1/clips", "/api/v1/clips/clip_test/finalize"), calls)
    }

    @Test
    fun constrainsTrimMovementAndResizingToTheSource() {
        val trim = ClipTrim.initial(180)
        assertEquals(ClipTrim(0, 30), trim.moveBy(-200, 180))
        assertEquals(ClipTrim(150, 180), trim.moveBy(20, 180))
        assertEquals(ClipTrim(120, 180), trim.resizeStart(-100, 180))
        assertEquals(ClipTrim(170, 180), trim.resizeStart(100, 180))
        assertEquals(ClipTrim(150, 160), trim.resizeEnd(-100, 180))
        assertEquals(ClipTrim(0, 7), ClipTrim.initial(7).resizeEnd(-5, 7))
    }
}
