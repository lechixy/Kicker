package com.lechixy.kick.data.channel.repository

import com.lechixy.kick.data.channel.model.ClipChannel
import com.lechixy.kick.data.channel.model.ClipCreator
import com.lechixy.kick.data.channel.model.ClipDetail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipDetailCacheTest {
    private val clip = ClipDetail(
        id = "clip_shared",
        title = "Shared clip",
        duration = 20.0,
        playbackUrl = "https://example.com/clip.m3u8",
        startedAt = "",
        createdAt = "",
        channel = ClipChannel(1, "Channel", "channel"),
        creator = ClipCreator(2, "Creator", "creator"),
    )

    @Test fun repeatedSharesReuseTheRequestEvenWhenTheFirstRowLeaves() = runBlocking {
        var requests = 0
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val cache = ClipDetailCache(this, { 0L }) {
            requests++
            started.complete(Unit)
            finish.await()
            clip
        }
        val first = async { cache.get(clip.id, 60_000L, isPreview = true) }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.get(clip.id, 60_000L, isPreview = true) }
        first.cancelAndJoin()
        finish.complete(Unit)
        assertEquals(clip, second.await())
        assertEquals(clip, cache.get(clip.id, 60_000L))
        assertEquals(1, requests)
    }

    @Test fun previewCanReuseMetadataAfterThePlaybackUrlNeedsRefresh() = runBlocking {
        var now = 0L
        var requests = 0
        val cache = ClipDetailCache(this, { now }) {
            clip.copy(playbackUrl = "${clip.playbackUrl}?request=${++requests}")
        }
        val first = cache.get(clip.id, 60_000L)
        now = 60_000L
        assertNull(cache.cached(clip.id, 60_000L))
        assertEquals(first, cache.get(clip.id, 30 * 60_000L))
        assertEquals("${clip.playbackUrl}?request=2", cache.get(clip.id, 60_000L).playbackUrl)
    }
}
