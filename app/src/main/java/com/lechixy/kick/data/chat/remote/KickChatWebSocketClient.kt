package com.lechixy.kick.data.chat.remote

import android.util.Log
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.data.chat.model.CentrifugoConnectFrame
import com.lechixy.kick.data.chat.model.CentrifugoPushFrame
import com.lechixy.kick.data.chat.model.CentrifugoRefreshFrame
import com.lechixy.kick.data.chat.model.CentrifugoSubscribeFrame
import com.lechixy.kick.data.chat.model.CentrifugoSubscriptionRefresh
import com.lechixy.kick.data.chat.model.ChannelSubscriptionEvent
import com.lechixy.kick.data.chat.model.ChatCelebration
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageMetadata
import com.lechixy.kick.data.chat.model.ChatMessageType
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.ConnectPayload
import com.lechixy.kick.data.chat.model.ConnectionRefreshPayload
import android.os.SystemClock
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.data.chat.model.ChatPointsUpdate
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.data.chat.model.GiftsLeaderboardUpdatedEvent
import com.lechixy.kick.data.chat.model.KicksGiftedEvent
import com.lechixy.kick.data.chat.model.KicksLeaderboardUpdatedEvent
import com.lechixy.kick.data.chat.model.TimedChatEvent
import com.lechixy.kick.data.chat.model.tickerDurationMillis
import com.lechixy.kick.data.chat.model.MessageDeletedEvent
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.model.PointsUpdatedEvent
import com.lechixy.kick.data.chat.model.RealtimeAuthRequest
import com.lechixy.kick.data.chat.model.RealtimeClientInfo
import com.lechixy.kick.data.chat.model.RealtimeConnectionRequest
import com.lechixy.kick.data.chat.model.RewardRedeemedEvent
import com.lechixy.kick.data.chat.model.StopStreamBroadcastEvent
import com.lechixy.kick.data.chat.model.StreamHostEvent
import com.lechixy.kick.data.chat.model.StreamHostedEvent
import com.lechixy.kick.data.chat.model.StreamerIsLiveEvent
import com.lechixy.kick.data.chat.model.SubscribePayload
import com.lechixy.kick.data.chat.model.SubscriptionRefreshPayload
import com.lechixy.kick.data.chat.model.NewSubscriberUpdate
import com.lechixy.kick.data.chat.model.UserBannedEvent
import com.lechixy.kick.data.chat.model.mergeChatHistory
import com.lechixy.kick.data.chat.model.splitCentrifugoFrames
import com.lechixy.kick.data.chat.model.toRecipientMessages
import com.lechixy.kick.data.chat.model.withSelectedBadgesOnly
import com.lechixy.kick.data.common.remote.KickApi
import com.lechixy.kick.data.common.remote.KickNetwork

enum class ChatConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class KickChatWebSocketClient(
    private val api: KickApi = KickNetwork.api
) {
    private val tag = "KickChatWS"
    private val maxMessagesBuffer = 250

    companion object {
        private const val WATCHDOG_CHECK_INTERVAL_MS = 5_000L
        private const val SUBSCRIPTION_TIMEOUT_MS = 15_000L
        private const val QUIET_CHAT_CHECK_INTERVAL_MS = 45_000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentChannelId: Long? = null
    private var currentChatroomId: Long? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var connectionRefreshJob: Job? = null
    private var privateSubscriptionJob: Job? = null
    private var connectionJob: Job? = null
    private var connectionGeneration = 0L
    private var retryDelayMs = 1_000L
    private var lastChatMessageAt = 0L
    private var lastChatMessageCreatedAt: String? = null

    private val batcher = LiveChatBatcher()
    private var batchJob: Job? = null
    private val _highTraffic = MutableStateFlow(false)
    val highTraffic: StateFlow<Boolean> = _highTraffic.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _pinnedMessage = MutableStateFlow<PinnedMessageInfo?>(null)
    val pinnedMessage: StateFlow<PinnedMessageInfo?> = _pinnedMessage.asStateFlow()

    private val _userColors = MutableStateFlow<Map<String, String>>(emptyMap())
    val userColors: StateFlow<Map<String, String>> = _userColors.asStateFlow()

    private val _events = MutableSharedFlow<ChatWebSocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatWebSocketEvent> = _events.asSharedFlow()

    private val _tickerEvents = MutableStateFlow<List<TimedChatEvent>>(emptyList())
    val tickerEvents = _tickerEvents.asStateFlow()
    private val _subscriptionGoal = MutableStateFlow<KickGoalDto?>(null)
    val subscriptionGoal = _subscriptionGoal.asStateFlow()

    private val presentationRevision = AtomicLong()
    private val recentStreamHosts = RecentStreamHosts()
    private val _pointsUpdated = MutableStateFlow<ChatPointsUpdate?>(null)
    val pointsUpdated = _pointsUpdated.asStateFlow()

    fun consumePoints(revision: Long): Long {
        while (true) {
            val update = _pointsUpdated.value ?: return 0L
            if (update.revision != revision || update.pendingPoints == 0L) return 0L
            if (_pointsUpdated.compareAndSet(update, update.copy(pendingPoints = 0L))) {
                return update.pendingPoints
            }
        }
    }

    fun dismissTickerEvents(ids: Set<Long>) {
        _tickerEvents.update { events -> events.filterNot { it.id in ids } }
    }

    private fun publishEvent(event: ChatWebSocketEvent) {
        val now = SystemClock.elapsedRealtime()
        if (event is ChatWebSocketEvent.StreamHosted && !recentStreamHosts.accept(event.event, now)) return
        event.tickerDurationMillis()?.let { duration ->
            val timed = TimedChatEvent(presentationRevision.incrementAndGet(), event, duration, now + duration)
            _tickerEvents.update { events ->
                val active = events.filter { it.expiresAtMillis > now }
                active.takeLast(31) + timed
            }
        }
        _events.tryEmit(event)
    }

    private val _connectionState = MutableStateFlow(ChatConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ChatConnectionState> = _connectionState.asStateFlow()

    fun updatePinnedMessage(pinned: PinnedMessageInfo?) {
        _pinnedMessage.value = pinned?.takeIf { it.message != null }?.withSelectedBadgesOnly()
    }

    @Synchronized
    fun seedHistory(channelId: Long, chatroomId: Long, messages: List<ChatMessage>) {
        if (currentChannelId != channelId || currentChatroomId != chatroomId) {
            disconnectInternal(clearMessages = true)
            currentChannelId = channelId
            currentChatroomId = chatroomId
        }
        flushMessages(sample = false)
        _messages.update { current -> mergeChatHistory(messages, current, maxMessagesBuffer) }
        cacheUserColorsFromMessages(messages)
    }

    fun cacheUserColorsFromMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        _userColors.update { current ->
            val updated = current.toMutableMap()
            messages.forEach { msg ->
                val username = msg.sender?.username
                val color = msg.sender?.identity?.color
                if (!username.isNullOrBlank() && !color.isNullOrBlank()) {
                    updated[username.lowercase()] = color
                }
            }
            while (updated.size > 1_000) updated.remove(updated.keys.first())
            updated
        }
    }

    @Synchronized
    fun connect(channelId: Long, chatroomId: Long) {
        val isSameChannel = currentChannelId == channelId && currentChatroomId == chatroomId
        if (isSameChannel && _connectionState.value in setOf(ChatConnectionState.CONNECTED, ChatConnectionState.CONNECTING)) {
            return
        }

        val isSwitchingChannel = currentChannelId != null && !isSameChannel
        disconnectInternal(clearMessages = isSwitchingChannel)

        currentChannelId = channelId
        currentChatroomId = chatroomId
        retryDelayMs = 1_000L

        batchJob = clientScope.launch {
            while (isActive) {
                delay((if (_highTraffic.value) 250L else 100L).milliseconds)
                flushMessages()
                val now = SystemClock.elapsedRealtime()
                if (_tickerEvents.value.any { it.expiresAtMillis <= now }) {
                    _tickerEvents.update { events -> events.filter { it.expiresAtMillis > now } }
                }
            }
        }
        _connectionState.value = ChatConnectionState.CONNECTING
        val generation = connectionGeneration
        connectionJob = clientScope.launch {
            startConnectionFlow(channelId, chatroomId, generation)
        }
    }

    private suspend fun startConnectionFlow(channelId: Long, chatroomId: Long, generation: Long) {
        try {
            val account = runCatching { com.lechixy.kick.data.account.AccountGraph.auth }.getOrNull()
            val userId = account?.session?.value?.takeIf { it.signedIn }?.profile?.userId
            val guestClientId = userId?.toString() ?: (10000000..99999999).random().toString()

            val connectionReq = RealtimeConnectionRequest(
                client = RealtimeClientInfo(id = guestClientId, type = "web"),
                capabilities = com.lechixy.kick.data.chat.model.RealtimeCapabilities(
                    acceptedProviders = listOf(
                        com.lechixy.kick.data.chat.model.ProviderCapability("pusher"),
                        com.lechixy.kick.data.chat.model.ProviderCapability("centrifugo")
                    )
                )
            )

            val connectionRes = try {
                api.getChannelChatConnection(channelId, connectionReq)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                api.getRealtimeConnection(connectionReq)
            }

            val wsUrl = connectionRes.data?.connections.orEmpty()
                .filter { it.provider == "centrifugo" || it.provider == null }
                .firstNotNullOfOrNull { it.credentials?.url?.takeIf(String::isNotBlank) }
                ?: "wss://realtime.us-east-1.platform.kick.com/connection/websocket"

            val accountTokenProvider: (suspend () -> String)? = if (userId != null) {
                { account.realtimeConnectionToken(guestClientId) }
            } else null
            val guestTokenProvider: suspend () -> String = {
                api.getRealtimeAuth(RealtimeAuthRequest(clientId = guestClientId)).data?.token.orEmpty()
            }
            val authenticatedToken = try {
                accountTokenProvider?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Log.w(tag, "Authenticated chat unavailable; private channel points updates unavailable until reconnect")
                null
            }
            val token = authenticatedToken ?: guestTokenProvider()
            val refreshToken = accountTokenProvider?.takeIf { authenticatedToken != null } ?: guestTokenProvider

            val request = Request.Builder()
                .url(wsUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Origin", "https://kick.com")
                .build()

            synchronized(this) {
                if (connectionGeneration != generation) return
                val listener = ChatSocketListener(
                    token, guestClientId, channelId, chatroomId, authenticatedToken != null, refreshToken,
                )
                val socket = client.newWebSocket(request, listener)
                webSocket = socket
                lastChatMessageAt = SystemClock.elapsedRealtime()
                lastChatMessageCreatedAt = null
                listener.watch(socket)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            synchronized(this) {
                if (connectionGeneration != generation) return
                Log.w(tag, "Failed to initiate chat connection: ${e.javaClass.simpleName}")
                scheduleReconnect(channelId, chatroomId)
            }
        }
    }

    private inner class ChatSocketListener(
        private val token: String,
        private val clientId: String,
        private val channelId: Long,
        private val chatroomId: Long,
        private val authenticated: Boolean,
        private val refreshToken: suspend () -> String,
    ) : WebSocketListener() {
        private val subscriptions = mapOf(
            2 to "chatroom_$chatroomId",
            3 to "channel_$channelId",
            4 to "channel.$channelId",
            5 to "chatrooms.$chatroomId",
            6 to "predictions-channel-$channelId",
            7 to "chatrooms.$chatroomId.v2",
        )
        private val pointsChannel = "private-channelpoints-$clientId"
        private var nextCommandId = 10
        private val commandReplies = mutableMapOf<Int, CompletableDeferred<JsonObject>>()
        private val confirmedSubscriptions = mutableSetOf<String>()
        private val roomRepliesPending = subscriptions.filterValues { it.startsWith("chatroom") }.keys.toMutableSet()
        private val startedAt = SystemClock.elapsedRealtime()
        private var lastFrameAt = startedAt
        private var inactivityLimitMs: Long? = null
        private var sendPong = true
        private var connectedAt: Instant? = null
        private var nextHistoryCheckAt = startedAt + QUIET_CHAT_CHECK_INTERVAL_MS

        fun watch(socket: WebSocket) {
            heartbeatJob = clientScope.launch {
                while (isActive) {
                    delay(WATCHDOG_CHECK_INTERVAL_MS.milliseconds)
                    val historyCheck = synchronized(this@KickChatWebSocketClient) {
                        if (webSocket !== socket) return@launch
                        val now = SystemClock.elapsedRealtime()
                        val setupTimedOut = connectedAt == null && now - startedAt >= SUBSCRIPTION_TIMEOUT_MS
                        val heartbeatsStopped = inactivityLimitMs?.let { now - lastFrameAt >= it } == true
                        if (setupTimedOut || heartbeatsStopped) {
                            reconnect(socket, if (setupTimedOut) "Chat subscription timed out" else "Chat heartbeats stopped")
                            return@launch
                        }
                        if (connectedAt != null && now >= nextHistoryCheckAt &&
                            now - lastChatMessageAt >= QUIET_CHAT_CHECK_INTERVAL_MS
                        ) {
                            nextHistoryCheckAt = now + QUIET_CHAT_CHECK_INTERVAL_MS
                            lastChatMessageAt
                        } else null
                    }
                    if (historyCheck != null) checkQuietChat(socket, historyCheck)
                }
            }
        }

        private suspend fun checkQuietChat(socket: WebSocket, lastMessageAt: Long) {
            val history = try {
                withTimeoutOrNull(8_000) { api.getChatHistory(channelId).data?.messages }.orEmpty()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return
            }
            synchronized(this@KickChatWebSocketClient) {
                if (webSocket !== socket || lastChatMessageAt != lastMessageAt) return
                val subscribedAt = connectedAt ?: return
                val after = chatMessageTime(lastChatMessageCreatedAt) ?: subscribedAt
                val knownIds = _messages.value.mapTo(hashSetOf()) { it.id }
                if (historyHasMissingChatMessages(history, knownIds, after, Instant.now())) {
                    reconnect(socket, "Chat publications stopped while the room was active")
                }
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(this@KickChatWebSocketClient) {
                if (this@KickChatWebSocketClient.webSocket !== webSocket) return
                val frame = CentrifugoConnectFrame(ConnectPayload(token), id = 1)
                if (!webSocket.send(json.encodeToString(CentrifugoConnectFrame.serializer(), frame))) {
                    reconnect(webSocket, "Could not send chat connect command")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            synchronized(this@KickChatWebSocketClient) {
                if (this@KickChatWebSocketClient.webSocket !== webSocket) return
                lastFrameAt = SystemClock.elapsedRealtime()

                for (frame in splitCentrifugoFrames(text)) {
                    val root = runCatching { json.parseToJsonElement(frame) as? JsonObject }.getOrNull() ?: continue
                    if (root.isEmpty()) {
                        if (sendPong && !webSocket.send("{}")) {
                            reconnect(webSocket, "Could not send chat pong")
                            return
                        }
                        continue
                    }

                    val replyId = (root["id"] as? JsonPrimitive)?.intOrNull ?: 0
                    if (replyId > 0) {
                        handleReply(webSocket, replyId, root)
                    } else {
                        val push = (root["push"] as? JsonObject) ?: root
                        val unsubscribedChannel = (push["channel"] as? JsonPrimitive)?.content
                        if (push["disconnect"] != null) {
                            reconnect(webSocket, "Server disconnected chat")
                        } else if (push["unsubscribe"] != null && unsubscribedChannel == pointsChannel && authenticated) {
                            confirmedSubscriptions.remove(pointsChannel)
                            startPointsSubscription(webSocket)
                        } else if (push["unsubscribe"] != null && unsubscribedChannel != null &&
                            unsubscribedChannel in confirmedSubscriptions
                        ) {
                            reconnect(webSocket, "Server removed a chat subscription")
                        } else {
                            handlePublication(root, channelId, chatroomId, clientId.toLongOrNull().takeIf { authenticated })
                        }
                    }
                    if (this@KickChatWebSocketClient.webSocket !== webSocket) return
                }
            }
        }

        private fun handleReply(socket: WebSocket, id: Int, root: JsonObject) {
            commandReplies.remove(id)?.let {
                it.complete(root)
                return
            }

            if (id == 1) {
                val result = root["connect"] as? JsonObject
                if (result == null || root["error"] != null) {
                    reconnect(socket, "Chat connect command rejected")
                    return
                }
                inactivityLimitMs = (result["ping"] as? JsonPrimitive)?.intOrNull
                    ?.takeIf { it > 0 }?.let { it * 1_000L + 10_000L }
                sendPong = (result["pong"] as? JsonPrimitive)?.booleanOrNull == true
                for ((subId, channel) in subscriptions) {
                    val frame = CentrifugoSubscribeFrame(
                        SubscribePayload(channel),
                        id = subId,
                    )
                    if (!socket.send(json.encodeToString(CentrifugoSubscribeFrame.serializer(), frame))) {
                        reconnect(socket, "Could not send chat subscribe command")
                        return
                    }
                }
                connectionRefreshJob?.cancel()
                connectionRefreshJob = clientScope.launch { refreshConnection(socket, result) }
                if (authenticated) startPointsSubscription(socket)
                return
            }

            val channel = subscriptions[id] ?: return
            val isChatroom = roomRepliesPending.remove(id)
            if (root["subscribe"] is JsonObject && root["error"] == null) {
                confirmedSubscriptions.add(channel)
                if (isChatroom && connectedAt == null) {
                    connectedAt = Instant.now()
                    retryDelayMs = 1_000L
                    _connectionState.value = ChatConnectionState.CONNECTED
                }
            } else if (isChatroom && connectedAt == null && roomRepliesPending.isEmpty()) {
                reconnect(socket, "Chat room subscriptions rejected")
            }
        }

        private suspend fun refreshConnection(socket: WebSocket, connected: JsonObject) {
            val kept = maintainCentrifugoToken(connected) {
                val refreshed = try {
                    refreshToken().takeIf { it.isNotBlank() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                } ?: return@maintainCentrifugoToken null
                command(socket, "refresh") { id ->
                    json.encodeToString(CentrifugoRefreshFrame(ConnectionRefreshPayload(refreshed), id))
                }
            }
            if (!kept) reconnect(socket, "Chat connection token refresh failed")
        }

        private fun startPointsSubscription(socket: WebSocket) {
            privateSubscriptionJob?.cancel()
            privateSubscriptionJob = clientScope.launch { subscribeToPoints(socket) }
        }

        private suspend fun subscribeToPoints(socket: WebSocket) {
            val auth = com.lechixy.kick.data.account.AccountGraph.auth
            var retryDelay = 1_000L
            while (true) {
                val subscribed = try {
                    val channelToken = auth.realtimeSubscriptionToken(pointsChannel)
                    command(socket, "subscribe") { id ->
                        json.encodeToString(CentrifugoSubscribeFrame(SubscribePayload(pointsChannel, token = channelToken), id))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (subscribed != null) {
                    synchronized(this@KickChatWebSocketClient) {
                        if (webSocket !== socket) return
                        confirmedSubscriptions.add(pointsChannel)
                    }
                    val kept = maintainCentrifugoToken(subscribed) {
                        val channelToken = try {
                            auth.realtimeSubscriptionToken(pointsChannel)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            return@maintainCentrifugoToken null
                        }
                        command(socket, "sub_refresh") { id ->
                            json.encodeToString(CentrifugoSubscriptionRefresh(SubscriptionRefreshPayload(pointsChannel, channelToken), id))
                        }
                    }
                    if (kept) return
                    retryDelay = 1_000L
                    Log.w(tag, "Chat points subscription refresh failed; retrying on the same socket")
                }
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(30_000L)
            }
        }

        private suspend fun command(socket: WebSocket, expected: String, encode: (Int) -> String): JsonObject? {
            val reply = CompletableDeferred<JsonObject>()
            val id = synchronized(this@KickChatWebSocketClient) {
                if (webSocket !== socket) return null
                val id = nextCommandId++
                commandReplies[id] = reply
                if (!socket.send(encode(id))) {
                    commandReplies.remove(id)
                    return null
                }
                id
            }
            try {
                val frame = withTimeoutOrNull(10_000) { reply.await() } ?: return null
                return (frame[expected] as? JsonObject)?.takeIf { frame["error"] == null }
            } finally {
                synchronized(this@KickChatWebSocketClient) { commandReplies.remove(id) }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            reconnect(webSocket, "Chat socket failed: ${t.javaClass.simpleName}")

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) =
            reconnect(webSocket, "Chat socket closing: $code")

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
            reconnect(webSocket, "Chat socket closed: $code")

        private fun reconnect(socket: WebSocket, reason: String) {
            synchronized(this@KickChatWebSocketClient) {
                if (this@KickChatWebSocketClient.webSocket !== socket) return
                Log.w(tag, reason)
                scheduleReconnect(channelId, chatroomId)
            }
        }
    }

    private fun handlePublication(
        frame: JsonObject,
        channelId: Long,
        chatroomId: Long,
        authenticatedUserId: Long?,
    ) {
        try {
            val pushFrame = json.decodeFromJsonElement<CentrifugoPushFrame>(frame)
            val publication = pushFrame.push?.pub ?: pushFrame.pub ?: return
            val eventData = publication.data ?: return
            val rawData = eventData.data
            val eventType = publication.eventName ?: return
            val publicationChannel = pushFrame.push?.channel ?: pushFrame.channel
            val isChannelPublication = publicationChannel == "channel_$channelId" || publicationChannel == "channel.$channelId"

            when (eventType) {
                "App\\Events\\ChatMoveToSupportedChannelEvent", "ChatMoveToSupportedChannelEvent" -> {
                    if ((pushFrame.push?.channel ?: pushFrame.channel) != "channel.$channelId") return
                    val event = rawData?.let { decodeEventPayload<com.lechixy.kick.data.chat.model.ChannelRaid>(it) } ?: return
                    if (event.channel.id != channelId || event.hosted.id == channelId || event.targetSlug.isBlank()) return
                    publishEvent(ChatWebSocketEvent.Raid(event))
                }

                "App\\Events\\StreamHostedEvent", "StreamHostedEvent" -> {
                    if (publicationChannel != "chatrooms.$chatroomId" && publicationChannel != "chatrooms.$chatroomId.v2" &&
                        !isChannelPublication) return
                    val data = rawData ?: return
                    val event = decodeEventPayload<StreamHostedEvent>(data) ?: return
                    publishEvent(ChatWebSocketEvent.StreamHosted(event))
                }
                "App\\Events\\StreamHostEvent", "StreamHostEvent" -> {
                    if (publicationChannel != "chatrooms.$chatroomId.v2") return
                    val event = rawData?.let { decodeEventPayload<StreamHostEvent>(it) } ?: return
                    if (event.chatroomId != chatroomId) return
                    publishEvent(ChatWebSocketEvent.StreamHosted(event.toStreamHostedEvent()))
                }
                "App\\Events\\ChatroomUpdatedEvent", "ChatroomUpdatedEvent",
                "App\\Events\\ChatSettingsChanged", "ChatSettingsChanged" -> {
                    val payload = rawData?.let { decodeEventPayload<JsonObject>(it) } ?: return
                    val eventChannel = (payload["channel"] as? JsonObject)?.get("id")
                    if (eventChannel != null && (eventChannel as? JsonPrimitive)?.content != channelId.toString()) return
                    if (eventType.endsWith("ChatroomUpdatedEvent") &&
                        (payload["id"] as? JsonPrimitive)?.content != chatroomId.toString()) return
                    publishEvent(ChatWebSocketEvent.SettingsChanged(
                        com.lechixy.kick.data.chat.policy.ChatSettingsUpdates.normalize(payload)))
                }

                "App\\Events\\ChatMessageEvent",
                "ChatMessageEvent" -> {
                    val data = rawData ?: return
                    val chatMessage = decodeEventPayload<ChatMessage>(data)?.withSelectedBadgesOnly()
                    if (chatMessage != null && (chatMessage.content.isNotBlank() || chatMessage.isCelebration)) {
                        lastChatMessageAt = SystemClock.elapsedRealtime()
                        lastChatMessageCreatedAt = chatMessage.createdAt
                        addMessage(chatMessage)
                        publishEvent(ChatWebSocketEvent.MessageReceived(chatMessage))
                    }
                }

                "App\\Events\\ChatCelebrationEvent",
                "ChatCelebrationEvent" -> {
                    val data = rawData ?: return
                    val chatMessage = decodeEventPayload<ChatMessage>(data)?.withSelectedBadgesOnly()
                    if (chatMessage != null && (chatMessage.isCelebration || chatMessage.sender != null)) {
                        val normalizedMsg = if (chatMessage.isCelebration) {
                            chatMessage
                        } else {
                            chatMessage.copy(type = ChatMessageType.CELEBRATION)
                        }
                        addMessage(normalizedMsg)
                        publishEvent(ChatWebSocketEvent.MessageReceived(normalizedMsg))
                    } else {
                        val celebration = decodeEventPayload<ChatCelebration>(data) ?: return
                        val eventMessage = ChatMessage(
                            id = "celebration_${System.nanoTime()}_${celebration.id.ifBlank { celebration.username }}",
                            type = ChatMessageType.CELEBRATION,
                            sender = celebration.sender,
                            metadata = ChatMessageMetadata(celebration = celebration)
                        )
                        addMessage(eventMessage)
                        publishEvent(ChatWebSocketEvent.Celebration(celebration))
                    }
                }

                "App\\Events\\PinnedMessageCreatedEvent",
                "PinnedMessageCreatedEvent" -> {
                    val data = rawData ?: return
                    val pinnedInfo = decodeEventPayload<PinnedMessageInfo>(data)?.withSelectedBadgesOnly() ?: return
                    if (pinnedInfo.message != null) {
                        _pinnedMessage.value = pinnedInfo
                        publishEvent(ChatWebSocketEvent.PinnedMessageCreated(pinnedInfo))
                    }
                }

                "App\\Events\\PinnedMessageDeletedEvent",
                "PinnedMessageDeletedEvent" -> {
                    _pinnedMessage.value = null
                    publishEvent(ChatWebSocketEvent.PinnedMessageDeleted)
                }

                "RewardRedeemedEvent",
                "App\\Events\\RewardRedeemedEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<RewardRedeemedEvent>(data) ?: return
                    val eventMessage = ChatMessage(
                        id = "reward_${System.nanoTime()}_${event.userId ?: event.username}",
                        type = ChatMessageType.REWARD_REDEEMED,
                        content = event.userInput,
                        rewardRedeemed = event
                    )
                    addMessage(eventMessage)
                    publishEvent(ChatWebSocketEvent.RewardRedeemed(event))
                }

                "GiftedSubscriptionsEvent",
                "App\\Events\\GiftedSubscriptionsEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<GiftedSubscriptionsEvent>(data) ?: return
                    val recipientMessages = event.toRecipientMessages()
                    recipientMessages.forEach(::addMessage)
                    publishEvent(ChatWebSocketEvent.GiftedSubscriptions(event))
                }

                "App\\Events\\ChannelSubscriptionEvent",
                "ChannelSubscriptionEvent",
                "App\\Events\\SubscriptionEvent",
                "SubscriptionEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<ChannelSubscriptionEvent>(data) ?: return
                    val eventMessage = ChatMessage(
                        id = "sub_${System.nanoTime()}_${event.username.ifBlank { event.userIds.firstOrNull() ?: "sub" }}",
                        type = ChatMessageType.CHANNEL_SUBSCRIPTION,
                        channelSubscription = event
                    )
                    addMessage(eventMessage)
                    publishEvent(ChatWebSocketEvent.ChannelSubscribed(event))
                }

                "KicksGifted", "App\\Events\\KicksGifted" -> {
                    if (!isChannelPublication) return
                    val event = rawData?.let { decodeEventPayload<KicksGiftedEvent>(it) } ?: return
                    if (event.giftTransactionId.isBlank()) return
                    addMessage(ChatMessage(
                        id = "kicks_${event.giftTransactionId}",
                        content = event.message,
                        type = ChatMessageType.KICKS_GIFTED,
                        createdAt = event.createdAt,
                        sender = com.lechixy.kick.data.chat.model.ChatSender(
                            id = event.sender.id, username = event.sender.username
                        ),
                        kicksGifted = event
                    ))
                    publishEvent(ChatWebSocketEvent.KicksGifted(event))
                }
                "NewSubscriberUpdatedEvent", "App\\Events\\NewSubscriberUpdatedEvent" -> {
                    if (!isChannelPublication) return
                    val event = rawData?.let { decodeEventPayload<NewSubscriberUpdate>(it) } ?: return
                    if (event.channelId != channelId || event.count < 0) return
                    publishEvent(ChatWebSocketEvent.SubscriberCountUpdated(event))
                }
                "GoalProgressUpdateEvent", "App\\Events\\GoalProgressUpdateEvent" -> {
                    if (!isChannelPublication) return
                    val goal = rawData?.let { decodeEventPayload<KickGoalDto>(it) } ?: return
                    if (goal.type == "subscriptions") _subscriptionGoal.value = goal
                    publishEvent(ChatWebSocketEvent.GoalProgressUpdated(goal))
                }
                "GiftsLeaderboardUpdated", "App\\Events\\GiftsLeaderboardUpdated" -> {
                    if (!isChannelPublication) return
                    val event = rawData?.let { decodeEventPayload<GiftsLeaderboardUpdatedEvent>(it) } ?: return
                    publishEvent(ChatWebSocketEvent.GiftsLeaderboardUpdated(event))
                }
                "KicksLeaderboardUpdated", "App\\Events\\KicksLeaderboardUpdated" -> {
                    if (!isChannelPublication) return
                    val event = rawData?.let { decodeEventPayload<KicksLeaderboardUpdatedEvent>(it) } ?: return
                    publishEvent(ChatWebSocketEvent.KicksLeaderboardUpdated(event))
                }

                "App\\Events\\UserBannedEvent",
                "UserBannedEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<UserBannedEvent>(data) ?: return
                    publishEvent(ChatWebSocketEvent.UserBanned(event))
                }

                "App\\Events\\MessageDeletedEvent",
                "MessageDeletedEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<MessageDeletedEvent>(data) ?: return
                    markMessageDeleted(event)
                    publishEvent(ChatWebSocketEvent.MessageDeleted(event))
                }

                "PointsUpdated",
                "App\\Events\\PointsUpdated" -> {
                    if (!com.lechixy.kick.data.channel.policy.ChannelPointsPolicy.acceptsPublication(
                            pushFrame.push?.channel ?: pushFrame.channel, authenticatedUserId)) return
                    val data = rawData ?: return
                    val event = decodeEventPayload<PointsUpdatedEvent>(data) ?: return
                    if (!com.lechixy.kick.data.channel.policy.ChannelPointsPolicy.accepts(event, channelId, authenticatedUserId)) return
                    val revision = presentationRevision.incrementAndGet()
                    val now = java.time.Instant.now()
                    _pointsUpdated.update { ChatPointsUpdate.receive(it, event.copy(userId = authenticatedUserId), now, revision) }
                    publishEvent(ChatWebSocketEvent.PointsUpdated(event))
                }

                "App\\Events\\StreamerIsLive",
                "StreamerIsLive" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<StreamerIsLiveEvent>(data) ?: return
                    publishEvent(ChatWebSocketEvent.StreamerIsLive(event))
                }

                "App\\Events\\StopStreamBroadcast",
                "StopStreamBroadcast" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<StopStreamBroadcastEvent>(data) ?: return
                    publishEvent(ChatWebSocketEvent.StopStreamBroadcast(event))
                }
            }
        } catch (_: Exception) {
            // Ignore non-chat or unparseable frames
        }
    }

    private inline fun <reified T> decodeEventPayload(rawData: JsonElement): T? {
        return when {
            rawData is JsonPrimitive && rawData.isString -> {
                json.decodeFromString<T>(rawData.content)
            }
            rawData is JsonObject -> {
                json.decodeFromJsonElement<T>(rawData)
            }
            else -> null
        }
    }

    @Synchronized
    private fun addMessage(message: ChatMessage) {
        batcher.add(message, android.os.SystemClock.elapsedRealtime())
    }

    @Synchronized
    private fun flushMessages(sample: Boolean = true) {
        val now = android.os.SystemClock.elapsedRealtime()
        _highTraffic.value = batcher.isBusy(now)
        val batch = batcher.drain(now, sample)
        if (batch.isEmpty()) return
        cacheUserColorsFromMessages(batch)
        _messages.update { current ->
            val ids = current.mapTo(HashSet()) { it.id }
            (current + batch.filter { ids.add(it.id) }).takeLast(maxMessagesBuffer)
        }
    }

    @Synchronized
    private fun markMessageDeleted(event: MessageDeletedEvent) {
        flushMessages(sample = false)
        val targetId = event.message?.id?.takeIf { it.isNotBlank() } ?: event.id
        if (targetId.isBlank()) return

        val fallbackText = when {
            event.aiModerated -> "Message deleted by AI moderator"
            event.violatedRules.isNotEmpty() -> "Message deleted because user violated rule"
            else -> "Message deleted by moderator"
        }

        _messages.update { currentList ->
            var changed = false
            val updated = currentList.map { msg ->
                val matchesMessage = msg.id == targetId
                val originalMsg = msg.metadata?.originalMessage
                val matchesOriginal = originalMsg != null && originalMsg.id == targetId
                if (matchesMessage || matchesOriginal) {
                    changed = true
                    msg.copy(
                        content = if (matchesMessage) fallbackText else msg.content,
                        messageDeleted = if (matchesMessage) event else msg.messageDeleted,
                        metadata = if (matchesOriginal) {
                            msg.metadata.copy(
                                originalMessage = originalMsg.copy(
                                    content = fallbackText,
                                    messageDeleted = event
                                )
                            )
                        } else {
                            msg.metadata
                        }
                    )
                } else {
                    msg
                }
            }
            if (changed) updated else currentList
        }

        _pinnedMessage.update { currentPin ->
            val pinnedMsg = currentPin?.message
            if (pinnedMsg != null && pinnedMsg.id == targetId) {
                currentPin.copy(
                    message = pinnedMsg.copy(
                        content = fallbackText,
                        messageDeleted = event
                    )
                )
            } else {
                currentPin
            }
        }
    }

    private fun scheduleReconnect(channelId: Long, chatroomId: Long) {
        val generation = ++connectionGeneration
        val socket = webSocket
        webSocket = null
        socket?.cancel()
        heartbeatJob?.cancel()
        connectionRefreshJob?.cancel()
        privateSubscriptionJob?.cancel()
        reconnectJob?.cancel()
        _connectionState.value = ChatConnectionState.CONNECTING
        val delayMs = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
        reconnectJob = clientScope.launch {
            delay(delayMs.milliseconds)
            startConnectionFlow(channelId, chatroomId, generation)
        }
    }

    @Synchronized
    fun refreshAccountConnection() {
        val channelId = currentChannelId ?: return
        val chatroomId = currentChatroomId ?: return
        if (_connectionState.value == ChatConnectionState.DISCONNECTED) return
        disconnectInternal(clearMessages = false)
        connect(channelId, chatroomId)
    }

    @Synchronized
    fun disconnect() {
        disconnectInternal(clearMessages = true)
    }

    private fun disconnectInternal(clearMessages: Boolean = true) {
        connectionGeneration++
        batchJob?.cancel()
        if (!clearMessages) flushMessages(sample = false)
        batcher.clear()
        _highTraffic.value = false
        connectionJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        connectionRefreshJob?.cancel()
        privateSubscriptionJob?.cancel()
        // Private publications belong to this connection's account, even when keeping public chat history.
        _pointsUpdated.value = null
        val socket = webSocket
        webSocket = null
        socket?.cancel()
        if (clearMessages) {
            currentChannelId = null
            currentChatroomId = null
            _messages.value = emptyList()
            _pinnedMessage.value = null
            _tickerEvents.value = emptyList()
            recentStreamHosts.clear()
            _subscriptionGoal.value = null
            _userColors.value = emptyMap()
        }
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }
}

