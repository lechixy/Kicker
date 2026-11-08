package com.lechixy.kick.components.channel.chat

import com.lechixy.kick.data.chat.model.ChatBadge
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatSenderBadge
import com.lechixy.kick.data.settings.model.FullscreenChatPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenChatPresentationTest {
    @Test fun snappingUsesDistanceInPixelsOnEachAxis() {
        val nearCorner = FullscreenChatPosition(0.04f, 0.96f)
        assertEquals(FullscreenChatPosition(0f, 1f), nearCorner.snapToEdges(200f, 240f, 10f))
        assertEquals(FullscreenChatPosition(0.04f, 1f), nearCorner.snapToEdges(300f, 240f, 10f))
    }

    @Test fun snappingCanBeReleasedBeyondTheThreshold() {
        val inside = FullscreenChatPosition(0.02f, 0.5f)
        val outside = FullscreenChatPosition(0.06f, 0.5f)
        assertEquals(0f, inside.snapToEdges(200f, 200f, 10f).x, 0f)
        assertEquals(outside, outside.snapToEdges(200f, 200f, 10f))
    }

    @Test fun essentialBadgesAreRetainedByLegacyTypeAndV2Identity() {
        val identity = ChatIdentity(
            badges = listOf(ChatSenderBadge("moderator"), ChatSenderBadge("subscriber"), ChatSenderBadge("verified")),
            badgesV2 = listOf(
                ChatBadge(name = "Broadcaster", selected = true),
                ChatBadge(name = "Partner", badgeType = "verified", selected = true),
                ChatBadge(name = "Founder", selected = true),
            ),
        )
        val visible = identity.withEssentialFullscreenBadges()
        assertEquals(listOf("moderator", "verified"), visible.badges.map { it.type })
        assertEquals(listOf("Broadcaster", "Partner"), visible.badgesV2.map { it.name })
    }
}
