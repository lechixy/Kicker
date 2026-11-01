package com.lechixy.kick.data.chat.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Immutable
@Serializable
data class EmoteDto(
    val id: Long,
    @SerialName("channel_id") val channelId: Long? = null,
    val name: String,
    @SerialName("subscribers_only") val subscribersOnly: Boolean = false
) {
    val imageUrl get() = "https://files.kick.com/emotes/$id/fullsize"
    val messageToken get() = "[emote:$id:$name]"
}

enum class EmoteCategoryKind { FREQUENT, CHANNEL, GLOBAL, EMOJIS, COLLECTIBLES }

data class EmoteCategory(
    val id: String,
    val title: String,
    val kind: EmoteCategoryKind,
    val emotes: List<EmoteDto>,
    val profilePicUrl: String? = null
)

private val emoteJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** Category IDs in this endpoint mix numbers and strings. Read their JSON content directly. */
fun parseEmoteCategories(payload: JsonElement): List<EmoteCategory> =
    (payload as? JsonArray).orEmpty().mapNotNull { element ->
        val category = element as? JsonObject ?: return@mapNotNull null
        val name = (category["name"] as? JsonPrimitive)?.contentOrNull
        val id = (category["id"] as? JsonPrimitive)?.contentOrNull ?: name?.lowercase() ?: return@mapNotNull null
        val kind = when (name?.lowercase()?.takeIf { it in listOf("collectibles", "global", "emoji", "emojis", "emotes") } ?: id.lowercase()) {
            "global" -> EmoteCategoryKind.GLOBAL
            "emoji", "emojis", "emotes" -> EmoteCategoryKind.EMOJIS
            "collectibles" -> EmoteCategoryKind.COLLECTIBLES
            else -> EmoteCategoryKind.CHANNEL
        }
        val user = category["user"] as? JsonObject
        val emotes = (category["emotes"] as? JsonArray).orEmpty().mapNotNull {
            runCatching { emoteJson.decodeFromJsonElement<EmoteDto>(it) }.getOrNull()
        }.distinctBy { it.id }
        EmoteCategory(id, (user?.get("username") as? JsonPrimitive)?.contentOrNull
            ?: (category["name"] as? JsonPrimitive)?.contentOrNull
            ?: (category["slug"] as? JsonPrimitive)?.contentOrNull ?: id,
            kind, emotes, (user?.get("profile_pic") as? JsonPrimitive)?.contentOrNull)
    }

fun filterEmotes(categories: List<EmoteCategory>, query: String): List<EmoteDto> =
    categories.flatMap { it.emotes }.distinctBy { it.id }.filter { it.name.contains(query.trim(), ignoreCase = true) }

data class EmotePickerUiState(
    val searchQuery: String = "",
    val categories: List<EmoteCategory> = emptyList(),
    val selectedCategoryIndex: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class EmoteInsertion(val text: String, val cursor: Int)

fun insertEmote(text: String, selectionStart: Int, selectionEnd: Int, emote: EmoteDto): EmoteInsertion {
    val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
    val token = (if (start > 0 && !text[start - 1].isWhitespace()) " " else "") + emote.messageToken + " "
    return EmoteInsertion(text.replaceRange(start, end, token), start + token.length)
}
