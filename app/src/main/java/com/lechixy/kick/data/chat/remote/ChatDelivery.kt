package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatMessageType
import java.time.Instant
import java.time.format.DateTimeParseException

internal fun chatMessageTime(createdAt: String?): Instant? {
    return try {
        createdAt?.let(Instant::parse)
    } catch (_: DateTimeParseException) {
        null
    }
}

internal fun historyHasMissingChatMessages(
    history: List<ChatMessage>,
    knownIds: Set<String>,
    after: Instant,
    now: Instant,
): Boolean {
    // Allow history and real-time delivery to arrive in either order.
    val settledBefore = now.minusSeconds(5)
    return history.any { message ->
        message.type == ChatMessageType.MESSAGE && message.content.isNotBlank() &&
            message.id.isNotBlank() && message.id !in knownIds &&
            chatMessageTime(message.createdAt)?.let { it > after && it <= settledBefore } == true
    }
}
