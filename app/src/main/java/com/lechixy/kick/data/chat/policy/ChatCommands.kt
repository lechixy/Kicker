package com.lechixy.kick.data.chat.policy

import kotlinx.serialization.json.*

import com.lechixy.kick.data.channel.model.ChannelDetail

data class ChatSlashCommand(val name: String, val arguments: String = "", val moderator: Boolean = false,
    val ownerOnly: Boolean = false) {
    val usage get() = "/$name" + arguments.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
}
sealed interface ChatCommandAction {
    data object Help : ChatCommandAction
    data class User(val username: String) : ChatCommandAction
    data class Request(val url: String, val method: String, val body: JsonObject? = null) : ChatCommandAction
}
class ChatCommandException(val usage: String? = null) : IllegalArgumentException("Invalid chat command")
object ChatCommands {
    val commands = listOf(ChatSlashCommand("help"), ChatSlashCommand("user", "<username>"),
        ChatSlashCommand("ban", "<username> [reason]", true),
        ChatSlashCommand("unban", "<username>", true),
        ChatSlashCommand("timeout", "<username> <minutes> [reason]", true),
        ChatSlashCommand("clear", moderator = true),
        ChatSlashCommand("slow", "<on|off> [seconds]", true),
        ChatSlashCommand("followonly", "<on|off> [minutes]", true),
        ChatSlashCommand("emoteonly", "<on|off>", true),
        ChatSlashCommand("subonly", "<on|off>", true, true),
        ChatSlashCommand("title", "<title>", true),
        ChatSlashCommand("mod", "<username>", true, true), ChatSlashCommand("unmod", "<username>", true, true),
        ChatSlashCommand("vip", "<username>", true, true), ChatSlashCommand("unvip", "<username>", true, true),
        ChatSlashCommand("og", "<username>", true, true), ChatSlashCommand("unog", "<username>", true, true))
    fun available(privileged: Boolean, owner: Boolean) = commands.filter {
        (!it.moderator || privileged || owner) && (!it.ownerOnly || owner)
    }
    fun parse(channel: ChannelDetail, input: String, privileged: Boolean, owner: Boolean): ChatCommandAction {
        val parts = input.trim().removePrefix("/").split(Regex("\\s+"))
        val spec = available(privileged, owner).firstOrNull { it.name == parts[0].lowercase() }
            ?: throw ChatCommandException()
        val args = parts.drop(1)
        fun valid(condition: Boolean) { if (!condition) throw ChatCommandException(spec.usage) }
        fun username(): String {
            val name = args.firstOrNull()?.removePrefix("@").orEmpty()
            valid(name.matches(Regex("[A-Za-z0-9_-]{1,25}")))
            return name
        }
        valid(channel.slug.matches(Regex("[A-Za-z0-9_-]+")))
        val base = "https://kick.com/api/v2/channels/${channel.slug}"
        return when (spec.name) {
            "help" -> { valid(args.isEmpty()); ChatCommandAction.Help }
            "user" -> { valid(args.size == 1); ChatCommandAction.User(username()) }
            "ban", "timeout" -> {
                val name = username()
                val duration = args.getOrNull(1)?.toIntOrNull()
                if (spec.name == "timeout") valid(duration != null && duration in 1..10080)
                val body = buildJsonObject {
                    put("banned_username", name); put("permanent", spec.name == "ban")
                    if (spec.name == "timeout") put("duration", duration)
                    put("reason", args.drop(if (spec.name == "ban") 1 else 2).joinToString(" "))
                }
                ChatCommandAction.Request("$base/bans", "POST", body)
            }
            "unban" -> { valid(args.size == 1); ChatCommandAction.Request("$base/bans/${username()}", "DELETE") }
            "clear" -> { valid(args.isEmpty()); ChatCommandAction.Request("$base/chat-commands", "POST", buildJsonObject { put("command", "clear") }) }
            "title" -> { valid(args.isNotEmpty()); ChatCommandAction.Request("$base/stream-info", "PATCH", buildJsonObject { put("stream_title", args.joinToString(" ")) }) }
            "mod", "unmod", "vip", "unvip", "og", "unog" -> {
                valid(args.size == 1)
                val role = when (spec.name.removePrefix("un")) { "mod" -> "moderator"; else -> spec.name.removePrefix("un") }
                val url = "https://kick.com/api/internal/v1/channels/${channel.slug}/community/${role}s"
                if (spec.name.startsWith("un")) ChatCommandAction.Request("$url/${username()}", "DELETE")
                else ChatCommandAction.Request(url, "POST", buildJsonObject { put("username", username()) })
            }
            else -> {
                valid(args.firstOrNull() in listOf("on", "off"))
                val enabled = args[0] == "on"
                val duration = args.getOrNull(1)?.toIntOrNull()
                if (spec.name in listOf("slow", "followonly")) {
                    valid(args.size <= 2)
                    if (args.size > 1) valid(duration != null && duration in (if (spec.name == "slow") 1..3600 else 0..10080))
                } else valid(args.size == 1)
                val key = when (spec.name) {
                    "slow" -> "slow_mode"; "followonly" -> "followers_only_mode"
                    "subonly" -> "subscribers_only_mode"; else -> "emotes_only_mode"
                }
                ChatCommandAction.Request("https://web.kick.com/api/v1/channels/${channel.id}/chat/settings", "PATCH", buildJsonObject {
                    putJsonObject(key) {
                        put("enabled", enabled)
                        if (spec.name == "slow") put("duration_seconds", duration ?: 3)
                        if (spec.name == "followonly") put("duration_seconds", ((duration ?: 0) * 60).coerceAtLeast(1))
                    }
                })
            }
        }
    }
}
