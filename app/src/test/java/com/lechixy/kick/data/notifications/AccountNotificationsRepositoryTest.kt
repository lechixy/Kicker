package com.lechixy.kick.data.notifications

import com.lechixy.kick.data.account.repository.ChallengesRepository
import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun accountFeedAuthorizesEachTopicAndReceivesNotifications() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val opened = CompletableDeferred<TestSocket>()
        val levelRequests = AtomicInteger()
        val animatedBadge = "https://ext.cdn.kick.com/chat/badges/27.gif"
        val auth = signedInAuth(userId = 29081495) { request ->
            when (request.url.encodedPath) {
                "/api/v1/realtime/connection" -> objectJson("""{"data":{"connections":[
                    {"provider":"pusher","credentials":{"app_key":"key","cluster":"us2"}},
                    {"provider":"centrifugo","credentials":{"url":"wss://realtime.example/connection/websocket"}}
                ]}}""")
                "/api/v1/realtime/auth/connection" -> objectJson("""{"data":{"token":"connection-token"}}""")
                "/api/v1/gamification/user/level" -> {
                    val level = if (levelRequests.incrementAndGet() == 1) 26 else 27
                    val animatedImage = if (level == 27) animatedBadge else "https://ext.cdn.kick.com/chat/badges/26.gif"
                    objectJson("""{"data":{"level":$level,"progress_xp":21,"xp_to_next_level":2665,
                        "badge":{"animated_image_url":"$animatedImage","image_url":"badge.png"}}}""")
                }
                "/api/v1/realtime/auth/channel" -> {
                    assertEquals("POST", request.method)
                    val body = Buffer().also { request.body!!.writeTo(it) }
                    val channel = objectJson(body.readUtf8()).getValue("channel").jsonPrimitive.content
                    assertTrue(channel == "private-userfeed.29081495" || channel == "private-29081495")
                    objectJson("""{"data":{"token":"token-$channel"}}""")
                }
                else -> objectJson("{}")
            }
        }
        val factory = object : WebSocket.Factory {
            override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket =
                TestSocket(request, listener).also { opened.complete(it) }
        }
        val repository = AccountNotificationsRepository(auth, ChallengesRepository(auth, scope), scope, factory) { 1L }
        try {
            withTimeout(10_000) {
                repository.setForeground(true)
                val socket = opened.await()
                assertEquals("realtime.example", socket.request().url.host)
                socket.open()
                val connect = socket.commands.receive()
                assertEquals("connection-token", connect.getValue("connect").jsonObject.getValue("token").jsonPrimitive.content)
                assertNull(socket.commands.tryReceive().getOrNull())
                socket.reply(connect, """"connect":{}""")

                val subscriptions = List(2) { socket.commands.receive() }.associateBy {
                    it.getValue("subscribe").jsonObject.getValue("channel").jsonPrimitive.content
                }
                subscriptions.forEach { (channel, command) ->
                    assertEquals("token-$channel", command.getValue("subscribe").jsonObject.getValue("token").jsonPrimitive.content)
                }
                socket.reply(subscriptions.getValue("private-userfeed.29081495"), """"error":{"code":109}""")
                socket.reply(subscriptions.getValue("private-29081495"), """"subscribe":{}""")
                socket.push("""{"push":{"channel":"private-29081495","pub":{"data":{
                    "data":"{\"user_id\":29081495,\"challenge_id\":\"daily-1\",\"recurrence\":\"daily\",\"threshold_minutes\":60}"
                },"tags":{"event":"App\\Events\\GamificationWatchTimeGoalReached"}}}}""")
                val drop = repository.notifications.first { it.isNotEmpty() }.single()
                assertEquals(NotificationKind.WatchTimeGoal, drop.kind)
                assertEquals(60, drop.thresholdMinutes)

                val retry = socket.commands.receive()
                assertEquals("private-userfeed.29081495", retry.getValue("subscribe").jsonObject.getValue("channel").jsonPrimitive.content)
                socket.reply(retry, """"subscribe":{}""")
                socket.push("""{
                    "push":{"channel":"private-userfeed.29081495","pub":{
                        "data":{"event":"NotifyFollowersStreamHasStarted",
                            "data":"{\"user_id\":29081495,\"title\":\"\\u003cstrong\\u003eGokhanOner\\u003c/strong\\u003e just went live!\",\"description\":\"13 fps Fransizca Dublaj | !sonvideo !kopazar\",\"path\":\"/gokhanoner\",\"profile_picture\":\"https://files.kick.com/images/user/26068857/profile_image/conversion/f3cf7de9-c590-4a2d-9000-69c56a647d4b-medium.webp\"}"},
                        "tags":{"event":"NotifyFollowersStreamHasStarted"}
                    }}
                }""")
                val live = repository.notifications.first { it.size == 2 }.last()
                assertEquals(NotificationKind.StreamStarted, live.kind)
                assertEquals("gokhanoner", live.channelSlug)
                assertEquals("<strong>GokhanOner</strong> just went live!", live.title)

                val levelUp = """{"push":{"channel":"private-29081495","pub":{
                    "data":{"event":"GamificationLevelUp",
                        "data":"{\"user_id\":29081495,\"level\":27,\"total_xp\":22590}"},
                    "tags":{"event":"GamificationLevelUp"}
                }}}"""
                socket.push(levelUp.replace("private-29081495", "private-userfeed.29081495"))
                socket.push(levelUp.replace("""\"user_id\":29081495""", """\"user_id\":42"""))
                assertEquals(0, levelRequests.get())
                socket.push(levelUp)
                val reachedLevel = repository.notifications.first { it.size == 3 }.last()
                assertEquals(NotificationKind.LevelUp, reachedLevel.kind)
                assertEquals(27, reachedLevel.level)
                assertEquals(animatedBadge, reachedLevel.animatedBadgeUrl)
                assertEquals(2, levelRequests.get())

                repository.setForeground(false)
                socket.cancelled.await()
                assertEquals(emptyList<AppNotification>(), repository.notifications.first { it.isEmpty() })
            }
        } finally {
            scope.cancel()
        }
    }

    private class TestSocket(private val request: Request, private val listener: WebSocketListener) : WebSocket {
        val commands = Channel<JsonObject>(Channel.UNLIMITED)
        val cancelled = CompletableDeferred<Unit>()

        fun open() = listener.onOpen(this,
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
        fun reply(command: JsonObject, result: String) = push("""{"id":${command.getValue("id")},$result}""")
        fun push(frame: String) = listener.onMessage(this, frame)
        override fun request() = request
        override fun queueSize() = 0L
        override fun send(text: String) = commands.trySend(objectJson(text)).isSuccess
        override fun send(bytes: ByteString) = false
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled.complete(Unit) }
    }
}
