package com.lechixy.kick.data.chat.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatHistoryTimelineTest {
    @Test fun descendingHistoryIsChronologicalBeforeLiveMessages() {
        val old = ChatMessage(id = "old", createdAt = "2026-10-30T18:54:43Z")
        val recent = ChatMessage(id = "recent", createdAt = "2026-10-30T18:55:59Z")
        val live = ChatMessage(id = "live")
        val messages = mergeChatHistory(listOf(recent, old), listOf(live), 250)
        assertEquals(listOf("old", "recent", "live"), messages.map { it.id })
        assertEquals(listOf(true, true, false), messages.map { it.isHistory })
    }

    @Test fun lateHistoryDoesNotDuplicateOrReplaceLiveCopies() {
        val live = ChatMessage(id = "overlap", content = "live copy")
        val history = listOf(ChatMessage(id = "older"), ChatMessage(id = "overlap"), ChatMessage(id = "older"))
        val once = mergeChatHistory(history, listOf(live), 250)
        assertEquals(listOf("older", "overlap"), once.map { it.id })
        assertEquals(live, once.last())
        assertEquals(once, mergeChatHistory(history, once, 250))
    }

    @Test fun bufferLimitDropsOldestHistoryBeforeLiveMessages() {
        val history = (1..4).map { ChatMessage(id = "$it", createdAt = "2026-10-30T18:54:0${it}Z") }
        val result = mergeChatHistory(history, listOf(ChatMessage(id = "live")), 3)
        assertEquals(listOf("3", "4", "live"), result.map { it.id })
        assertEquals(listOf(ChatMessage(id = "live")), mergeChatHistory(history, result.takeLast(1), 1))
    }

    @Test fun historyUsesExistingReplyAndStringMetadataParser() {
        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<ChatHistoryResponse>(
            """{
                "data": {
                    "messages": [{
                        "id": "reply", "chat_id": 26670447, "user_id": 42,
                        "content": "hello", "type": "reply", "thread_parent_id": "parent",
                        "metadata": "{\"original_message\":{\"id\":\"parent\",\"content\":\"original\"},\"message_ref\":\"1793386483600\"}",
                        "sender": {"id": 42, "username": "viewer", "identity": {
                            "badges_v2": [{"name": "level", "selected": true, "metadata": {"level": 36}}]
                        }}, "created_at": "2026-10-30T18:54:43Z"
                    }],
                    "cursor": "1793386453755206",
                    "pinned_message": {"message": {"id": "pin", "content": "pinned"},
                        "pinned_by": {"username": "moderator"}}
                }, "message": "OK"
            }"""
        )
        val message = response.data!!.messages.single()
        assertTrue(message.isReply)
        assertEquals("parent", message.metadata?.originalMessage?.id)
        assertEquals("original", message.metadata?.originalMessage?.content)
        assertEquals(26670447L, message.chatId)
        assertEquals("moderator", response.data!!.pinnedMessage?.pinnedBy?.username)
        assertEquals("pinned", response.data!!.pinnedMessage?.message?.content)
        assertTrue(mergeChatHistory(listOf(message), emptyList(), 250).single().isHistory)
        assertFalse(json.encodeToString(ChatMessage.serializer(), message.copy(isHistory = true)).contains("isHistory"))
    }
}
