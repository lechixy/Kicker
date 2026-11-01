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
internal class RecordedChatTimeline(val start: Instant) {
    private val messages = linkedMapOf<String, ChatMessage>()

    fun requestTime(positionMs: Long): String = DateTimeFormatterBuilder().appendInstant(3).toFormatter()
        .format(start.plusMillis(positionMs.coerceAtLeast(0)))

    fun clear() = messages.clear()

    fun add(batch: List<ChatMessage>, positionMs: Long) {
        batch.forEach { message ->
            val time = recordingStartInstant(message.createdAt) ?: return@forEach
            val offset = time.toEpochMilli() - start.toEpochMilli()
            if (message.id.isNotBlank() && offset >= 0 && offset in (positionMs - 120_000)..(positionMs + 60_000)) {
                messages[message.id] = message.withSelectedBadgesOnly().copy(recordingOffsetMs = offset, isHistory = false)
            }
        }
        messages.entries.removeAll { (it.value.recordingOffsetMs ?: 0) < positionMs - 120_000 }
        if (messages.size > 2000) {
            val keep = messages.values.sortedBy { it.recordingOffsetMs }.takeLast(2000).mapTo(hashSetOf()) { it.id }
            messages.keys.retainAll(keep)
        }
    }

    fun visible(positionMs: Long): List<ChatMessage> = messages.values
        .filter { it.recordingOffsetMs!! <= positionMs && it.recordingOffsetMs >= positionMs - 120_000 }
        .sortedWith(compareBy<ChatMessage> { it.recordingOffsetMs }.thenBy { it.id })
        .takeLast(500)
}
