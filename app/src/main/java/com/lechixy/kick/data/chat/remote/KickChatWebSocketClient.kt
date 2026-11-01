package com.lechixy.kick.data.chat.remote

import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

import com.lechixy.kick.data.chat.model.CentrifugoConnectFrame
import com.lechixy.kick.data.chat.model.CentrifugoPushFrame
import com.lechixy.kick.data.chat.model.CentrifugoSubscribeFrame
import com.lechixy.kick.data.chat.model.ChannelSubscriptionEvent
import com.lechixy.kick.data.chat.model.ChatCelebration
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageMetadata
import com.lechixy.kick.data.chat.model.ChatMessageType
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.ConnectPayload
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.data.chat.model.MessageDeletedEvent
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.model.PointsUpdatedEvent
import com.lechixy.kick.data.chat.model.RealtimeAuthRequest
import com.lechixy.kick.data.chat.model.RealtimeClientInfo
import com.lechixy.kick.data.chat.model.RealtimeConnectionRequest
import com.lechixy.kick.data.chat.model.RewardRedeemedEvent
import com.lechixy.kick.data.chat.model.StopStreamBroadcastEvent
import com.lechixy.kick.data.chat.model.StreamerIsLiveEvent
import com.lechixy.kick.data.chat.model.SubscribePayload
import com.lechixy.kick.data.chat.model.UserBannedEvent
import com.lechixy.kick.data.chat.model.mergeChatHistory
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
        private const val WATCHDOG_CHECK_INTERVAL_MS = 30_000L
        private const val INACTIVITY_TIMEOUT_MS = 90_000L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentChannelId: Long? = null
    private var currentChatroomId: Long? = null
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null
    private var isIntentionallyClosed = false
    private val lastServerActivityMs = AtomicLong(0L)

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

    private val _pointsUpdated = MutableStateFlow<PointsUpdatedEvent?>(null)
    val pointsUpdated: StateFlow<PointsUpdatedEvent?> = _pointsUpdated.asStateFlow()

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

    fun connect(channelId: Long, chatroomId: Long) {
        val isSameChannel = currentChannelId == channelId && currentChatroomId == chatroomId
        if (isSameChannel && _connectionState.value == ChatConnectionState.CONNECTED) {
            return
        }

        val isSwitchingChannel = currentChannelId != null && currentChannelId != channelId
        disconnectInternal(clearMessages = isSwitchingChannel)

        currentChannelId = channelId
        currentChatroomId = chatroomId
        isIntentionallyClosed = false

        batchJob = clientScope.launch {
            while (isActive) {
                delay(if (_highTraffic.value) 250L else 100L)
                flushMessages()
            }
        }
        _connectionState.value = ChatConnectionState.CONNECTING
        connectionJob = clientScope.launch {
            startConnectionFlow(channelId, chatroomId)
        }
    }

    private suspend fun startConnectionFlow(channelId: Long, chatroomId: Long) {
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

            val wsUrl = connectionRes.data?.connections?.firstOrNull()?.credentials?.url
                ?.takeIf { it.isNotBlank() }
                ?: "wss://realtime.us-east-1.platform.kick.com/connection/websocket"

            val authReq = RealtimeAuthRequest(clientId = guestClientId)
            val authenticatedToken = if (userId != null) try {
                account.request("https://web.kick.com/api/v1/realtime/auth/connection", "POST",
                    kotlinx.serialization.json.buildJsonObject {
                        put("client_id", JsonPrimitive(guestClientId))
                    })?.get("data")?.let { it as? JsonObject }?.get("token")
                    ?.let { it as? JsonPrimitive }?.content
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
              catch (_: Exception) {
                  Log.w(tag, "Authenticated chat unavailable; private channel points updates unavailable until reconnect")
                  null
              } else null
            val token = authenticatedToken ?: api.getRealtimeAuth(authReq).data?.token.orEmpty()

            val request = Request.Builder()
                .url(wsUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                .header("Origin", "https://kick.com")
                .build()

            webSocket?.cancel()
            val listener = createWebSocketListener(token, guestClientId, channelId, chatroomId, authenticatedToken != null)
            webSocket = client.newWebSocket(request, listener)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate chat connection: ${e.message}", e)
            _connectionState.value = ChatConnectionState.ERROR
            scheduleReconnect(channelId, chatroomId)
        }
    }

    private fun startHeartbeatWatchdog(activeSocket: WebSocket, channelId: Long, chatroomId: Long) {
        heartbeatJob?.cancel()
        lastServerActivityMs.set(System.currentTimeMillis())

        heartbeatJob = clientScope.launch {
            while (isActive && !isIntentionallyClosed && webSocket === activeSocket) {
                delay(WATCHDOG_CHECK_INTERVAL_MS)
                if (isIntentionallyClosed || webSocket !== activeSocket) break

                val elapsedSinceLastActivity = System.currentTimeMillis() - lastServerActivityMs.get()

                // Centrifugo sends "{}" every ~25s; only reconnect if server is completely silent for >90s
                if (elapsedSinceLastActivity > INACTIVITY_TIMEOUT_MS) {
                    Log.w(tag, "Chat watchdog detected silent connection (${elapsedSinceLastActivity}ms), reconnecting...")
                    scheduleReconnect(channelId, chatroomId, delayMs = 500L)
                    break
                }
            }
        }
    }

    private fun createWebSocketListener(
        token: String,
        clientId: String,
        channelId: Long,
        chatroomId: Long,
        authenticated: Boolean
    ) = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (this@KickChatWebSocketClient.webSocket !== webSocket) return
            Log.d(tag, "WebSocket connected successfully")
            lastServerActivityMs.set(System.currentTimeMillis())

            // Frame 1: Connect
            val connectFrame = CentrifugoConnectFrame(
                connect = ConnectPayload(token = token, name = "js"),
                id = 1
            )
            webSocket.send(json.encodeToString(CentrifugoConnectFrame.serializer(), connectFrame))

            // Subscription Frames
            val subscriptions = listOf(
                "chatroom_$chatroomId" to 2,
                "channel_$channelId" to 3,
                "channel.$channelId" to 4,
                "chatrooms.$chatroomId" to 5,
                "predictions-channel-$channelId" to 6,
                "chatrooms.$chatroomId.v2" to 7,
                "private-channelpoints-$clientId" to 10
            )

            for ((ch, id) in subscriptions) {
                if (ch.startsWith("private-") && !authenticated) continue
                val subToken = if (ch.startsWith("private-")) token else null
                val subFrame = CentrifugoSubscribeFrame(
                    subscribe = SubscribePayload(channel = ch, flag = 1, token = subToken),
                    id = id
                )
                webSocket.send(json.encodeToString(CentrifugoSubscribeFrame.serializer(), subFrame))
            }

            _connectionState.value = ChatConnectionState.CONNECTED
            startHeartbeatWatchdog(webSocket, channelId, chatroomId)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (this@KickChatWebSocketClient.webSocket !== webSocket) return
            lastServerActivityMs.set(System.currentTimeMillis())

            val trimmed = text.trim()
            if (trimmed == "{}" || trimmed.isEmpty()) {
                webSocket.send("{}")
                if (_connectionState.value != ChatConnectionState.CONNECTED) {
                    _connectionState.value = ChatConnectionState.CONNECTED
                }
                return
            }

            // A message can contain newline-separated or directly adjacent protocol objects.
            com.lechixy.kick.data.chat.model.splitCentrifugoFrames(trimmed)
                .forEach { frameText ->
                    if (frameText == "{}") {
                        webSocket.send("{}")
                        return@forEach
                    }
                    handleFrame(frameText, channelId, chatroomId, clientId.toLongOrNull().takeIf { authenticated })
                }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (this@KickChatWebSocketClient.webSocket !== webSocket) return
            Log.e(tag, "WebSocket failure: ${t.message}")
            if (!isIntentionallyClosed) {
                _connectionState.value = ChatConnectionState.ERROR
                scheduleReconnect(channelId, chatroomId)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (this@KickChatWebSocketClient.webSocket !== webSocket) return
            Log.d(tag, "WebSocket closed: $reason")
            if (!isIntentionallyClosed) {
                _connectionState.value = ChatConnectionState.DISCONNECTED
                scheduleReconnect(channelId, chatroomId)
            }
        }
    }

    private fun handleFrame(frameText: String, channelId: Long, chatroomId: Long, authenticatedUserId: Long?) {
        try {
            // Check for Centrifugo disconnect or unsubscribe frames (e.g., token expired)
            if (frameText.contains("\"disconnect\"") || frameText.contains("\"unsubscribe\"")) {
                val rootObj = json.parseToJsonElement(frameText) as? JsonObject
                if (rootObj?.containsKey("disconnect") == true ||
                    (rootObj?.get("push") as? JsonObject)?.containsKey("unsubscribe") == true ||
                    (rootObj?.get("push") as? JsonObject)?.containsKey("disconnect") == true
                ) {
                    Log.w(tag, "Centrifugo disconnect/unsubscribe received, reconnecting...")
                    scheduleReconnect(channelId, chatroomId, delayMs = 500L)
                    return
                }
            }

            val pushFrame = json.decodeFromString<CentrifugoPushFrame>(frameText)
            val publication = pushFrame.push?.pub ?: pushFrame.pub ?: return
            val eventData = publication.data ?: return
            val rawData = eventData.data
            val eventType = publication.eventName ?: return

            when (eventType) {
                "App\\Events\\ChatMoveToSupportedChannelEvent", "ChatMoveToSupportedChannelEvent" -> {
                    if ((pushFrame.push?.channel ?: pushFrame.channel) != "channel.$channelId") return
                    val event = rawData?.let { decodeEventPayload<com.lechixy.kick.data.chat.model.ChannelRaid>(it) } ?: return
                    if (event.channel.id != channelId || event.hosted.id == channelId || event.targetSlug.isBlank()) return
                    _events.tryEmit(ChatWebSocketEvent.Raid(event))
                }

                "App\\Events\\StreamHostedEvent", "StreamHostedEvent" -> {
                    if (pushFrame.push?.channel != "chatrooms.$chatroomId") return
                    val data = rawData ?: return
                    val event = decodeEventPayload<com.lechixy.kick.data.chat.model.StreamHostedEvent>(data) ?: return
                    _events.tryEmit(ChatWebSocketEvent.StreamHosted(event))
                }
                "App\\Events\\ChatroomUpdatedEvent", "ChatroomUpdatedEvent",
                "App\\Events\\ChatSettingsChanged", "ChatSettingsChanged" -> {
                    val payload = rawData?.let { decodeEventPayload<JsonObject>(it) } ?: return
                    val eventChannel = (payload["channel"] as? JsonObject)?.get("id")
                    if (eventChannel != null && (eventChannel as? JsonPrimitive)?.content != channelId.toString()) return
                    if (eventType.endsWith("ChatroomUpdatedEvent") &&
                        (payload["id"] as? JsonPrimitive)?.content != chatroomId.toString()) return
                    _events.tryEmit(ChatWebSocketEvent.SettingsChanged(
                        com.lechixy.kick.data.chat.policy.ChatSettingsUpdates.normalize(payload)))
                }

                "App\\Events\\ChatMessageEvent",
                "ChatMessageEvent" -> {
                    val data = rawData ?: return
                    val chatMessage = decodeEventPayload<ChatMessage>(data)?.withSelectedBadgesOnly()
                    if (chatMessage != null && (chatMessage.content.isNotBlank() || chatMessage.isCelebration)) {
                        addMessage(chatMessage)
                        _events.tryEmit(ChatWebSocketEvent.MessageReceived(chatMessage))
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
                        _events.tryEmit(ChatWebSocketEvent.MessageReceived(normalizedMsg))
                    } else {
                        val celebration = decodeEventPayload<ChatCelebration>(data) ?: return
                        val eventMessage = ChatMessage(
                            id = "celebration_${System.nanoTime()}_${celebration.id.ifBlank { celebration.username }}",
                            type = ChatMessageType.CELEBRATION,
                            sender = celebration.sender,
                            metadata = ChatMessageMetadata(celebration = celebration)
                        )
                        addMessage(eventMessage)
                        _events.tryEmit(ChatWebSocketEvent.Celebration(celebration))
                    }
                }

                "App\\Events\\PinnedMessageCreatedEvent",
                "PinnedMessageCreatedEvent" -> {
                    val data = rawData ?: return
                    val pinnedInfo = decodeEventPayload<PinnedMessageInfo>(data)?.withSelectedBadgesOnly() ?: return
                    if (pinnedInfo.message != null) {
                        _pinnedMessage.value = pinnedInfo
                        _events.tryEmit(ChatWebSocketEvent.PinnedMessageCreated(pinnedInfo))
                    }
                }

                "App\\Events\\PinnedMessageDeletedEvent",
                "PinnedMessageDeletedEvent" -> {
                    _pinnedMessage.value = null
                    _events.tryEmit(ChatWebSocketEvent.PinnedMessageDeleted)
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
                    _events.tryEmit(ChatWebSocketEvent.RewardRedeemed(event))
                }

                "GiftedSubscriptionsEvent",
                "App\\Events\\GiftedSubscriptionsEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<GiftedSubscriptionsEvent>(data) ?: return
                    val recipientMessages = event.toRecipientMessages()
                    recipientMessages.forEach(::addMessage)
                    _events.tryEmit(ChatWebSocketEvent.GiftedSubscriptions(event))
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
                    _events.tryEmit(ChatWebSocketEvent.ChannelSubscribed(event))
                }

                "App\\Events\\UserBannedEvent",
                "UserBannedEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<UserBannedEvent>(data) ?: return
                    _events.tryEmit(ChatWebSocketEvent.UserBanned(event))
                }

                "App\\Events\\MessageDeletedEvent",
                "MessageDeletedEvent" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<MessageDeletedEvent>(data) ?: return
                    markMessageDeleted(event)
                    _events.tryEmit(ChatWebSocketEvent.MessageDeleted(event))
                }

                "PointsUpdated",
                "App\\Events\\PointsUpdated" -> {
                    if (!com.lechixy.kick.data.channel.policy.ChannelPointsPolicy.acceptsPublication(
                            pushFrame.push?.channel ?: pushFrame.channel, authenticatedUserId)) return
                    val data = rawData ?: return
                    val event = decodeEventPayload<PointsUpdatedEvent>(data) ?: return
                    if (!com.lechixy.kick.data.channel.policy.ChannelPointsPolicy.accepts(event, channelId, authenticatedUserId)) return
                    _pointsUpdated.value = event
                    _events.tryEmit(ChatWebSocketEvent.PointsUpdated(event))
                }

                "App\\Events\\StreamerIsLive",
                "StreamerIsLive" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<StreamerIsLiveEvent>(data) ?: return
                    _events.tryEmit(ChatWebSocketEvent.StreamerIsLive(event))
                }

                "App\\Events\\StopStreamBroadcast",
                "StopStreamBroadcast" -> {
                    val data = rawData ?: return
                    val event = decodeEventPayload<StopStreamBroadcastEvent>(data) ?: return
                    _events.tryEmit(ChatWebSocketEvent.StopStreamBroadcast(event))
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

    private fun scheduleReconnect(channelId: Long, chatroomId: Long, delayMs: Long = 2500L) {
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        reconnectJob = clientScope.launch {
            delay(delayMs)
            if (!isIntentionallyClosed && currentChannelId == channelId && currentChatroomId == chatroomId) {
                Log.d(tag, "Attempting reconnect...")
                startConnectionFlow(channelId, chatroomId)
            }
        }
    }

    fun refreshAccountConnection() {
        val channelId = currentChannelId ?: return
        val chatroomId = currentChatroomId ?: return
        if (isIntentionallyClosed) return
        disconnectInternal(clearMessages = false)
        connect(channelId, chatroomId)
    }

    fun disconnect() {
        isIntentionallyClosed = true
        disconnectInternal(clearMessages = true)
    }

    @Synchronized
    private fun disconnectInternal(clearMessages: Boolean = true) {
        batchJob?.cancel()
        if (!clearMessages) flushMessages(sample = false)
        batcher.clear()
        _highTraffic.value = false
        connectionJob?.cancel()
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        // Private publications belong to this connection's account, even when keeping public chat history.
        _pointsUpdated.value = null
        webSocket?.close(1000, "Disconnect requested")
        webSocket = null
        if (clearMessages) {
            currentChannelId = null
            currentChatroomId = null
            _messages.value = emptyList()
            _pinnedMessage.value = null
            _userColors.value = emptyMap()
        }
        _connectionState.value = ChatConnectionState.DISCONNECTED
    }
}

