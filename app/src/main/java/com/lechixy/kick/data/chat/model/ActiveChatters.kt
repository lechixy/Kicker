package com.lechixy.kick.data.chat.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ActiveChatter(
    val username: String = "",
    val slug: String = "",
    @SerialName("profile_picture") val profilePicture: String? = null
)

@Serializable
data class ActiveChatters(
    val moderators: List<ActiveChatter> = emptyList(),
    val ogs: List<ActiveChatter> = emptyList(),
    val vips: List<ActiveChatter> = emptyList(),
    val chatters: List<ActiveChatter> = emptyList(),
    val bots: List<ActiveChatter> = emptyList(),
    @SerialName("total_count") val totalCount: Int = 0
) {
    val users: List<ActiveChatter> get() = (moderators + ogs + vips + chatters + bots)
        .distinctBy { it.username.lowercase(java.util.Locale.ROOT) }
    val listedCount: Int get() = users.size
    val isPartial: Boolean get() = totalCount > listedCount
}

private val activeChattersJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

fun parseActiveChatters(response: JsonObject): ActiveChatters {
    val payload = if ("data" in response) response["data"] as? JsonObject
        ?: error("Missing active chatters data") else response
    require((payload["total_count"] as? JsonPrimitive)?.intOrNull?.let { it >= 0 } == true) {
        "Missing active chatters total"
    }
    return activeChattersJson.decodeFromJsonElement(payload)
}
