package com.lechixy.kick.data.notifications

import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationKind { WatchTimeGoal, StreamStarted }
data class AppNotification(val id: String, val kind: NotificationKind, val userId: Long, val generation: Long,
    val title: String = "", val description: String = "", val channelSlug: String? = null,
    val thresholdMinutes: Int = 60, val preview: Boolean = false)

internal fun decodeAccountNotification(source: String?, event: String, raw: JsonElement, userId: Long, generation: Long): AppNotification? {
    val payload = runCatching {
        (if (raw is JsonPrimitive && raw.isString) Json.parseToJsonElement(raw.content) else raw).jsonObject
    }.getOrNull() ?: return null
    fun text(key: String) = (payload[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    if (text("user_id").toLongOrNull() != userId) return null
    return when (event.substringAfterLast('\\')) {
        "NotifyFollowersStreamHasStarted" -> {
            if (source != "private-userfeed.$userId") return null
            val slug = text("path").removePrefix("/")
            if (!Regex("[A-Za-z0-9_][A-Za-z0-9_-]{0,99}").matches(slug)) return null
            AppNotification("live:$slug:${text("title")}:${text("description")}", NotificationKind.StreamStarted, userId, generation,
                text("title").take(500), text("description").take(1_000), slug)
        }
        "GamificationWatchTimeGoalReached" -> {
            if (source != "private-$userId" || text("challenge_id").isBlank()) return null
            val threshold = text("threshold_minutes").toIntOrNull()?.takeIf { it > 0 } ?: return null
            AppNotification("goal:${text("challenge_id")}:${text("recurrence")}", NotificationKind.WatchTimeGoal,
                userId, generation, thresholdMinutes = threshold)
        }
        else -> null
    }
}

/** Bounded FIFO with short replay suppression. All mutation is serialized independently of WebSocket callbacks. */
internal class NotificationQueue {
    private val mutable = MutableStateFlow<List<AppNotification>>(emptyList())
    val items = mutable.asStateFlow()
    private val seen = LinkedHashMap<String, Long>()
    @Synchronized fun add(item: AppNotification, nowMs: Long) {
        if (mutable.value.any { it.id == item.id } || seen[item.id]?.let { nowMs - it < 60_000 } == true) return
        seen[item.id] = nowMs
        while (seen.size > 100) seen.remove(seen.keys.first())
        // Keep the visible notification stable when dropping overflow.
        mutable.value = if (mutable.value.size < 5) mutable.value + item else mutable.value.take(1) + mutable.value.drop(2) + item
    }
    @Synchronized fun dismiss(id: String) { mutable.value = mutable.value.filterNot { it.id == id } }
    @Synchronized fun clear() { mutable.value = emptyList(); seen.clear() }
}
