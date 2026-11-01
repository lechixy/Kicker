package com.lechixy.kick.data.channel.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class KickChannelLinkDto(
    val id: Long,
    @SerialName("channel_id") val channelId: Long,
    val description: String? = null,
    val link: String? = null,
    val order: Int = 0,
    val title: String? = null,
    val image: KickLinkImageDto? = null
)

@Serializable
data class KickLinkImageDto(val url: String)

@Serializable
data class KickGoalDto(
    val id: String,
    @SerialName("channel_id") val channelId: String,
    val type: String,
    val status: String,
    @SerialName("target_value") val targetValue: Int,
    @SerialName("current_value") val currentValue: Int,
    @SerialName("progress_bar_emoji_id") val progressBarEmojiId: String? = null
)

data class ChannelSectionState<T>(val value: T? = null, val loading: Boolean = false,
    val failed: Boolean = false, val loaded: Boolean = false)

private val aboutJson = Json { ignoreUnknownKeys = true }
private fun aboutItems(root: JsonElement, key: String): JsonArray = when (root) {
    JsonNull -> JsonArray(emptyList())
    is JsonArray -> root
    is JsonObject -> when {
        root.containsKey("data") -> aboutItems(root.getValue("data"), key)
        root.containsKey(key) -> aboutItems(root.getValue(key), key)
        root.containsKey("id") -> JsonArray(listOf(root))
        else -> error("Missing $key")
    }
    else -> error("Invalid $key")
}

fun parseChannelLinks(root: JsonElement): List<KickChannelLinkDto> =
    aboutItems(root, "links").map { aboutJson.decodeFromJsonElement<KickChannelLinkDto>(it) }.sortedBy { it.order }

fun parseChannelGoals(root: JsonElement): List<KickGoalDto> =
    aboutItems(root, "goals").map { aboutJson.decodeFromJsonElement<KickGoalDto>(it) }
