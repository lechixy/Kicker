package com.lechixy.kick.data.channel.model

import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class ChannelReward(
    val id: String,
    val title: String,
    val cost: Long,
    val description: String = "",
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("is_user_input_required") val isUserInputRequired: Boolean = false,
    val prompt: String = "",
    @SerialName("background_color") val backgroundColor: String = "#53FC18",
    @SerialName("should_redemptions_skip_request_queue") val skipRequestQueue: Boolean = false
)

private val rewardsJson = Json { ignoreUnknownKeys = true }

internal fun parseChannelRewards(response: JsonElement): List<ChannelReward> {
    val data = when (response) {
        is JsonArray -> response
        is JsonObject -> response["data"] as? JsonArray ?: error("Missing rewards data")
        else -> error("Invalid rewards response")
    }
    return rewardsJson.decodeFromJsonElement<List<ChannelReward>>(data)
        .also { rewards -> check(rewards.all { it.id.isNotBlank() && it.cost >= 0 }) { "Invalid reward" } }
}

internal fun formatExactBalance(balance: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(balance)
