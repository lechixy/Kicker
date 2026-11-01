package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageType
import org.junit.Assert.*
import org.junit.Test

class LiveChatBatcherTest {
    @Test fun ordinaryTrafficPreservesOrderAndDeduplicatesPendingMessages() {
        val batcher = LiveChatBatcher()
        batcher.add(ChatMessage(id = "a"), 0)
        batcher.add(ChatMessage(id = "b"), 100)
        batcher.add(ChatMessage(id = "a", content = "updated"), 200)
        val batch = batcher.drain(300)
        assertEquals(listOf("a", "b"), batch.map { it.id })
        assertEquals("updated", batch.first().content)
        assertFalse(batcher.isBusy(300))
        assertTrue(batcher.drain(400).isEmpty())
    }

    @Test fun fiftyMessagesPerSecondAreLimitedToTwentyVisibleRows() {
        val batcher = LiveChatBatcher()
        repeat(20) { batcher.add(ChatMessage(id = "warmup$it"), it * 20L) }
        batcher.drain(400)
        var visible = 0
        repeat(4) { window ->
            repeat(13) { index ->
                batcher.add(ChatMessage(id = "$window-$index"), 500L + window * 250 + index * 19)
            }
            val batch = batcher.drain(750L + window * 250)
            visible += batch.size
            assertEquals("$window-12", batch.last().id)
        }
        assertEquals(20, visible)
        assertTrue(batcher.isBusy(1_500))
    }

    @Test fun floodKeepsServiceEventsAndLatestChatMessagesInOrder() {
        val batcher = LiveChatBatcher()
        repeat(30) { batcher.add(ChatMessage(id = "$it"), it.toLong()) }
        batcher.add(ChatMessage(id = "subscription", type = ChatMessageType.CHANNEL_SUBSCRIPTION), 30)
        batcher.add(ChatMessage(id = "reply", type = ChatMessageType.REPLY), 31)
        assertEquals(listOf("26", "27", "28", "29", "subscription", "reply"),
            batcher.drain(250).map { it.id })
    }

    @Test fun pendingWorkIsBoundedDuringExtremeFloods() {
        val batcher = LiveChatBatcher()
        repeat(50_000) { batcher.add(ChatMessage(id = "$it"), 0) }
        val batch = batcher.drain(100, sample = false)
        assertEquals(250, batch.size)
        assertEquals("49750", batch.first().id)
        assertEquals("49999", batch.last().id)
    }

    @Test fun trafficWindowAndRecoveryDoNotStayBusyAfterQuietPeriod() {
        val batcher = LiveChatBatcher()
        repeat(19) { batcher.add(ChatMessage(id = "$it"), 0) }
        batcher.add(ChatMessage(id = "later"), 1_000)
        assertFalse(batcher.isBusy(1_000))
        repeat(19) { batcher.add(ChatMessage(id = "burst$it"), 1_000) }
        assertTrue(batcher.isBusy(5_999))
        assertFalse(batcher.isBusy(6_000))
        assertEquals(39, batcher.drain(6_000).size)
    }

    @Test fun disconnectClearsPendingWorkAndTrafficState() {
        val batcher = LiveChatBatcher()
        repeat(30) { batcher.add(ChatMessage(id = "$it"), 0) }
        batcher.clear()
        assertFalse(batcher.isBusy(1))
        assertTrue(batcher.drain(1).isEmpty())
        batcher.add(ChatMessage(id = "new-channel"), 2)
        assertEquals(listOf("new-channel"), batcher.drain(3).map { it.id })
    }
}
