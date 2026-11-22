package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageType
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDeliveryTest {
    private val connectedAt = Instant.parse("2026-11-22T12:00:00Z")
    private val now = connectedAt.plusSeconds(45)
    private val delivered = ChatMessage("delivered", content = "Hi", createdAt = connectedAt.plusSeconds(20).toString())
    private val missing = delivered.copy(id = "missing")

    @Test
    fun historyShowsMessagesThatDidNotArriveOnTheSocket() {
        assertTrue(historyHasMissingChatMessages(listOf(delivered, missing), setOf(delivered.id), connectedAt, now))
    }

    @Test
    fun quietRoomsAndHistoryFromBeforeTheSubscriptionKeepTheConnection() {
        val old = missing.copy(createdAt = connectedAt.minusSeconds(1).toString())
        assertFalse(historyHasMissingChatMessages(listOf(delivered, old), setOf(delivered.id), connectedAt, now))
    }

    @Test
    fun newMessagesAndNonChatEventsDoNotTriggerReconnect() {
        val tooRecent = missing.copy(createdAt = now.minusSeconds(1).toString())
        val event = missing.copy(type = ChatMessageType.CELEBRATION)
        assertFalse(historyHasMissingChatMessages(listOf(tooRecent, event), emptySet(), connectedAt, now))
    }
}
