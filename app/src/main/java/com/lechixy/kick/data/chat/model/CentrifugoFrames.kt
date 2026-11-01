package com.lechixy.kick.data.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ConnectPayload(
    val token: String,
    val name: String = "js"
)

@Serializable
data class CentrifugoConnectFrame(
    val connect: ConnectPayload,
    val id: Int
)

@Serializable
data class SubscribePayload(
    val channel: String,
    val flag: Int = 1,
    val token: String? = null
)

@Serializable
data class CentrifugoSubscribeFrame(
    val subscribe: SubscribePayload,
    val id: Int
)

@Serializable
data class CentrifugoPushFrame(
    val push: CentrifugoPushData? = null,
    val channel: String? = null,
    val pub: CentrifugoPublication? = null
)

@Serializable
data class CentrifugoPushData(
    val channel: String? = null,
    val pub: CentrifugoPublication? = null
)

@Serializable
data class CentrifugoPublication(
    val data: CentrifugoEventData? = null,
    val tags: Map<String, String>? = null
) {
    val eventName: String?
        get() = data?.event?.trim()?.takeIf { it.isNotEmpty() } ?: tags?.get("event")?.trim()
}

@Serializable
data class CentrifugoEventData(
    val event: String? = null,
    val data: JsonElement? = null
)


/** Separates complete protocol objects, including adjacent objects and newline batches. */
internal fun splitCentrifugoFrames(message: String): List<String> {
    val frames = mutableListOf<String>()
    var start = -1
    var depth = 0
    var inString = false
    var escaped = false
    message.forEachIndexed { index, char ->
        if (start == -1) {
            if (char == '{') { start = index; depth = 1 }
        } else if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        frames += message.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
    }
    return frames
}
