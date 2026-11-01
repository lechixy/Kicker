package com.lechixy.kick.data.following.repository

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.following.repository.FollowingRepository

class FollowingRepositoryTest {
    @Test fun followMutationUsesServerResultAndFailureDoesNotChangeList() = runBlocking<Unit> {
        var followed = true
        var failMutation = false
        val methods = mutableListOf<String>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user/livestreams") return@signedInAuth Json.parseToJsonElement("[]")
            if (request.url.encodedPath.endsWith("/follow")) {
                methods.add(request.method)
                if (failMutation) throw AccountApiException(403)
                followed = request.method == "POST"
                objectJson("{}")
            } else if (followed) objectJson("""{"channels":[{"channel_slug":"one","is_live":true}]}""")
            else objectJson("""{"channels":[]}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            repo.setFollowing("one", false)
            withTimeout(3000) { repo.state.first { it.loaded && !it.loading && it.channels.isEmpty() } }
            failMutation = true
            assertTrue(runCatching { repo.setFollowing("one", true) }.isFailure)
            assertTrue(repo.state.value.channels.isEmpty())
            assertEquals(listOf("DELETE", "POST"), methods)
        } finally { job.cancelAndJoin() }
    }

    @Test fun cursorPaginationDeduplicatesChannelsAndStopsRepeatedCursor() = runBlocking<Unit> {
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user/livestreams") return@signedInAuth Json.parseToJsonElement("[]")
            assertEquals("/api/v2/channels/followed-page", request.url.encodedPath)
            if (request.url.queryParameter("cursor") == null) objectJson("""{
                "nextCursor":5,"channels":[{"channel_slug":"one","user_username":"One","is_live":true}]}""")
            else objectJson("""{"nextCursor":5,"channels":[
                {"channel_slug":"one","is_live":true},{"channel_slug":"two","is_live":false}]}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            assertEquals("5", repo.state.value.nextCursor)
            repo.loadMore()
            withTimeout(3000) { repo.state.first { !it.loading && it.channels.size == 2 } }
            assertNull(repo.state.value.nextCursor)
            assertTrue(repo.state.value.channels[0].live)
            assertFalse(repo.state.value.channels[1].live)
            assertEquals(1, repo.state.value.pageIndex)
            assertEquals(listOf("two"), repo.state.value.pageChannels.filterNot { it.live }.map { it.slug })
            repo.previousPage()
            assertEquals(0, repo.state.value.pageIndex)
            assertTrue(repo.state.value.pageChannels.all { it.live })
            repo.loadMore()
            assertEquals(1, repo.state.value.pageIndex)
            assertNull(repo.state.value.nextCursor)
            auth.signOut()
            withTimeout(3000) { repo.state.first { it.channels.isEmpty() && !it.loaded } }
        } finally { job.cancelAndJoin() }
    }
    @Test fun endpointErrorIsNotAnEmptyFollowingListAndCanRetry() = runBlocking<Unit> {
        var fail = true
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user/livestreams") Json.parseToJsonElement("[]")
            else if (fail) throw AccountApiException(403)
            else objectJson("""{"channels":[],"nextCursor":null}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.error != null } }
            assertFalse(repo.state.value.loaded)
            assertTrue(auth.session.value.signedIn)
            fail = false; repo.refresh()
            withTimeout(3000) { repo.state.first { it.loaded } }
            assertTrue(repo.state.value.channels.isEmpty())
            assertNull(repo.state.value.error)
        } finally { job.cancelAndJoin() }
    }

    @Test fun liveFeedIsIndependentFromMixedPagesAndPreservesCardFields() = runBlocking<Unit> {
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user/livestreams") Json.parseToJsonElement("""[{
                "id":134687912,"session_title":"CS MESELESİ","is_live":true,"viewer_count":121,
                "show_view_count":false,"thumbnail":{"src":"https://images.kick.com/720.webp"},
                "channel":{"id":1743035,"slug":"panky","user":{"username":"Panky","profilepic":"avatar"}},
                "categories":[{"id":15,"name":"Just Chatting","slug":"just-chatting"}]
            }]""")
            else throw AccountApiException(503)
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            val state = withTimeout(3000) { repo.state.first { it.streamsLoaded && it.error != null } }
            val stream = state.livestreams.single()
            assertEquals("panky", stream.channel?.slug)
            assertEquals("avatar", stream.channel?.profilePic)
            assertEquals("CS MESELESİ", stream.title)
            assertEquals("https://images.kick.com/720.webp", stream.thumbnail?.src)
            assertEquals("Just Chatting", stream.category?.name)
            assertEquals(121, stream.viewerCount)
            assertFalse(stream.showViewCount)
            assertFalse(state.loaded)
            assertNull(state.streamsError)
            auth.signOut()
            withTimeout(3000) { repo.state.first { !it.streamsLoaded && it.livestreams.isEmpty() } }
        } finally { job.cancelAndJoin() }
    }

    @Test fun failedNextPageRetriesSameCursorAndRefreshFailureRetainsNavigation() = runBlocking<Unit> {
        var fail = false
        val cursors = mutableListOf<String?>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user/livestreams") return@signedInAuth Json.parseToJsonElement("[]")
            val cursor = request.url.queryParameter("cursor")
            cursors.add(cursor)
            if (fail) throw AccountApiException(503)
            if (cursor == null) objectJson("""{"channels":[{"channel_slug":"live","is_live":true}],"nextCursor":20}""")
            else objectJson("""{"channels":[{"channel_slug":"offline","is_live":false}],"nextCursor":null}""")
        }
        val job = SupervisorJob()
        val repo = FollowingRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            fail = true
            repo.loadMore()
            withTimeout(3000) { repo.state.first { it.error != null } }
            assertEquals(0, repo.state.value.pageIndex)
            assertEquals("20", repo.state.value.nextCursor)
            fail = false
            repo.retryPage()
            withTimeout(3000) { repo.state.first { it.pageIndex == 1 } }
            assertEquals(listOf(null, "20", "20"), cursors)
            repo.previousPage()
            repo.loadMore()
            assertEquals(3, cursors.size)
            fail = true
            repo.refresh()
            withTimeout(3000) { repo.state.first { it.error != null && !it.loading } }
            repo.previousPage()
            assertEquals(0, repo.state.value.pageIndex)
            assertEquals("live", repo.state.value.pageChannels.single().slug)
        } finally { job.cancelAndJoin() }
    }
}
