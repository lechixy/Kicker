package com.lechixy.kick.data.following.repository

import com.lechixy.kick.data.account.support.*
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.following.model.FollowedChannel
import com.lechixy.kick.data.favorites.local.FavoriteUser
import com.lechixy.kick.data.favorites.local.FavoriteUsers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class RailFollowingRepositoryTest {
    @Test fun onlyFiveChannelsFromFirstPageAreFetchedEvenWhenCursorExists() = runBlocking<Unit> {
        var requests = 0
        val auth = signedInAuth { request ->
            requests++
            assertEquals("/api/v2/channels/followed", request.url.encodedPath)
            assertNull(request.url.queryParameter("cursor"))
            objectJson("""{"nextCursor":5,"channels":[
                {"channel_slug":"one"},{"channel_slug":"two"},{"channel_slug":"three"},
                {"channel_slug":"rraenee","user_username":"RRaenee","profile_picture":"avatar.webp","is_live":true,"viewer_count":26233},
                {"channel_slug":"five"},{"channel_slug":"must_not_appear"}]}""")
        }
        val job = SupervisorJob()
        val repo = RailFollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            yield(); repo.refresh()
            val loaded = withTimeout(3000) { repo.state.first { it.loaded } }
            assertEquals(5, loaded.channels.size)
            assertFalse(loaded.channels.any { it.slug == "must_not_appear" })
            assertEquals("avatar.webp", loaded.channels.first { it.slug == "rraenee" }.avatar)
            yield(); assertEquals(1, requests)
            repo.refresh()
            withTimeout(3000) { repo.state.first { !it.loading } }
            assertEquals(2, requests)
            assertEquals(5, repo.state.value.channels.size)
            auth.signOut()
            withTimeout(3000) { repo.state.first { !it.loaded && it.channels.isEmpty() } }
        } finally { job.cancelAndJoin() }
    }
    @Test fun failedRefreshRetainsSnapshotAndRetryStillRequestsOnlyFirstPage() = runBlocking<Unit> {
        var fail = false
        var requests = 0
        val auth = signedInAuth { request ->
            requests++; assertNull(request.url.queryParameter("cursor"))
            if (fail) throw java.io.IOException("offline")
            objectJson("""{"channels":[{"channel_slug":"one"}],"nextCursor":5}""")
        }
        val job = SupervisorJob()
        val repo = RailFollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            yield(); repo.refresh()
            withTimeout(3000) { repo.state.first { it.loaded } }
            fail = true; repo.refresh()
            val error = withTimeout(3000) { repo.state.first { it.failed } }
            assertEquals("one", error.channels.single().slug)
            fail = false; repo.retry()
            withTimeout(3000) { repo.state.first { !it.loading && !it.failed } }
            assertEquals(3, requests)
        } finally { job.cancelAndJoin() }
    }
    @Test fun favoritesRankFirstAndBothGroupsUseDescendingViewers() {
        fun channel(name: String, viewers: Int, live: Boolean = true) = FollowedChannel(name, name, null, live, null, null, viewers, true)
        val channels = listOf(channel("normal-low", 100), channel("favorite-low", 500), channel("normal-high", 50_000),
            channel("favorite-high", 12_000), channel("offline", 0, false), channel("sixth", 999_999))
        val favorites = FavoriteUsers(listOf(FavoriteUser(username = "FAVORITE-HIGH"), FavoriteUser(username = "other", slug = "favorite-low")))
        assertEquals(listOf("favorite-high", "favorite-low", "normal-high", "normal-low", "offline"),
            rankRailChannels(channels, favorites).map { it.slug })
        assertEquals("normal-high", rankRailChannels(channels, FavoriteUsers()).first().slug)
    }
    @Test fun guestDoesNotMakeAuthenticatedRequests() = runBlocking<Unit> {
        var requests = 0
        val auth = AuthRepository(MemorySessionStore()) { requests++; objectJson("{}") }
        val job = SupervisorJob()
        val repo = RailFollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try { yield(); repo.refresh(); yield(); assertEquals(0, requests); assertTrue(repo.state.value.channels.isEmpty()) }
        finally { job.cancelAndJoin() }
    }
}
