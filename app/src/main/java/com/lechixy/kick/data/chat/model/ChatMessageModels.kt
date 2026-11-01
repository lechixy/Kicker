package com.lechixy.kick.data.chat.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ChatMessageType {
    const val MESSAGE = "message"
    const val REPLY = "reply"
    const val CELEBRATION = "celebration"
    const val REWARD_REDEEMED = "reward_redeemed"
    const val GIFTED_SUBSCRIPTIONS = "gifted_subscriptions"
    const val CHANNEL_SUBSCRIPTION = "channel_subscription"
}

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("chatroom_id")
    val chatroomId: Long? = null,
    @SerialName("chat_id")
    val chatId: Long? = null,
    @SerialName("user_id")
    val userId: Long? = null,
    val content: String = "",
    val type: String = ChatMessageType.MESSAGE,
    @SerialName("created_at")
    val createdAt: String? = null,
    val sender: ChatSender? = null,
    @Serializable(with = ChatMessageMetadataSerializer::class)
    val metadata: ChatMessageMetadata? = null,
    @SerialName("thread_parent_id")
    val threadParentId: String? = null,
    val rewardRedeemed: RewardRedeemedEvent? = null,
    val giftedSubscriptions: GiftedSubscriptionsEvent? = null,
    val channelSubscription: ChannelSubscriptionEvent? = null,
    val messageDeleted: MessageDeletedEvent? = null,
    @kotlinx.serialization.Transient
    val isHistory: Boolean = false,
    @kotlinx.serialization.Transient
    val recordingOffsetMs: Long? = null
) {
    val isDeleted: Boolean
        get() = messageDeleted != null

    val isReply: Boolean
        get() = type == ChatMessageType.REPLY || metadata?.originalMessage != null

    val isCelebration: Boolean
        get() = type == ChatMessageType.CELEBRATION || metadata?.celebration != null

    val isRewardRedeemed: Boolean
        get() = type == ChatMessageType.REWARD_REDEEMED || rewardRedeemed != null

    val isGiftedSubscriptions: Boolean
        get() = type == ChatMessageType.GIFTED_SUBSCRIPTIONS || giftedSubscriptions != null

    val isChannelSubscription: Boolean
        get() = type == ChatMessageType.CHANNEL_SUBSCRIPTION || channelSubscription != null
}

@Serializable
data class ChatMessageMetadata(
    @SerialName("message_ref")
    val messageRef: String? = null,
    @SerialName("original_sender")
    val originalSender: ChatOriginalSender? = null,
    @SerialName("original_message")
    val originalMessage: ChatOriginalMessage? = null,
    val celebration: ChatCelebration? = null
)

object ChatMessageMetadataSerializer : KSerializer<ChatMessageMetadata?> {
    private val delegateSerializer = ChatMessageMetadata.serializer()
    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatMessageMetadataNullable")

    override fun deserialize(decoder: Decoder): ChatMessageMetadata? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        val element = try {
            jsonDecoder.decodeJsonElement()
        } catch (_: Exception) {
            return null
        }

        return try {
            when (element) {
                is JsonNull -> null
                is JsonObject -> lenientJson.decodeFromJsonElement(delegateSerializer, element)
                is JsonPrimitive -> {
                    if (!element.isString) return null
                    val raw = element.content.trim()
                    if (raw.isEmpty() || raw == "null" || raw == "[]") return null
                    lenientJson.decodeFromString(delegateSerializer, raw)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun serialize(encoder: Encoder, value: ChatMessageMetadata?) {
        val jsonEncoder = encoder as? JsonEncoder
        if (value == null) {
            jsonEncoder?.encodeJsonElement(JsonNull)
        } else {
            jsonEncoder?.encodeSerializableValue(delegateSerializer, value)
        }
    }
}

@Serializable
data class ChatOriginalSender(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null,
    val identity: ChatIdentity? = null
)

@Serializable
data class ChatOriginalMessage(
    val id: String = "",
    val content: String = "",
    val sender: ChatSender? = null,
    val messageDeleted: MessageDeletedEvent? = null
)

@Serializable
data class ChatCelebration(
    val id: String = "",
    val type: String = "",
    @SerialName("total_months")
    val totalMonths: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val username: String = "",
    val sender: ChatSender? = null
)

@Serializable
data class ChatSender(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null,
    val identity: ChatIdentity? = null
)

@Serializable
data class ChatIdentity(
    val color: String? = null,
    val badges: List<ChatSenderBadge> = emptyList(),
    @SerialName("badges_v2")
    val badgesV2: List<ChatBadge> = emptyList()
)

@Serializable
data class ChatSenderBadge(
    val type: String = "",
    val text: String? = null,
    val count: Int? = null,
    @SerialName("sort_order")
    val sortOrder: Int? = null,
    val active: Boolean? = null
)

@Serializable
data class ChatBadge(
    val name: String = "",
    @SerialName("badge_type")
    val badgeType: String? = null,
    @SerialName("image_url")
    val imageUrl: String? = null,
    val metadata: JsonElement? = null,
    val selected: Boolean = false,
    @SerialName("sort_order")
    val sortOrder: Int? = null
)

@Serializable
data class ChatHistoryResponse(
    val data: ChatHistoryData? = null,
    val message: String? = null
)

@Serializable
data class ChatHistoryData(
    val messages: List<ChatMessage> = emptyList(),
    val cursor: String? = null,
    @SerialName("pinned_message")
    val pinnedMessage: PinnedMessageInfo? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PinnedMessageInfo(
    val message: ChatMessage? = null,
    @JsonNames("pinned_by", "pinnedBy")
    val pinnedBy: ChatSender? = null,
    val duration: JsonElement? = null
)

@Serializable
data class RewardRedeemedEvent(
    @SerialName("reward_title")
    val rewardTitle: String = "",
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("channel_id")
    val channelId: Long? = null,
    val username: String = "",
    @SerialName("user_input")
    val userInput: String = "",
    @SerialName("reward_background_color")
    val rewardBackgroundColor: String? = null
)

@Serializable
data class GiftedSubscriptionsEvent(
    @SerialName("chatroom_id")
    val chatroomId: Long? = null,
    @SerialName("correlation_id")
    val correlationId: String? = null,
    @SerialName("gifted_usernames")
    val giftedUsernames: List<String> = emptyList(),
    @SerialName("gifter_username")
    val gifterUsername: String = "",
    @SerialName("gifted_total")
    val giftedTotal: Int? = null,
    @SerialName("gifter_total")
    val gifterTotal: Int? = null,
    @SerialName("chunk_details")
    val chunkDetails: JsonElement? = null
)

fun GiftedSubscriptionsEvent.toRecipientMessages(): List<ChatMessage> {
    val eventId = correlationId?.takeIf { it.isNotBlank() } ?: "${gifterUsername}_${System.nanoTime()}"
    val recipients = giftedUsernames.filter { it.isNotBlank() }.distinct()
    if (recipients.isEmpty()) return listOf(ChatMessage(
        id = "gift_$eventId", type = ChatMessageType.GIFTED_SUBSCRIPTIONS, giftedSubscriptions = this
    ))
    return recipients.map { recipient -> ChatMessage(
        id = "gift_${eventId}_$recipient", type = ChatMessageType.GIFTED_SUBSCRIPTIONS,
        giftedSubscriptions = copy(giftedUsernames = listOf(recipient), giftedTotal = 1)
    ) }
}

@Serializable
data class ChannelSubscriptionEvent(
    @SerialName("user_ids")
    val userIds: List<Long> = emptyList(),
    val username: String = "",
    @SerialName("channel_id")
    val channelId: Long? = null,
    val months: Int? = null,
    @SerialName("total_months")
    val totalMonths: Int? = null
)

@Serializable
data class BannedUserInfo(
    val id: Long? = null,
    val username: String = "",
    val slug: String? = null
)

@Serializable
data class UserBannedEvent(
    val id: String = "",
    val user: BannedUserInfo? = null,
    @SerialName("banned_by")
    val bannedBy: BannedUserInfo? = null,
    val permanent: Boolean = false,
    val duration: Int? = null,
    @SerialName("expires_at")
    val expiresAt: String? = null
)

@Serializable
data class DeletedMessageRef(
    val id: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MessageDeletedEvent(
    val id: String = "",
    val message: DeletedMessageRef? = null,
    @JsonNames("aiModerated", "ai_moderated")
    val aiModerated: Boolean = false,
    @JsonNames("violatedRules", "violated_rules")
    val violatedRules: List<JsonElement> = emptyList()
)

@Serializable
data class PointsUpdatedEvent(
    val reason: String = "",
    val points: Int = 0,
    val balance: Int = 0,
    @SerialName("user_id")
    val userId: Long? = null,
    @SerialName("channel_id")
    val channelId: Long? = null
)

@Serializable
data class StreamerIsLiveLivestream(
    val id: Long,
    @SerialName("channel_id")
    val channelId: Long? = null,
    @SerialName("session_title")
    val sessionTitle: String? = null,
    val source: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class StreamerIsLiveEvent(
    val livestream: StreamerIsLiveLivestream? = null
)

@Serializable
data class StopStreamChannelRef(
    val id: Long? = null,
    @SerialName("is_banned")
    val isBanned: Boolean = false
)

@Serializable
data class StopStreamLivestreamRef(
    val id: Long? = null,
    val channel: StopStreamChannelRef? = null
)

@Serializable
data class StopStreamBroadcastEvent(
    val livestream: StopStreamLivestreamRef? = null
)

@Serializable
data class StreamHostedMessage(val id: String, val numberOfViewers: Int)

@Serializable
data class StreamHostedUser(val id: Long, val username: String)

@Serializable
data class StreamHostedEvent(val message: StreamHostedMessage, val user: StreamHostedUser)

sealed interface ChatWebSocketEvent {
    data class Raid(val event: ChannelRaid) : ChatWebSocketEvent
    data class StreamHosted(val event: StreamHostedEvent) : ChatWebSocketEvent
    data class MessageReceived(val message: ChatMessage) : ChatWebSocketEvent
    data class Celebration(val event: ChatCelebration) : ChatWebSocketEvent
    data class RewardRedeemed(val event: RewardRedeemedEvent) : ChatWebSocketEvent
    data class GiftedSubscriptions(val event: GiftedSubscriptionsEvent) : ChatWebSocketEvent
    data class ChannelSubscribed(val event: ChannelSubscriptionEvent) : ChatWebSocketEvent
    data class UserBanned(val event: UserBannedEvent) : ChatWebSocketEvent
    data class MessageDeleted(val event: MessageDeletedEvent) : ChatWebSocketEvent
    data class SettingsChanged(val settings: JsonObject) : ChatWebSocketEvent
    data class PointsUpdated(val event: PointsUpdatedEvent) : ChatWebSocketEvent
    data class PinnedMessageCreated(val event: PinnedMessageInfo) : ChatWebSocketEvent
    data object PinnedMessageDeleted : ChatWebSocketEvent
    data class StreamerIsLive(val event: StreamerIsLiveEvent) : ChatWebSocketEvent
    data class StopStreamBroadcast(val event: StopStreamBroadcastEvent) : ChatWebSocketEvent
}

fun ChatIdentity.withSelectedBadgesOnly(): ChatIdentity = copy(
    badgesV2 = badgesV2.filter { badge ->
        val isGlobalLevelBadge = badge.name.equals("level", ignoreCase = true) ||
            badge.badgeType.equals("global", ignoreCase = true)
        if (isGlobalLevelBadge) badge.selected else badge.selected
    }
)

fun ChatSender.withSelectedBadgesOnly(): ChatSender = copy(
    identity = identity?.withSelectedBadgesOnly()
)

fun ChatMessage.withSelectedBadgesOnly(): ChatMessage = copy(
    sender = sender?.withSelectedBadgesOnly(),
    metadata = metadata?.copy(
        originalSender = metadata.originalSender?.copy(
            identity = metadata.originalSender.identity?.withSelectedBadgesOnly()
        ),
        originalMessage = metadata.originalMessage?.copy(
            sender = metadata.originalMessage.sender?.withSelectedBadgesOnly()
        )
    )
)

fun PinnedMessageInfo.withSelectedBadgesOnly(): PinnedMessageInfo = copy(
    message = message?.withSelectedBadgesOnly(),
    pinnedBy = pinnedBy?.withSelectedBadgesOnly()
)

fun ChatHistoryData.withSelectedBadgesOnly(): ChatHistoryData = copy(
    messages = messages.map { it.withSelectedBadgesOnly() },
    pinnedMessage = pinnedMessage?.withSelectedBadgesOnly()
)

