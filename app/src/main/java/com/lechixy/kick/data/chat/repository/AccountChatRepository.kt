package com.lechixy.kick.data.chat.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant

import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelPointsSettings
import com.lechixy.kick.data.channel.model.ChannelReward
import com.lechixy.kick.data.channel.model.parseChannelRewards
import com.lechixy.kick.data.channel.policy.ChannelPointsPolicy
import com.lechixy.kick.data.chat.model.ChatAccess
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.chatSendPayload
import com.lechixy.kick.data.chat.model.ChannelMembership
import com.lechixy.kick.data.chat.policy.ChatCommandAction
import com.lechixy.kick.data.chat.policy.ChatCommands
import com.lechixy.kick.data.chat.policy.ChatIssue
import com.lechixy.kick.data.chat.policy.ChatPolicy
import com.lechixy.kick.data.chat.policy.ChatPolicyException
import com.lechixy.kick.data.chat.policy.ChatRestriction
import com.lechixy.kick.data.chat.model.ChatSettings
import com.lechixy.kick.data.chat.model.Mode

class AccountChatRepository(private val auth: AuthRepository) {
    private val sendMutex = Mutex()
    private val lastSent = mutableMapOf<Pair<Long, Long>, Instant>()
    private val blockedUntil = mutableMapOf<Pair<Long, Long>, Instant>()
    private val blockedDuration = mutableMapOf<Pair<Long, Long>, Long>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun identity(channelId: Long): ChatIdentity {
        val userId = auth.session.value.profile?.userId ?: error("Sign in required")
        val response = auth.request("https://kick.com/api/v2/channels/$channelId/users/$userId/identity")
        val data = (response["data"] as? JsonObject) ?: response
        return json.decodeFromJsonElement(data["identity"] ?: error("Missing identity"))
    }

    suspend fun updateIdentity(channelId: Long, expectedGeneration: Long, identity: ChatIdentity) {
        check(auth.session.value.generation == expectedGeneration && auth.session.value.signedIn)
        val userId = auth.session.value.profile?.userId ?: error("Sign in required")
        require(identity.badges.count { it.active != false } + identity.badgesV2.count { it.selected } <= 4)
        auth.request("https://kick.com/api/v2/channels/$channelId/users/$userId/identity", "PUT", buildJsonObject {
            putJsonArray("badges") { identity.badges.filter { it.active != false }.forEach { add(it.type) } }
            // The website sends V2 badge descriptors, unlike the legacy string badge list.
            putJsonArray("badges_v2") {
                identity.badgesV2.filter { it.selected }.forEach { badge ->
                    add(buildJsonObject { put("name", badge.name) })
                }
            }
            put("color", identity.color)
        })
    }

    suspend fun access(channel: ChannelDetail): ChatAccess {
        require(channel.slug.matches(Regex("[A-Za-z0-9_-]+")))
        val settings = auth.request("https://web.kick.com/api/v1/channels/${channel.id}/chat/settings")["data"]?.jsonObject
            ?: error("Missing settings")
        val me = auth.request("https://kick.com/api/v2/channels/${channel.slug}/me")
        fun bool(root: JsonObject, key: String): Boolean =
            root[key]?.jsonPrimitive?.booleanOrNull ?: error("Missing $key")
        fun mode(key: String): Mode {
            val value = settings[key]?.jsonObject ?: error("Missing $key")
            val enabled = bool(value, "enabled")
            val duration = if (enabled) value.string("duration_seconds")?.toLongOrNull()?.takeIf { it in 0..31_536_000 }
                ?: error("Invalid duration") else 0
            return Mode(enabled, duration)
        }
        check(me.containsKey("subscription") && me.containsKey("banned"))
        val subscription = me["subscription"]
        check(subscription == JsonNull || subscription is JsonObject) { "Unknown subscription status" }
        val membership = ChannelMembership(
            bool(me, "is_following"), me.string("following_since")?.let { runCatching { Instant.parse(it) }.getOrNull() },
            subscription is JsonObject,
            me["banned"] != JsonNull && me["banned"] != JsonPrimitive(false),
            bool(me, "is_broadcaster") || bool(me, "is_moderator") || (me["is_super_admin"]?.jsonPrimitive?.booleanOrNull == true))
        val policy = ChatSettings(
            bool(settings["allow_links"]!!.jsonObject, "enabled"),
            bool(settings["emotes_only_mode"]!!.jsonObject, "enabled"),
            bool(settings["subscribers_only_mode"]!!.jsonObject, "enabled"),
            mode("followers_only_mode"), mode("minimum_account_age"), mode("slow_mode"), settings.string("rules").orEmpty())
        val userId = auth.session.value.profile?.userId
        val identity = if (userId == null) null else try {
            val response = auth.request("https://kick.com/api/v2/channels/${channel.id}/users/$userId/identity")
            response["data"]?.jsonObject?.get("identity")?.let { json.decodeFromJsonElement<ChatIdentity>(it) }
        } catch (error: Exception) { if (error is CancellationException) throw error; null }
        return ChatAccess(policy, membership, identity)
    }

    suspend fun emotes(slug: String): List<com.lechixy.kick.data.chat.model.EmoteCategory> {
        require(slug.matches(Regex("[A-Za-z0-9_-]+")))
        return com.lechixy.kick.data.chat.model.parseEmoteCategories(auth.requestElement("https://kick.com/emotes/$slug"))
    }

    suspend fun channelPointSettings(channel: ChannelDetail): ChannelPointsSettings {
        require(channel.slug.matches(Regex("[A-Za-z0-9_-]+")))
        return json.decodeFromJsonElement(auth.request(
            "https://kick.com/api/v2/channels/${channel.slug}/channel-points/channel-settings")["data"]!!)
    }

    suspend fun points(channel: ChannelDetail): Long = auth.request(
        "https://kick.com/api/v2/channels/${channel.slug}/points")["data"]!!.jsonObject["points"]!!.jsonPrimitive.long

    suspend fun kicksBalance(): Long = auth.request("https://web.kick.com/api/v1/kicks/balance")["data"]!!
        .jsonObject["balance"]!!.jsonObject["available"]!!.jsonPrimitive.long

    suspend fun rewards(channel: ChannelDetail): List<ChannelReward> {
        require(channel.slug.matches(Regex("[A-Za-z0-9_-]+")))
        return parseChannelRewards(auth.requestElement(
            "https://kick.com/api/v2/channels/${channel.slug}/rewards?is_enabled=true"))
    }

    data class Cooldown(val remainingMillis: Long, val totalMillis: Long)

    fun cooldown(channelId: Long, access: ChatAccess): Cooldown {
        val key = auth.session.value.generation to channelId
        val now = Instant.now()
        val slowDuration = if (access.settings.slowMode.enabled && !access.member.privileged)
            access.settings.slowMode.durationSeconds * 1000 else 0L
        val slowEnd = lastSent[key]?.plusMillis(slowDuration)
        val blockedEnd = blockedUntil[key]
        val end = listOfNotNull(slowEnd, blockedEnd).maxOrNull() ?: now
        val remaining = java.time.Duration.between(now, end).toMillis().coerceAtLeast(0)
        val total = if (end == blockedEnd) blockedDuration[key] ?: remaining else slowDuration
        return Cooldown(remaining, maxOf(total, remaining, 1))
    }

    fun clearSession() { lastSent.clear(); blockedUntil.clear(); blockedDuration.clear() }

    fun restriction(channelId: Long, access: ChatAccess, content: String? = null): String? {
        return restrictionIssue(channelId, access, content)?.legacyMessage
    }

    fun restrictionIssue(channelId: Long, access: ChatAccess, content: String? = null): ChatRestriction? {
        val key = auth.session.value.generation to channelId
        val now = Instant.now()
        blockedUntil[key]?.let {
            if (now.isBefore(it)) return ChatRestriction(ChatIssue.RATE_LIMIT, it.epochSecond - now.epochSecond + 1)
        }
        return ChatPolicy.restrictionIssue(access.settings, access.member,
            auth.accountCreatedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            now, lastSent[key], content)
    }

    suspend fun send(channel: ChannelDetail, content: String, reply: com.lechixy.kick.data.chat.model.ChatMessage? = null): String? {
        val generation = auth.session.value.generation
        return sendMutex.withLock {
            check(auth.session.value.signedIn && generation == auth.session.value.generation) { "Session changed" }
            ChatPolicy.messageIssue(content)?.let { throw ChatPolicyException(ChatRestriction(it)) }
            val access = access(channel) // Fresh settings and membership before every send.
            if (reply == null && content.trimStart().startsWith("/")) {
                val action = ChatCommands.parse(channel, content, access.member.privileged,
                    ChannelPointsPolicy.isOwnChannel(channel, auth.session.value.profile?.userId))
                require(action is ChatCommandAction.Request) { "This command is handled by the composer" }
                check(auth.session.value.signedIn && generation == auth.session.value.generation) { "Session changed" }
                val response = auth.request(action.url, action.method, action.body)
                check((response["status"] as? JsonObject)?.get("error")?.jsonPrimitive?.booleanOrNull != true) { "Command was rejected" }
                return@withLock null
            }
            restrictionIssue(channel.id, access, content)?.let { throw ChatPolicyException(it) }
            val chatroomId = channel.chatroom?.id?.takeIf { it > 0 }
                ?: throw IllegalArgumentException("Sohbet odası doğrulanamadı. Kanalı yeniden açın.")
            check(auth.session.value.signedIn && generation == auth.session.value.generation)
            try {
                if (reply?.chatroomId != null) require(reply.chatroomId == chatroomId) { "Reply belongs to a different chatroom" }
                val response = auth.request("https://kick.com/api/v2/messages/send/$chatroomId", "POST", chatSendPayload(content, reply))
                val status = response["status"] as? JsonObject
                check(status?.get("error")?.jsonPrimitive?.booleanOrNull != true) { "Message not sent" }
                val data = response["data"] as? JsonObject
                check(data?.get("is_sent")?.jsonPrimitive?.booleanOrNull != false) { "Message not sent" }
                lastSent[generation to channel.id] = Instant.now()
                // Some web responses have no message ID. The WebSocket remains the sole
                // source of displayed messages, so HTTP success never inserts a duplicate.
                data?.string("id") ?: data?.string("message_id") ?: response.string("id")
            } catch (error: Exception) {
                if (error is java.io.IOException) {
                    // Outcome is unknown: apply cooldown but never automatically replay a POST.
                    if (auth.session.value.generation == generation) lastSent[generation to channel.id] = Instant.now()
                }
                if (error is AccountApiException && error.status == 429) {
                    val wait = error.retryAfterSeconds ?: 60
                    if (auth.session.value.generation == generation) {
                        blockedUntil[generation to channel.id] = Instant.now().plusSeconds(wait.coerceIn(1, 86400))
                        blockedDuration[generation to channel.id] = wait.coerceIn(1, 86400) * 1000
                    }
                }
                throw error
            }
        }
    }
}
