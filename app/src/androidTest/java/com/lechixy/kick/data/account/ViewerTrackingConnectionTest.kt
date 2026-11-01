package com.lechixy.kick.data.account

import com.lechixy.kick.data.account.remote.string

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in smoke test: obtains a viewer token and exchanges ping/pong without reporting a watch. */
@RunWith(AndroidJUnit4::class)
class ViewerTrackingConnectionTest {
    @Test fun tokenRequestAndPingPongUseActualAppHeaders() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("viewerNetworkSmoke") == "true")
        val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(15)).build()
        try {
            val token = client.newCall(AccountGraph.auth.viewerRequest("https://websockets.kick.com/viewer/v1/token".toHttpUrl()))
                .execute().use { response ->
                    assertEquals("Viewer token HTTP status", 200, response.code)
                    Json.parseToJsonElement(response.body.string()).jsonObject["data"]!!.jsonObject["token"]!!.jsonPrimitive.content
                }
            assertTrue(token.isNotBlank())
            val pong = CompletableDeferred<Unit>()
            val url = "https://websockets.kick.com/viewer/v1/connect".toHttpUrl().newBuilder().addQueryParameter("token", token).build()
            val socket = client.newWebSocket(AccountGraph.auth.viewerRequest(url), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!webSocket.send("{\"type\":\"ping\"}")) pong.completeExceptionally(AssertionError("Ping send failed"))
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (runCatching { Json.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull() == "pong") pong.complete(Unit)
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Omit exception URLs because the query contains the short-lived viewer token.
                    pong.completeExceptionally(AssertionError("Viewer connection failed (HTTP ${response?.code ?: "none"})"))
                }
            })
            try { withTimeout(15_000) { pong.await() } } finally { socket.cancel() }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
