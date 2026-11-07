package com.lechixy.kick.data.account.repository

import com.lechixy.kick.data.account.support.MemorySessionStore
import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import okhttp3.HttpUrl.Companion.toHttpUrl

import com.lechixy.kick.data.account.model.SessionStatus
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.model.StoredWebSession

class AuthRepositoryTest {
    @Test fun viewerCredentialsAreScopedToKickViewerEndpointsAndClearedOnLogout() = runBlocking {
        val auth = AuthRepository(MemorySessionStore()) { objectJson(profile) }
        val address = "https://websockets.kick.com/viewer/v1/token".toHttpUrl()
        assertNull(auth.viewerRequest(address).header("Authorization"))
        assertFalse(auth.viewerRequest(address).header("X-CLIENT-TOKEN").isNullOrBlank())
        assertNull(auth.viewerRequest("https://websockets.kick.com/viewer/v1/connect?token=viewer-token".toHttpUrl()).header("X-CLIENT-TOKEN"))
        auth.connect(cookies, cookies, "viewer-agent")
        assertEquals("Bearer web-token", auth.viewerRequest(address).header("Authorization"))
        assertEquals("viewer-agent", auth.viewerRequest(address).header("User-Agent"))
        assertEquals("Bearer web-token", auth.viewerRequest("https://websockets.kick.com/viewer/v1/connect?token=viewer-token".toHttpUrl()).header("Authorization"))
        for (url in listOf("http://websockets.kick.com/viewer/v1/token", "https://websockets.kick.com.evil.test/viewer/v1/token",
            "https://websockets.kick.com/other", "https://websockets.kick.com:8443/viewer/v1/token")) {
            assertTrue(runCatching { auth.viewerRequest(url.toHttpUrl()) }.isFailure)
        }
        auth.signOut()
        assertNull(auth.viewerRequest(address).header("Authorization"))
    }
    private val cookies = "session_token=web-token; XSRF-TOKEN=a%2Bb"
    private val profile = """{"id":42,"username":"tester","profilepic":"https://files.kick.com/avatar.webp","created_at":"2020-01-01T00:00:00Z"}"""

    @Test fun guestCanSignInOnceAndRestoreWithoutAnyServerConfiguration() = runBlocking {
        val store = MemorySessionStore()
        val auth = AuthRepository(store) { request ->
            assertEquals("https://kick.com/api/v1/user", request.url.toString())
            assertEquals("Bearer web-token", request.header("Authorization"))
            objectJson(profile)
        }
        assertFalse(auth.session.value.signedIn)
        val generation = auth.startLogin()
        assertEquals(SessionStatus.Connecting, auth.session.value.status)
        auth.connect(cookies, cookies, "agent", generation)
        assertEquals("tester", auth.session.value.profile?.name)
        assertEquals("https://files.kick.com/avatar.webp", auth.session.value.profile?.avatar)
        val restored = AuthRepository(store)
        assertTrue(restored.session.value.signedIn)
        assertEquals(42L, restored.session.value.profile?.userId)
        restored.signOut()
        assertNull(store.read("website_account"))
        assertFalse(AuthRepository(store).session.value.signedIn)
    }

    @Test fun requestsUseHostSpecificCookiesXsrfAndNeverSendCredentialsElsewhere() = runBlocking {
        var calls = 0
        val auth = signedInAuth { request ->
            calls++
            assertEquals("kick.com", request.url.host)
            objectJson("{}")
        }
        for (url in listOf("https://api.kick.com/public/v1/users", "https://evil.kick.com/", "https://kick.com.evil.test/",
            "http://kick.com/", "https://kick.com:8443/", "https://user@kick.com/")) {
            assertTrue(runCatching { auth.request(url) }.isFailure)
        }
        assertEquals(0, calls)
        val repository = AuthRepository(MemorySessionStore()) { request ->
            calls++
            if (request.url.encodedPath == "/api/v1/user") objectJson(profile) else {
                assertEquals("web.kick.com", request.url.host)
                assertEquals("session_token=host-token; XSRF-TOKEN=host%2Bxsrf", request.header("Cookie"))
                assertEquals("Bearer host-token", request.header("Authorization"))
                assertEquals("host+xsrf", request.header("X-XSRF-TOKEN"))
                objectJson("{}")
            }
        }
        repository.connect(cookies, "session_token=host-token; XSRF-TOKEN=host%2Bxsrf", "agent")
        repository.request("https://web.kick.com/api/test", "POST")
        assertEquals(2, calls)
    }

    @Test fun missingOrMalformedCredentialsAndProfileCannotCreateSession() = runBlocking {
        val store = MemorySessionStore()
        val auth = AuthRepository(store) { objectJson("""{"username":"missing-id"}""") }
        assertTrue(runCatching { auth.connect("session_token=%broken", "", "agent") }.isFailure)
        assertTrue(runCatching { auth.connect(cookies, cookies, "agent") }.isFailure)
        assertFalse(auth.session.value.signedIn)
        assertNull(store.read("website_account"))
        assertEquals("a+b", AuthRepository.cookie("session_token=a+b", "session_token"))
    }

    @Test fun cancelOrLogoutDuringLoginCannotResurrectSession() = runBlocking {
        for (logout in listOf(false, true)) {
            val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val store = MemorySessionStore()
            val auth = AuthRepository(store) { entered.complete(Unit); finish.await(); objectJson(profile) }
            val attempt = auth.startLogin()
            val login = async { runCatching { auth.connect(cookies, cookies, "agent", attempt) } }
            entered.await()
            if (logout) auth.signOut() else auth.cancelLogin()
            finish.complete(Unit)
            assertTrue(login.await().isFailure)
            assertFalse(auth.session.value.signedIn)
            assertNull(store.read("website_account"))
        }
    }

    @Test fun unauthorizedErasesAccountButOtherFailuresPreserveIt() = runBlocking {
        for (code in listOf(401, 403, 404, 429, 500)) {
            val auth = signedInAuth { throw AccountApiException(code) }
            assertTrue(runCatching { auth.request("https://kick.com/api/v2/channels/followed") }.isFailure)
            assertEquals(code != 401, auth.session.value.signedIn)
            if (code == 401) {
                assertEquals(SessionStatus.ReauthenticationRequired, auth.session.value.status)
                assertNull(auth.session.value.profile)
            }
        }
        val offline = signedInAuth { throw java.io.IOException() }
        runCatching { offline.request("https://kick.com/api/v1/user") }
        assertTrue(offline.session.value.signedIn)
    }

    @Test fun unexpectedProfileIdentityExpiresTheSession() = runBlocking {
        val auth = signedInAuth { objectJson(profile.replace("42", "99")) }
        assertTrue(runCatching { auth.loadProfile() }.isFailure)
        assertEquals(SessionStatus.ReauthenticationRequired, auth.session.value.status)
        assertNull(auth.session.value.profile)
    }

    @Test fun channelFollowUnauthorizedExpiresSessionOnlyWhenProfileAlsoRejectsIt() = runBlocking {
        for (profileUnauthorized in listOf(false, true)) {
            val requests = mutableListOf<String>()
            val auth = signedInAuth { request ->
                val path = request.url.encodedPath
                requests.add(path)
                if (path == "/api/v1/user" && !profileUnauthorized) objectJson(profile)
                else throw AccountApiException(401)
            }
            val path = "/api/v2/channels/one/follow"
            val failure = runCatching { auth.requestElement("https://kick.com$path", "POST") }.exceptionOrNull()
            assertEquals(401, (failure as AccountApiException).status)
            assertEquals(listOf(path, "/api/v1/user"), requests)
            assertEquals(!profileUnauthorized, auth.session.value.signedIn)
        }
    }

    @Test fun stale401CannotInvalidateNewAccount() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/user") objectJson(profile.replace("42", "43"))
            else { entered.complete(Unit); finish.await(); throw AccountApiException(401) }
        }
        val old = async { runCatching { auth.request("https://kick.com/api/v2/channels/followed") } }
        entered.await(); auth.signOut(); auth.connect(cookies, cookies, "agent")
        finish.complete(Unit); old.await()
        assertTrue(auth.session.value.signedIn)
        assertEquals(43L, auth.session.value.profile?.userId)
    }

    @Test fun staleProfileCannotReplaceNewAccount() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var first = true
        val auth = signedInAuth {
            if (first) { first = false; entered.complete(Unit); finish.await(); objectJson(profile) }
            else objectJson(profile.replace("42", "43"))
        }
        val old = async { runCatching { auth.loadProfile() } }
        entered.await(); auth.signOut(); auth.connect(cookies, cookies, "agent")
        finish.complete(Unit)
        assertTrue(old.await().isFailure)
        assertEquals(43L, auth.session.value.profile?.userId)
    }

    @Test fun previousVerifiedWebsiteSessionMigratesAndOldSecretsAreRemoved() {
        val store = MemorySessionStore()
        store.write("web_session", Json.encodeToString(StoredWebSession(42, cookies, cookies, "agent")))
        store.write("session", """{"accessToken":"old","refreshToken":"old-r","profile":{"user_id":42,"name":"tester"}}""")
        store.write("pending", "old-attempt")
        val auth = AuthRepository(store)
        assertTrue(auth.session.value.signedIn)
        assertEquals("tester", auth.session.value.profile?.name)
        for (key in listOf("session", "pending", "web_session")) assertNull(store.read(key))
        assertNotNull(store.read("website_account"))
    }

    @Test fun mismatchedOrCorruptLegacySessionIsDiscarded() {
        for (oldProfile in listOf("""{"profile":{"user_id":99,"name":"wrong"}}""", "broken")) {
            val store = MemorySessionStore()
            store.write("web_session", Json.encodeToString(StoredWebSession(42, cookies, cookies, "agent")))
            store.write("session", oldProfile)
            assertFalse(AuthRepository(store).session.value.signedIn)
            assertNull(store.read("web_session")); assertNull(store.read("session"))
        }
    }
}
