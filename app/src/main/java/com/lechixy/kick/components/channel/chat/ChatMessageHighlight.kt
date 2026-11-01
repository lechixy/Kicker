package com.lechixy.kick.components.channel.chat

import com.lechixy.kick.data.chat.model.ChatMessage

/** Prefer the immutable account ID; names are only a fallback for incomplete chat payloads. */
internal fun isOwnChatMessage(message: ChatMessage, userId: Long?, username: String?): Boolean {
    if (userId == null) return false
    val senderId = message.sender?.id ?: message.userId
    if (senderId != null) return senderId == userId
    return !username.isNullOrBlank() &&
        (message.sender?.username.equals(username, ignoreCase = true) || message.sender?.slug.equals(username, ignoreCase = true))
}
