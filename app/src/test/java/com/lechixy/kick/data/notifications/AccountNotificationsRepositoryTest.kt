package com.lechixy.kick.data.notifications

import com.lechixy.kick.data.account.repository.ChallengesRepository
import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountNotificationsRepositoryTest {
    @Test fun foregroundConnectionIncludesRequiredClientTypeAndProviderCapabilities() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val captured = CompletableDeferred<JsonObject>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath == "/api/v1/realtime/connection") {
                assertEquals("POST", request.method)
                val buffer = Buffer()
                request.body!!.writeTo(buffer)
                captured.complete(objectJson(buffer.readUtf8()))
                // Stop before opening a real websocket or starting retry delays.
                throw CancellationException("Connection request captured")
            }
            objectJson("{}")
        }
        try {
            val repository = AccountNotificationsRepository(auth, ChallengesRepository(auth, scope), scope)
            repository.setForeground(true)
            val body = withTimeout(5_000) { captured.await() }
            assertEquals(objectJson("""{
                "client":{"id":"42","type":"web"},
                "capabilities":{"accepted_providers":[{"provider":"pusher"},{"provider":"centrifugo"}]}
            }"""), body)
        } finally {
            scope.cancel()
        }
    }
}
