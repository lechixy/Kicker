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

    @Test fun resizedPositionRoundTripsWithoutChangingLegacyPositions() {
        val legacy = FullscreenChatPosition.decode("0.25,1.0")!!
        assertEquals(FullscreenChatPosition(0.25f, 1f), legacy)
        val resized = legacy.resize(320f, 160f, 96f, 40f, fromStart = false, horizontalAxis = true)
        assertEquals(0.625f, resized.widthFraction!!, 0.001f)
        assertEquals(40f, (320f - 200f) * resized.x, 0.001f)
        assertEquals(resized, FullscreenChatPosition.decode(resized.encode()))
    }

    @Test fun cornerResizeAnchorsOppositeEdgesAndClampsToBounds() {
        val resized = FullscreenChatPosition(0.5f, 0.5f)
            .resize(400f, 200f, 96f, -200f, fromStart = true, horizontalAxis = true)
            .resize(300f, 150f, 48f, 200f, fromStart = false, horizontalAxis = false)
        assertEquals(0f, resized.x, 0.001f)
        assertEquals(0.75f, resized.widthFraction!!, 0.001f)
        assertEquals(1f, resized.y, 0.001f)
        assertEquals(0.75f, resized.heightFraction!!, 0.001f)
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
