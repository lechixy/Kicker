package com.lechixy.kick.data.chat.model

import java.time.Instant

/** Keep live copies (including moderation changes) when a late history response overlaps them. */
internal fun mergeChatHistory(
    history: List<ChatMessage>,
    current: List<ChatMessage>,
    limit: Int
): List<ChatMessage> {
    val currentIds = current.mapTo(hashSetOf()) { it.id }
    val older = history.asSequence()
        .filter { it.id.isNotBlank() && it.id !in currentIds }
        .distinctBy { it.id }
        .sortedBy { runCatching { Instant.parse(it.createdAt) }.getOrDefault(Instant.MIN) }
        .map { it.copy(isHistory = true) }
        .toList()
    return (older + current).takeLast(limit)
}
