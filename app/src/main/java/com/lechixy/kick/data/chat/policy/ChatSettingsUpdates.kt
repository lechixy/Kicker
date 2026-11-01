package com.lechixy.kick.data.chat.policy

import com.lechixy.kick.data.chat.model.ChatSettings

import com.lechixy.kick.data.chat.model.Mode

import kotlinx.serialization.json.*

/** Normalize both realtime schemas; missing fields must preserve the current policy. */
object ChatSettingsUpdates {
    fun normalize(payload: JsonObject): JsonObject {
        (payload["settings"] as? JsonObject)?.let { return it }
        return buildJsonObject {
            val aliases = mapOf("subscribers_mode" to "subscribers_only_mode",
                "emotes_mode" to "emotes_only_mode", "followers_mode" to "followers_only_mode",
                "account_age" to "minimum_account_age", "slow_mode" to "slow_mode")
            aliases.forEach { (old, canonical) ->
                val mode = payload[old] as? JsonObject ?: return@forEach
                put(canonical, buildJsonObject {
                    mode["enabled"]?.let { put("enabled", it) }
                    val seconds = if (old == "slow_mode") mode["message_interval"]?.jsonPrimitive?.longOrNull
                        else mode["min_duration"]?.jsonPrimitive?.longOrNull?.times(60)
                    seconds?.let { put("duration_seconds", it.coerceIn(0, 31_536_000)) }
                })
            }
        }
    }

    fun merge(previous: JsonObject, patch: JsonObject): JsonObject = JsonObject(previous.toMutableMap().apply {
        patch.forEach { (key, value) ->
            val old = get(key) as? JsonObject
            put(key, if (old != null && value is JsonObject) merge(old, value) else value)
        }
    })

    fun apply(settings: ChatSettings, patch: JsonObject): ChatSettings {
        fun enabled(key: String, fallback: Boolean) = (patch[key] as? JsonObject)
            ?.get("enabled")?.jsonPrimitive?.booleanOrNull ?: fallback
        fun mode(key: String, old: Mode) = Mode(enabled(key, old.enabled),
            (patch[key] as? JsonObject)?.get("duration_seconds")?.jsonPrimitive?.longOrNull
                ?.coerceIn(0, 31_536_000) ?: old.durationSeconds)
        return settings.copy(allowLinks = enabled("allow_links", settings.allowLinks),
            emotesOnly = enabled("emotes_only_mode", settings.emotesOnly),
            subscribersOnly = enabled("subscribers_only_mode", settings.subscribersOnly),
            followersOnly = mode("followers_only_mode", settings.followersOnly),
            minimumAccountAge = mode("minimum_account_age", settings.minimumAccountAge),
            slowMode = mode("slow_mode", settings.slowMode),
            rules = patch["rules"]?.jsonPrimitive?.contentOrNull ?: settings.rules)
    }
}
