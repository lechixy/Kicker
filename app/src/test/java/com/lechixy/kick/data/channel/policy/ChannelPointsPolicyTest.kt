package com.lechixy.kick.data.channel.policy

import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelUser

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.chat.model.PointsUpdatedEvent

class ChannelPointsPolicyTest {
    @Test fun unlimitedPointsBelongOnlyToTheAuthenticatedChannelOwner() {
        val channel = com.lechixy.kick.data.channel.model.ChannelDetail(id = 7, userId = 42, slug = "tester")
        assertTrue(ChannelPointsPolicy.isOwnChannel(channel, 42))
        assertFalse(ChannelPointsPolicy.isOwnChannel(channel, 7))
        assertFalse(ChannelPointsPolicy.isOwnChannel(channel, null))
        assertFalse(ChannelPointsPolicy.isOwnChannel(channel.copy(userId = null), 42))
        assertTrue(ChannelPointsPolicy.isOwnChannel(channel.copy(userId = null,
            user = com.lechixy.kick.data.channel.model.ChannelUser(42, "tester")), 42))
    }

    @Test fun privatePublicationDoesNotRequireRedundantUserId() {
        val event = Json.decodeFromString<PointsUpdatedEvent>(
            """{"reason":"WATCHED","points":10,"balance":120,"channel_id":7}""")
        assertNull(event.userId)
        assertTrue(ChannelPointsPolicy.acceptsPublication("private-channelpoints-42", 42))
        assertTrue(ChannelPointsPolicy.accepts(event, 7, 42))
        assertEquals(120, event.balance)
    }

    @Test fun rejectsOtherChannelsAccountsAndGuestPublications() {
        val event = PointsUpdatedEvent(channelId = 7, userId = 42)
        assertTrue(ChannelPointsPolicy.accepts(event, 7, 42))
        assertFalse(ChannelPointsPolicy.accepts(event, 8, 42))
        assertFalse(ChannelPointsPolicy.accepts(event, 7, 99))
        assertFalse(ChannelPointsPolicy.accepts(event.copy(userId = null), 7, null))
        assertFalse(ChannelPointsPolicy.acceptsPublication("chatrooms.7", 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication("private-channelpoints-99", 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication(null, 42))
        assertFalse(ChannelPointsPolicy.acceptsPublication("private-channelpoints-42", null))
    }
}
