package com.lechixy.kick.data.chat.model

import kotlinx.serialization.json.*

fun chatSendPayload(content: String, reply: ChatMessage? = null, messageRef: String = System.currentTimeMillis().toString()): JsonObject {
    if (reply != null) require(reply.id.isNotBlank() && !reply.isDeleted && reply.sender?.id != null)
    return buildJsonObject {
        put("content", content)
        put("type", if (reply == null) "message" else "reply")
        if (reply != null) {
            put("message_ref", messageRef)
            putJsonObject("metadata") {
                putJsonObject("original_message") { put("id", reply.id); put("content", reply.content) }
                putJsonObject("original_sender") { put("id", reply.sender!!.id); put("username", reply.sender.username) }
            }
        }
    }
}
