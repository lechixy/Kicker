package com.lechixy.kick.data.notifications

import android.os.SystemClock
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.repository.ChallengesRepository
import com.lechixy.kick.data.account.repository.fetchAccountLevel
import com.lechixy.kick.data.account.remote.accountDiagnostic
import com.lechixy.kick.data.chat.model.*
import com.lechixy.kick.data.chat.remote.maintainCentrifugoToken
import com.lechixy.kick.data.chat.remote.realtimeConnectionToken
import com.lechixy.kick.data.chat.remote.realtimeSubscriptionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/** One foreground account feed, independent of the currently watched channel and chat visibility. */
class AccountNotificationsRepository internal constructor(
    private val auth: AuthRepository,
    private val challenges: ChallengesRepository,
    scope: CoroutineScope,
    private val client: WebSocket.Factory,
    private val elapsedRealtime: () -> Long,
) {
    constructor(auth: AuthRepository, challenges: ChallengesRepository, scope: CoroutineScope) : this(
        auth, challenges, scope, OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
        SystemClock::elapsedRealtime,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        // Connection discovery requires client.type and capabilities even when
        // their values match the Kotlin model defaults (as in the chat client).
        encodeDefaults = true
    }
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
                coroutineScope {
                    val levelUps = Channel<AppNotification>(capacity = 5)
                    launch {
                        var lastNotifiedLevel = 0
                        for (item in levelUps) {
                            if (item.level > lastNotifiedLevel) {
                                val badgeUrl = fetchLevelBadge(item.level)
                                if (foreground.value && accepts(item)) {
                                    lastNotifiedLevel = item.level
                                    queue.add(item.copy(animatedBadgeUrl = badgeUrl), elapsedRealtime())
                                }
                            }
                        }
                    }
                    var backoff = 1_000L
                    while (isActive) {
                        try { connect(userId, generation, levelUps) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { accountDiagnostic(TAG, "Feed connection failed: ${error.javaClass.simpleName}") }
                        delay(backoff.milliseconds)
                        backoff = (backoff * 2).coerceAtMost(30_000)
                    }
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
            elapsedRealtime()
        )
    }
    fun preview(kind: NotificationKind) {
        queue.add(AppNotification("preview:${System.nanoTime()}", kind, -1, -1,
            channelSlug = if (kind == NotificationKind.StreamStarted) "rraenee" else null, preview = true), elapsedRealtime())
    }
    fun accepts(item: AppNotification): Boolean {
        val session = auth.session.value
        return item.kind.isSleepTimer || item.preview ||
            (session.signedIn && session.generation == item.generation && session.profile?.userId == item.userId)
    }
    private suspend fun fetchLevelBadge(level: Int): String? = withTimeoutOrNull(4_000) {
        repeat(3) { attempt ->
            val current = try {
                auth.fetchAccountLevel()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (current != null && current.level >= level) {
                return@withTimeoutOrNull current.badge?.animatedImage
                    ?.takeIf { current.level == level && it.isNotBlank() }
            }
            if (attempt < 2) delay(500)
        }
        null
    }

    private suspend fun connect(
        userId: Long,
        generation: Long,
        levelUps: SendChannel<AppNotification>
    ) = coroutineScope {
        val metadata = auth.request("$REALTIME_URL/connection", "POST",
            json.encodeToJsonElement(RealtimeConnectionRequest(RealtimeClientInfo(userId.toString()))).jsonObject)
        val response = json.decodeFromJsonElement<RealtimeConnectionResponse>(metadata)
        val url = response.data?.connections.orEmpty()
            .filter { it.provider == "centrifugo" }
            .firstNotNullOfOrNull { it.credentials?.url?.takeIf(String::isNotBlank) }
            ?: "wss://realtime.us-east-1.platform.kick.com/connection/websocket"
        val clientId = userId.toString()
        val connectionToken = auth.realtimeConnectionToken(clientId)
        val opened = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val lastActivity = AtomicLong(elapsedRealtime())
        val nextCommandId = AtomicInteger(1)
        val replies = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        val socket = client.newWebSocket(Request.Builder().url(url).header("Origin", "https://kick.com").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (done.isCompleted) return
                lastActivity.set(elapsedRealtime())
                splitCentrifugoFrames(text).forEach { frame ->
                    if (frame == "{}") { webSocket.send("{}"); return@forEach }
                    runCatching {
                        val root = json.parseToJsonElement(frame).jsonObject
                        val replyId = (root["id"] as? JsonPrimitive)?.intOrNull
                        if (replyId != null && replyId > 0) {
                            replies.remove(replyId)?.complete(root)
                            return@forEach
                        }
                        val push = root["push"] as? JsonObject
                        if (root["disconnect"] != null || push?.get("disconnect") != null || push?.get("unsubscribe") != null) {
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
                        if (item.kind == NotificationKind.LevelUp) {
                            levelUps.trySend(item)
                            return@forEach
                        }
                        if (item.kind == NotificationKind.WatchTimeGoal) challenges.onGoalReached(source, raw)
                        queue.add(item, elapsedRealtime())
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                accountDiagnostic(TAG, "Feed socket failed: ${t.javaClass.simpleName}, HTTP ${response?.code}")
                done.complete(Unit)
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { done.complete(Unit) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.complete(Unit) }
        })

        suspend fun command(expected: String, encode: (Int) -> String): JsonObject? {
            val id = nextCommandId.getAndIncrement()
            val reply = CompletableDeferred<JsonObject>()
            replies[id] = reply
            try {
                if (!socket.send(encode(id))) return null
                val result = withTimeoutOrNull(10_000) { reply.await() }
                val payload = result?.get(expected) as? JsonObject
                if (payload != null && result?.get("error") == null) return payload
                val code = ((result?.get("error") as? JsonObject)?.get("code") as? JsonPrimitive)?.intOrNull
                accountDiagnostic(TAG, "Feed $expected failed: protocol code $code")
                return null
            } finally {
                replies.remove(id)
            }
        }

        val subscriptions = launch {
            if (withTimeoutOrNull(10_000) { opened.await() } == null) {
                done.complete(Unit)
                return@launch
            }
            val connected = command("connect") { id ->
                json.encodeToString(CentrifugoConnectFrame(ConnectPayload(connectionToken), id))
            }
            if (connected == null) {
                done.complete(Unit)
                return@launch
            }
            launch {
                val kept = maintainCentrifugoToken(connected) {
                    val refreshed = try {
                        auth.realtimeConnectionToken(clientId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        return@maintainCentrifugoToken null
                    }
                    command("refresh") { id ->
                        json.encodeToString(CentrifugoRefreshFrame(ConnectionRefreshPayload(refreshed), id))
                    }
                }
                if (!kept) done.complete(Unit)
            }
            for (channel in listOf("private-userfeed.$userId", "private-$userId")) launch {
                var backoff = 1_000L
                while (isActive) {
                    try {
                        val token = auth.realtimeSubscriptionToken(channel)
                        val subscribed = command("subscribe") { id ->
                            json.encodeToString(CentrifugoSubscribeFrame(SubscribePayload(channel, token = token), id))
                        }
                        if (subscribed != null) {
                            val feed = if (channel.startsWith("private-userfeed.")) "live" else "drops"
                            accountDiagnostic(TAG, "Subscribed to $feed feed")
                            val kept = maintainCentrifugoToken(subscribed) {
                                val refreshed = try {
                                    auth.realtimeSubscriptionToken(channel)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    return@maintainCentrifugoToken null
                                }
                                command("sub_refresh") { id ->
                                    json.encodeToString(CentrifugoSubscriptionRefresh(SubscriptionRefreshPayload(channel, refreshed), id))
                                }
                            }
                            if (!kept) done.complete(Unit)
                            break
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        accountDiagnostic(TAG, "Feed channel authorization failed: ${error.javaClass.simpleName}")
                    }
                    delay(backoff.milliseconds)
                    backoff = (backoff * 2).coerceAtMost(30_000)
                }
            }
        }
        val watchdog = launch {
            while (isActive) {
                delay(30_000.milliseconds)
                if (elapsedRealtime() - lastActivity.get() > 90_000) { done.complete(Unit); break }
            }
        }
        try { done.await() } finally {
            done.complete(Unit)
            subscriptions.cancel()
            watchdog.cancel()
            socket.cancel()
        }
    }

    private companion object {
        const val TAG = "KickAccountFeed"
        const val REALTIME_URL = "https://web.kick.com/api/v1/realtime"
    }
}
