package com.lechixy.kick.data.chat.model

import java.time.Instant

data class TimedChatEvent(
    val id: Long,
    val event: ChatWebSocketEvent,
    val durationMillis: Long,
    val expiresAtMillis: Long
) {
    fun remainingMillis(nowMillis: Long): Long =
        (expiresAtMillis - nowMillis).coerceIn(0L, durationMillis)
}

internal fun ChatWebSocketEvent.tickerDurationMillis(): Long? = when (this) {
    is ChatWebSocketEvent.StreamHosted -> (event.message.numberOfViewers * 1_000L).coerceIn(20_000L, 60_000L)
    is ChatWebSocketEvent.GiftedSubscriptions -> 15_000L
    is ChatWebSocketEvent.ChannelSubscribed, is ChatWebSocketEvent.Celebration -> 12_000L
    is ChatWebSocketEvent.MessageReceived -> 12_000L.takeIf { message.isCelebration }
    else -> null
}

data class ChatPointsUpdate(
    val event: PointsUpdatedEvent,
    val receivedAt: Instant,
    val revision: Long,
    val pendingPoints: Long
) {
    companion object {
        internal fun receive(previous: ChatPointsUpdate?, event: PointsUpdatedEvent, now: Instant, revision: Long): ChatPointsUpdate {
            val pending = previous?.takeIf {
                it.event.channelId == event.channelId && it.event.userId == event.userId
            }?.pendingPoints ?: 0L
            return ChatPointsUpdate(event, now, revision, pending + event.points.coerceAtLeast(0))
        }
    }
}
