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

    fun requestTime(positionMs: Long): String = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
        .format(start.plusMillis(positionMs.coerceAtLeast(0)))

    fun clear() = messages.clear()

    fun add(batch: List<ChatMessage>, positionMs: Long) {
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
        if (end == null && messages.size > 2000) {
            val keep = messages.values.sortedBy { it.recordingOffsetMs }.takeLast(2000).mapTo(hashSetOf()) { it.id }
            messages.keys.retainAll(keep)
        }
    }

    fun visible(positionMs: Long, limit: Int = 500): List<ChatMessage> {
        val position = endMs?.let(positionMs::coerceAtMost) ?: positionMs
        return messages.values
            .filter { it.recordingOffsetMs!! <= position && it.recordingOffsetMs >= position - historyWindowMs }
            .sortedWith(compareBy<ChatMessage> { it.recordingOffsetMs }.thenBy { it.id })
            .takeLast(limit)
    }
}
