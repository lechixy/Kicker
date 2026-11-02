package com.lechixy.kick.data.player.remote

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.data.common.remote.KickNetwork

data class ViewerSession(val channelId: Long, val livestreamId: Long, val vodId: String?, val channelSlug: String? = null)

data class ViewerTrackingState(
    val status: String = "idle",
    val session: ViewerSession? = null,
    val connectedAt: Long? = null,
    val lastPing: Long? = null,
    val lastPong: Long? = null,
    val lastHandshake: Long? = null,
    val lastWatchEvent: Long? = null,
    val latencyMs: Long? = null,
    val reconnects: Int = 0,
    val error: String? = null
)

/** Diagnostics deliberately exclude viewer tokens and full request URLs. */
object ViewerTrackingDiagnostics {
    internal val mutableState = MutableStateFlow(ViewerTrackingState())
    val state = mutableState.asStateFlow()
}

object ViewerTrackingProtocol {
    const val HANDSHAKE_MS = 15_000L
    const val PING_MS = 30_000L
    const val WATCH_MS = 120_000L
    const val PONG_TIMEOUT_MS = 10_000L

    fun handshake(session: ViewerSession) = buildJsonObject {
        put("type", "channel_handshake")
        putJsonObject("data") { putJsonObject("message") { put("channelId", session.channelId.toString()) } }
    }.toString()

    fun watch(session: ViewerSession) = buildJsonObject {
        require(!session.vodId.isNullOrBlank()) { "Viewer watch event requires a resolved VOD ID" }
        put("type", "user_event")
        putJsonObject("data") { putJsonObject("message") {
            put("name", "tracking.user.watch.livestream")
            put("channel_id", session.channelId)
            put("livestream_id", session.livestreamId)
            session.vodId.takeIf { it.isNotBlank() }?.let { put("vod_id", it) }
        } }
    }.toString()
}

/** Owned by PlaybackService, so mini player, PiP and background audio share one session. */
class ViewerTrackingClient(private val scope: CoroutineScope) {
    private val client = OkHttpClient.Builder().build()
    private var session: ViewerSession? = null
    private var job: Job? = null
    private val state = ViewerTrackingDiagnostics.mutableState

    fun setSession(value: ViewerSession?) {
        if (value == session) return
        job?.cancel()
        session = value
        state.value = ViewerTrackingState(session = value)
        job = value?.let { target -> scope.launch {
            var failures = 0
            var resolved = target
            while (isActive) {
                try {
                    if (resolved.vodId.isNullOrBlank()) {
                        state.update { it.copy(status = "metadata", error = null) }
                        resolved = com.lechixy.kick.data.common.repository.KickRepository(KickNetwork.api).resolveViewerSession(resolved)
                        state.update { it.copy(session = resolved) }
                    }
                    state.update { it.copy(status = "token", error = null) }
                    connect(resolved, fetchToken())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    failures++
                    state.update { it.copy(status = "retrying", reconnects = it.reconnects + 1,
                        error = failure.message ?: "Viewer connection failed") }
                    delay(((1_000L shl (failures - 1).coerceAtMost(5)).coerceAtMost(30_000L)).milliseconds)
                }
            }
        } }
    }

    fun refreshAuthentication() {
        val current = session ?: return
        setSession(null)
        setSession(current)
    }

    private fun request(url: String) = com.lechixy.kick.data.account.AccountGraph.auth.viewerRequest(url.toHttpUrl())

    private suspend fun fetchToken(): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request("https://websockets.kick.com/viewer/v1/token"))
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (continuation.isActive) continuation.resumeWithException(java.io.IOException("Viewer token request failed"))
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "Viewer token HTTP ${it.code}" }
                    val root = Json.parseToJsonElement(it.body.string()).jsonObject
                    root["data"]?.jsonObject?.get("token")?.jsonPrimitive?.content
                        ?.takeIf(String::isNotBlank) ?: error("Missing viewer token")
                } }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    private suspend fun connect(target: ViewerSession, token: String): Unit = coroutineScope {
        val ended = CompletableDeferred<Unit>()
        val attemptScope = this
        state.update { it.copy(status = "connecting", lastPing = null, lastPong = null, latencyMs = null) }
        var awaitingPongAt: Long? = null
        val url = "https://websockets.kick.com/viewer/v1/connect".toHttpUrl().newBuilder()
            .addQueryParameter("token", token).build()
        val socket = client.newWebSocket(request(url.toString()), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attemptScope.launch {
                    state.update { it.copy(status = "connected", connectedAt = System.currentTimeMillis(), error = null) }
                    fun send(frame: String) { check(webSocket.send(frame)) { "Viewer send failed" } }
                    send(ViewerTrackingProtocol.watch(target))
                    send(ViewerTrackingProtocol.handshake(target))
                    state.update { it.copy(lastWatchEvent = System.currentTimeMillis(), lastHandshake = System.currentTimeMillis()) }
                    launch {
                        while (isActive) {
                            delay(ViewerTrackingProtocol.HANDSHAKE_MS.milliseconds)
                            send(ViewerTrackingProtocol.handshake(target))
                            state.update { it.copy(lastHandshake = System.currentTimeMillis()) }
                        }
                    }
                    launch {
                        while (isActive) {
                            delay(ViewerTrackingProtocol.WATCH_MS.milliseconds)
                            send(ViewerTrackingProtocol.watch(target))
                            state.update { it.copy(lastWatchEvent = System.currentTimeMillis()) }
                        }
                    }
                    launch {
                        while (isActive) {
                            delay(ViewerTrackingProtocol.PING_MS.milliseconds)
                            awaitingPongAt = android.os.SystemClock.elapsedRealtime()
                            send("{\"type\":\"ping\"}")
                            state.update { it.copy(lastPing = System.currentTimeMillis()) }
                            launch {
                                delay(ViewerTrackingProtocol.PONG_TIMEOUT_MS.milliseconds)
                                if (awaitingPongAt != null) ended.completeExceptionally(java.io.IOException("Viewer pong timeout"))
                            }
                        }
                    }
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (runCatching { Json.parseToJsonElement(text).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull() == "pong") {
                    attemptScope.launch {
                        val sent = awaitingPongAt
                        awaitingPongAt = null
                        state.update { it.copy(lastPong = System.currentTimeMillis(),
                            latencyMs = sent?.let { android.os.SystemClock.elapsedRealtime() - it }) }
                    }
                }
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, null)
                ended.completeExceptionally(java.io.IOException("Viewer closed ($code)"))
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ended.completeExceptionally(java.io.IOException("Viewer closed ($code)"))
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                ended.completeExceptionally(java.io.IOException("Viewer websocket failed${response?.let { " (HTTP ${it.code})" }.orEmpty()}"))
            }
        })
        try { ended.await() } finally {
            socket.cancel()
            coroutineContext.cancelChildren()
        }
    }

    fun close() { setSession(null); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
}
