package com.lechixy.kick.data.chat.policy

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

import com.lechixy.kick.data.chat.model.ChannelMembership
import com.lechixy.kick.data.chat.policy.ChatPolicy
import com.lechixy.kick.data.chat.model.ChatSettings
import com.lechixy.kick.data.chat.model.Mode

class ChatPolicyTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val member = ChannelMembership(true, now.minusSeconds(100), true, false, false)
    private val settings = ChatSettings(true, false, false, Mode(false), Mode(false), Mode(false), "")
    @Test fun unicodeCountsGraphemesAndBytesIndependently() {
        assertNull(ChatPolicy.messageError("e\u0301".repeat(500)))
        assertNull(ChatPolicy.messageError("👨‍👩‍👧‍👦".repeat(50)))
        assertNotNull(ChatPolicy.messageError("a".repeat(501)))
        assertNotNull(ChatPolicy.messageError("👨‍👩‍👧‍👦".repeat(100)))
        assertNotNull(ChatPolicy.messageError("  \n"))
    }
    @Test fun restrictionsComposeAndPrivilegedUsersStillRespectBanAndEmoteMode() {
        fun blocked(policy: ChatSettings, user: ChannelMembership = member) =
            ChatPolicy.restriction(policy, user, now.minusSeconds(200_000), now, null)
        assertNotNull(blocked(settings, member.copy(banned = true, privileged = true)))
        assertNotNull(blocked(settings.copy(emotesOnly = true), member.copy(privileged = true)))
        assertNotNull(blocked(settings.copy(subscribersOnly = true), member.copy(subscriber = false)))
        assertNotNull(blocked(settings.copy(followersOnly = Mode(true, 101))))
        assertNull(blocked(settings.copy(followersOnly = Mode(true, 100))))
        assertNull(blocked(settings.copy(subscribersOnly = true), member.copy(subscriber = false, privileged = true)))
    }
    @Test fun missingAgeAndFollowTimeFailClosed() {
        assertNotNull(ChatPolicy.restriction(settings.copy(minimumAccountAge = Mode(true, 86400)), member, null, now, null))
        assertNotNull(ChatPolicy.restriction(settings.copy(followersOnly = Mode(true, 1)), member.copy(followingSince = null), null, now, null))
    }
    @Test fun slowModeAndLinkRestrictionAreEnforced() {
        assertNotNull(ChatPolicy.restriction(settings.copy(slowMode = Mode(true, 5)), member, null, now, now.minusSeconds(4)))
        assertNull(ChatPolicy.restriction(settings.copy(slowMode = Mode(true, 5)), member, null, now, now.minusSeconds(5)))
        assertNotNull(ChatPolicy.restriction(settings.copy(allowLinks = false), member, null, now, null, "see https://example.com"))
        assertNotNull(ChatPolicy.restriction(settings.copy(allowLinks = false), member, null, now, null, "example.com"))
    }
    @Test fun slowModeDoesNotUnlockDuringTheLastFractionOfASecond() {
        val policy = settings.copy(slowMode = Mode(true, 5))
        val sent = now.minusSeconds(5).plusMillis(100)
        assertNotNull(ChatPolicy.restriction(policy, member, null, now, sent))
        assertNull(ChatPolicy.restriction(policy, member, null, now.plusMillis(100), sent))
    }
}
