package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.account.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal suspend fun AuthRepository.realtimeConnectionToken(clientId: String): String {
    val response = request(
        "https://web.kick.com/api/v1/realtime/auth/connection", "POST",
        buildJsonObject { put("client_id", clientId) },
    )
    return ((response["data"] as? JsonObject)?.get("token") as? JsonPrimitive)
        ?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Missing realtime connection token")
}

internal suspend fun AuthRepository.realtimeSubscriptionToken(channel: String): String {
    val response = request(
        "https://web.kick.com/api/v1/realtime/auth/channel", "POST",
        buildJsonObject { put("channel", channel) },
    )
    return ((response["data"] as? JsonObject)?.get("token") as? JsonPrimitive)
        ?.contentOrNull?.takeIf(String::isNotBlank) ?: error("Missing realtime channel token")
}

/** A non-expiring reply ends the refresh loop and keeps the connection or subscription active. */
internal suspend fun maintainCentrifugoToken(
    initialReply: JsonObject,
    refresh: suspend () -> JsonObject?,
): Boolean {
    var reply = initialReply
    while ((reply["expires"] as? JsonPrimitive)?.booleanOrNull == true) {
        val ttlSeconds = (reply["ttl"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 } ?: return false
        val ttlMillis = ttlSeconds * 1_000L
        delay(ttlMillis - (ttlMillis / 10).coerceAtMost(30_000))
        reply = refresh() ?: return false
    }
    return true
}
