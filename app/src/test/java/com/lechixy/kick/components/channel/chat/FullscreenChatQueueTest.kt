package com.lechixy.kick.components.channel.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FullscreenChatQueueTest {
    @Test fun initialHistoryStartsWithLatestMessage() {
        val queue = FullscreenChatQueue()
        val ids = listOf("old", "latest")
        queue.update(ids, ids)
        assertEquals("latest", queue.next())
        assertNull(queue.next())
    }

    @Test fun burstsKeepFourNewestWaitingMessagesInOrder() {
        val queue = FullscreenChatQueue()
        queue.update(emptyList(), emptyList())
        val ids = (1..7).map(Int::toString)
        queue.update(ids, ids)
        assertEquals(listOf("4", "5", "6", "7"), List(4) { queue.next() })
        queue.update(ids, ids)
        assertNull(queue.next())
    }

    @Test fun removingEligibilityDropsWaitingMessagesWithoutReplayingThem() {
        val queue = FullscreenChatQueue()
        queue.update(emptyList(), emptyList())
        val ids = listOf("deleted", "muted", "expired", "visible")
        queue.update(ids, ids)
        queue.update(ids, listOf("visible"))
        assertEquals("visible", queue.next())
        queue.update(ids, listOf("muted", "visible"))
        assertNull(queue.next())
    }
}
