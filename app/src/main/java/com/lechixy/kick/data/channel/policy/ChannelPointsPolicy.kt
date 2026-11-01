package com.lechixy.kick.data.channel.policy

import com.lechixy.kick.data.chat.model.PointsUpdatedEvent

/** The authenticated private channel identifies the recipient even when user_id is omitted. */
object ChannelPointsPolicy {
    fun isOwnChannel(channel: com.lechixy.kick.data.channel.model.ChannelDetail, userId: Long?): Boolean =
        userId != null && userId == (channel.userId ?: channel.user?.id)

    fun accepts(event: PointsUpdatedEvent, channelId: Long, userId: Long?): Boolean =
        userId != null && event.channelId == channelId && (event.userId == null || event.userId == userId)

    fun acceptsPublication(source: String?, userId: Long?): Boolean =
        userId != null && source == "private-channelpoints-$userId"
}
