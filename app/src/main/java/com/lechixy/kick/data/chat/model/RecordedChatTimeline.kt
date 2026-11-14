package com.lechixy.kick.data.chat.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder

internal fun recordingStartInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrElse {
        runCatching { LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC) }.getOrNull()
    }
}

/** A bounded replay buffer. Network ordering and duplicate history pages never set display order. */
internal class RecordedChatTimeline(
    val start: Instant,
    private val endMs: Long? = null,
) {
    private val historyWindowMs = endMs ?: 120_000L
    private val end = endMs?.let(start::plusMillis)
    private val messages = linkedMapOf<String, ChatMessage>()
    private var orderedMessages: List<ChatMessage>? = null
    private var lastVisibleRange = IntRange.EMPTY
    private var lastVisibleMessages = emptyList<ChatMessage>()
    var discardedThroughMs: Long? = null
        private set

    fun requestTime(positionMs: Long): String = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
        .format(start.plusMillis(positionMs.coerceAtLeast(0)))

    fun clear() {
        messages.clear()
        discardedThroughMs = null
        invalidateVisibleMessages()
    }

    fun add(batch: List<ChatMessage>, positionMs: Long) {
        invalidateVisibleMessages()
        batch.forEach { message ->
            val time = recordingStartInstant(message.createdAt) ?: return@forEach
            val offset = time.toEpochMilli() - start.toEpochMilli()
            if (message.id.isNotBlank() && !time.isBefore(start) &&
                offset in (positionMs - historyWindowMs)..(positionMs + 60_000) &&
                (end == null || !time.isAfter(end))
            ) {
                messages[message.id] = message.withSelectedBadgesOnly().copy(recordingOffsetMs = offset, isHistory = false)
            }
        }
        messages.entries.removeAll { (it.value.recordingOffsetMs ?: 0) < positionMs - historyWindowMs }
        if (messages.size > MAX_MESSAGES) {
            val sorted = messages.values.sortedWith(messageOrder)
            val removeCount = sorted.size - MAX_MESSAGES
            discardedThroughMs = maxOf(discardedThroughMs ?: 0, sorted[removeCount - 1].recordingOffsetMs!!)
            for (index in 0 until removeCount) messages.remove(sorted[index].id)
            orderedMessages = sorted.subList(removeCount, sorted.size).toList()
        }
    }

    fun visible(positionMs: Long, limit: Int = 500): List<ChatMessage> {
        require(limit >= 0)
        val position = endMs?.let(positionMs::coerceAtMost) ?: positionMs
        // Position updates are much more frequent than page loads. Sort once per
        // page and reuse the visible list until a message crosses either boundary.
        val ordered = orderedMessages ?: messages.values.sortedWith(messageOrder)
            .also { orderedMessages = it }
        val endIndex = ordered.firstOffsetAtLeast(position, inclusive = false)
        val startIndex = maxOf(ordered.firstOffsetAtLeast(position - historyWindowMs), endIndex - limit)
        val range = startIndex until endIndex
        if (range != lastVisibleRange) {
            lastVisibleRange = range
            lastVisibleMessages = ordered.subList(startIndex, endIndex).toList()
        }
        return lastVisibleMessages
    }

    private fun invalidateVisibleMessages() {
        orderedMessages = null
        lastVisibleRange = IntRange.EMPTY
        lastVisibleMessages = emptyList()
    }

    private fun List<ChatMessage>.firstOffsetAtLeast(offsetMs: Long, inclusive: Boolean = true): Int {
        var low = 0
        var high = size
        while (low < high) {
            val mid = low + (high - low) / 2
            val offset = this[mid].recordingOffsetMs!!
            if (offset < offsetMs || (!inclusive && offset == offsetMs)) low = mid + 1 else high = mid
        }
        return low
    }

    private companion object {
        const val MAX_MESSAGES = 2000
        val messageOrder = compareBy<ChatMessage> { it.recordingOffsetMs }.thenBy { it.id }
    }
}
