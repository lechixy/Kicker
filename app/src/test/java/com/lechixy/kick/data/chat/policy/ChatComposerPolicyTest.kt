package com.lechixy.kick.data.chat.policy

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

import com.lechixy.kick.data.chat.model.ChannelMembership
import com.lechixy.kick.data.chat.policy.ChatComposerPolicy
import com.lechixy.kick.data.chat.policy.ChatInputBlock
import com.lechixy.kick.data.chat.model.ChatSettings
import com.lechixy.kick.data.chat.model.Mode

class ChatComposerPolicyTest {
    private val now = Instant.parse("2026-10-28T00:00:00Z")
    private val member = ChannelMembership(true, now.minusSeconds(86400), true, false, false)
    private val settings = ChatSettings(true, false, true, Mode(true, 86400), Mode(true, 86400), Mode(true, 33), "")
    private fun evaluate(policy: ChatSettings = settings, user: ChannelMembership = member, age: Instant? = now.minusSeconds(86400)) =
        ChatComposerPolicy.evaluate(policy, user, age, now)

    @Test fun subscriberWhoMeetsFollowAndAgeRequirementsOnlySeesApplicableSlowMode() {
        val result = evaluate()
        assertNull(result.block)
        assertTrue(result.slowMode)
        assertFalse(result.subscribersOnly)
        assertFalse(result.followersOnly)
        assertFalse(result.accountAge)
    }

    @Test fun nonSubscriberGetsDisabledInputEvenWhenOtherModesAreEnabled() {
        val result = evaluate(settings.copy(emotesOnly = true), member.copy(subscriber = false))
        assertEquals(ChatInputBlock.SUBSCRIBERS_ONLY, result.block)
        assertTrue(result.subscribersOnly)
        assertFalse(result.slowMode)
    }

    @Test fun privilegedUserDoesNotSeeBypassedModes() {
        val result = evaluate(user = member.copy(privileged = true, subscriber = false, following = false), age = null)
        assertNull(result.block)
        assertFalse(result.hasModes)
    }

    @Test fun unmetFollowAndAgeRequirementsStayVisibleUntilExactBoundary() {
        assertEquals(ChatInputBlock.FOLLOWERS_ONLY, evaluate(user = member.copy(following = false)).block)
        assertEquals(ChatInputBlock.FOLLOW_DURATION, evaluate(user = member.copy(followingSince = now.minusSeconds(86399))).block)
        assertEquals(ChatInputBlock.FOLLOW_DURATION, evaluate(user = member.copy(followingSince = null)).block)
        assertEquals(ChatInputBlock.ACCOUNT_AGE, evaluate(age = now.minusSeconds(86399)).block)
        assertEquals(ChatInputBlock.ACCOUNT_AGE, evaluate(age = null).block)
        assertNull(evaluate().block)
    }

    @Test fun banAndEmoteOnlyAlsoDisableTextInput() {
        val banned = evaluate(user = member.copy(banned = true, privileged = true))
        assertEquals(ChatInputBlock.BANNED, banned.block)
        assertFalse(banned.hasModes)
        assertEquals(ChatInputBlock.EMOTES_ONLY, evaluate(settings.copy(emotesOnly = true)).block)
    }

    @Test fun normalChannelsStayWritableWithoutASubscription() {
        val result = evaluate(settings.copy(subscribersOnly = false), member.copy(subscriber = false))
        assertNull(result.block)
        assertFalse(result.subscribersOnly)
    }
}
