package com.lechixy.kick.data.notifications

import android.os.SystemClock
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.repository.ChallengesRepository
import com.lechixy.kick.data.chat.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/** One foreground account feed, independent of the currently watched channel and chat visibility. */
class AccountNotificationsRepository(private val auth: AuthRepository, private val challenges: ChallengesRepository, scope: CoroutineScope) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Connection discovery requires client.type and capabilities even when
        // their values match the Kotlin model defaults (as in the chat client).
        encodeDefaults = true
    }
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val foreground = MutableStateFlow(false)
    private val queue = NotificationQueue()
    val notifications = queue.items
    init {
        scope.launch(Dispatchers.IO) {
            combine(auth.session, foreground) { session, visible ->
                Triple(session.signedIn && visible, session.generation, session.profile?.userId)
            }.distinctUntilChanged().collectLatest { (active, generation, userId) ->
                queue.clearAccountNotifications()
                if (!active || userId == null) return@collectLatest
                var backoff = 1_000L
                while (currentCoroutineContext().isActive) {
                    try { connect(userId, generation) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Retry with a fresh auth token, never downgrade to guest. */ }
                    delay(backoff.milliseconds)
                    backoff = (backoff * 2).coerceAtMost(30_000)
                }
            }
        }
    }
    fun setForeground(visible: Boolean) { foreground.value = visible }
    fun dismiss(id: String) = queue.dismiss(id)
    fun postSleepTimer(minutes: Int) {
        val kind = if (minutes > 0) NotificationKind.SleepTimerStarted else NotificationKind.SleepTimerStopped
        queue.add(
            AppNotification("sleep-timer:${System.nanoTime()}", kind, -1, -1, timerMinutes = minutes),
            SystemClock.elapsedRealtime()
        )
    }
    fun preview(kind: NotificationKind) {
        queue.add(AppNotification("preview:${System.nanoTime()}", kind, -1, -1,
            channelSlug = if (kind == NotificationKind.StreamStarted) "rraenee" else null, preview = true), SystemClock.elapsedRealtime())
    }
    fun accepts(item: AppNotification): Boolean {
        val session = auth.session.value
        return item.kind.isSleepTimer || item.preview ||
            (session.signedIn && session.generation == item.generation && session.profile?.userId == item.userId)
    }
    private suspend fun connect(userId: Long, generation: Long) = coroutineScope {
        val metadata = auth.request("https://web.kick.com/api/v1/realtime/connection", "POST",
            json.encodeToJsonElement(RealtimeConnectionRequest(RealtimeClientInfo(userId.toString()))).jsonObject)
        val response = json.decodeFromJsonElement<RealtimeConnectionResponse>(metadata)
        val connections = response.data?.connections.orEmpty()
        val url = (connections.firstOrNull { it.provider == "centrifugo" } ?: connections.firstOrNull())?.credentials?.url
            ?: "wss://realtime.us-east-1.platform.kick.com/connection/websocket"
        val authenticated = auth.request("https://web.kick.com/api/v1/realtime/auth/connection", "POST",
            buildJsonObject { put("client_id", userId.toString()) })
        val token = (authenticated["data"] as? JsonObject)?.get("token")?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() } ?: error("Missing account feed token")
        val done = CompletableDeferred<Unit>()
        val lastActivity = AtomicLong(SystemClock.elapsedRealtime())
        val socket = client.newWebSocket(Request.Builder().url(url).header("Origin", "https://kick.com").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(json.encodeToString(CentrifugoConnectFrame.serializer(), CentrifugoConnectFrame(ConnectPayload(token), 1)))
                listOf("private-userfeed.$userId", "private-$userId").forEachIndexed { index, channel ->
                    webSocket.send(json.encodeToString(CentrifugoSubscribeFrame.serializer(),
                        CentrifugoSubscribeFrame(SubscribePayload(channel, token = token), index + 2)))
                }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivity.set(SystemClock.elapsedRealtime())
                splitCentrifugoFrames(text).forEach { frame ->
                    if (frame == "{}") { webSocket.send("{}"); return@forEach }
                    runCatching {
                        val root = json.parseToJsonElement(frame).jsonObject
                        val push = root["push"] as? JsonObject
                        if (root["error"] != null || root["disconnect"] != null || push?.get("disconnect") != null || push?.get("unsubscribe") != null) {
                            done.complete(Unit); return@forEach
                        }
                        val decoded = json.decodeFromJsonElement<CentrifugoPushFrame>(root)
                        val publication = decoded.push?.pub ?: decoded.pub ?: return@forEach
                        val source = decoded.push?.channel ?: decoded.channel
                        val event = publication.eventName ?: return@forEach
                        val raw = publication.data?.data ?: return@forEach
                        val session = auth.session.value
                        if (!foreground.value || !session.signedIn || session.generation != generation || session.profile?.userId != userId) return@forEach
                        val item = decodeAccountNotification(source, event, raw, userId, generation) ?: return@forEach
                        if (item.kind == NotificationKind.WatchTimeGoal) challenges.onGoalReached(source, raw)
                        queue.add(item, SystemClock.elapsedRealtime())
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.complete(Unit) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(Unit) }
        })
        val watchdog = launch {
            while (isActive) {
                delay(30_000.milliseconds)
                if (SystemClock.elapsedRealtime() - lastActivity.get() > 90_000) { done.complete(Unit); break }
            }
        }
        try { done.await() } finally { watchdog.cancel(); socket.cancel() }
    }
}
