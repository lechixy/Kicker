package com.lechixy.kick.data.chat.repository

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChatroomDetail

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatSender
import com.lechixy.kick.data.chat.model.EmoteCategoryKind
import com.lechixy.kick.data.chat.repository.AccountChatRepository

class ChatInputRepositoryTest {
    private val channel = ChannelDetail(id = 100, userId = 999, slug = "tester", chatroom = ChatroomDetail(id = 777))
    private fun access(request: Request) = when {
        request.url.encodedPath.endsWith("/settings") -> objectJson("""{"data":{
            "allow_links":{"enabled":true},"emotes_only_mode":{"enabled":false},
            "subscribers_only_mode":{"enabled":false},"followers_only_mode":{"enabled":false},
            "minimum_account_age":{"enabled":false},"slow_mode":{"enabled":false}}}""")
        request.url.encodedPath.endsWith("/me") -> objectJson("""{"subscription":null,"banned":null,"is_following":true,
            "is_broadcaster":false,"is_moderator":true,"is_super_admin":false}""")
        else -> objectJson("{}")
    }
    @Test fun replyUsesTheMessageEndpointAndRejectsAnotherChatroomBeforePosting() = runBlocking {
        var posts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") {
                posts++
                assertEquals("/api/v2/messages/send/777", request.url.encodedPath)
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                val payload = objectJson(buffer.readUtf8())
                assertEquals("reply", payload["type"]!!.jsonPrimitive.content)
                assertEquals("source", payload["metadata"]!!.jsonObject["original_message"]!!.jsonObject["id"]!!.jsonPrimitive.content)
                objectJson("""{"status":{"error":false},"data":{"id":"sent-id"}}""")
            } else access(request)
        }
        val repo = AccountChatRepository(auth)
        val source = ChatMessage(id = "source", chatroomId = 778, content = "hi", sender = ChatSender(id = 5, username = "alice"))
        assertTrue(runCatching { repo.send(channel, "hello", source) }.isFailure)
        assertEquals(0, posts)
        assertEquals("sent-id", repo.send(channel, "hello", source.copy(chatroomId = 777)))
        assertEquals(1, posts)
    }
    @Test fun moderationCommandNeverPostsAsAnOrdinaryMessage() = runBlocking {
        var commandPosts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") {
                commandPosts++
                assertEquals("/api/v2/channels/tester/chat-commands", request.url.encodedPath)
                val buffer = Buffer(); request.body!!.writeTo(buffer)
                assertEquals(objectJson("""{"command":"clear"}"""), objectJson(buffer.readUtf8()))
                objectJson("{}")
            } else access(request)
        }
        assertNull(AccountChatRepository(auth).send(channel, "/clear"))
        assertEquals(1, commandPosts)
    }
    @Test fun ownedEmotesAndPointSettingsUseAuthenticatedReadRequests() = runBlocking {
        val auth = signedInAuth { request ->
            assertEquals("Bearer access", request.header("Authorization"))
            when (request.url.encodedPath) {
                "/emotes/tester" -> Json.parseToJsonElement("""[{"name":"Collectibles","emotes":[{"id":99,"name":"collectiblesFire"}]}]""")
                else -> objectJson("""{"data":{"channel_points_enabled":false,"mobile_watch_event_interval_seconds":120}}""")
            }
        }
        val repo = AccountChatRepository(auth)
        assertEquals(EmoteCategoryKind.COLLECTIBLES, repo.emotes("tester").single().kind)
        assertFalse(repo.channelPointSettings(channel).enabled)
        assertEquals(120L, repo.channelPointSettings(channel).mobileIntervalSeconds)
    }
}
