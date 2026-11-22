package com.lechixy.kick.data.chat.repository

import android.content.SharedPreferences
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.support.MemorySessionStore
import com.lechixy.kick.data.account.support.objectJson
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SilencedUsersRepositoryTest {
    @Test
    fun guestMutesSurviveLoginAndRemoteMutesDoNotSurviveLogout() = runBlocking {
        val writes = mutableListOf<String>()
        val auth = AuthRepository(MemorySessionStore()) { request ->
            when (request.url.encodedPath) {
                "/api/v1/user" -> objectJson("""{"id":42,"username":"tester"}""")
                "/api/v2/silenced-users" -> when (request.method) {
                    "GET" -> if (request.url.queryParameter("page") == "2") {
                        objectJson("""{"data":[{"id":55,"username":"Other"}],"links":{"next":null}}""")
                    } else {
                        objectJson("""{"data":[{"id":41117798,"username":"Existing"}],
                            "links":{"next":"https://kick.com/api/v2/silenced-users?page=2"}}""")
                    }
                    "POST" -> {
                        val buffer = Buffer()
                        checkNotNull(request.body).writeTo(buffer)
                        assertEquals(objectJson("""{"user_id":82}"""), objectJson(buffer.readUtf8()))
                        writes.add(request.method)
                        objectJson("""{"data":{"id":82,"username":"NewMute"}}""")
                    }
                    else -> error("Unexpected method")
                }
                "/api/v2/silenced-users/41117798" -> {
                    assertEquals("DELETE", request.method)
                    writes.add(request.method)
                    objectJson("{}")
                }
                else -> error("Unexpected request")
            }
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            val preferences = memoryPreferences()
            val repository = SilencedUsersRepository(auth, preferences, scope)
            repository.setMuted(null, "GuestMute", true)
            awaitNames(repository, setOf("guestmute"))
            assertTrue(writes.isEmpty())

            auth.connect("session_token=connected", "session_token=connected", "test-agent")
            awaitNames(repository, setOf("guestmute", "existing", "other"))
            repository.setMuted(82, "NewMute", true)
            repository.setMuted(41117798, "Existing", false)
            awaitNames(repository, setOf("guestmute", "newmute", "other"))
            assertEquals(listOf("POST", "DELETE"), writes)

            auth.signOut()
            awaitNames(repository, setOf("guestmute"))
            awaitNames(SilencedUsersRepository(auth, preferences, scope), setOf("guestmute"))
        } finally {
            scope.cancel()
        }
    }

    private suspend fun awaitNames(repository: SilencedUsersRepository, expected: Set<String>) {
        withTimeout(5_000) { repository.mutedUsers.first { it == expected } }
    }

    private fun memoryPreferences(): SharedPreferences {
        var users: Set<String> = emptySet()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putStringSet" -> {
                    @Suppress("UNCHECKED_CAST")
                    users = (args[1] as Set<String>).toSet()
                    proxy
                }
                "apply" -> null
                else -> error("Unexpected editor method: ${method.name}")
            }
        }
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getStringSet" -> users
                "edit" -> editor
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
    }
}
