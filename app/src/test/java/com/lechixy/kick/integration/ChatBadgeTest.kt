package com.lechixy.kick.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.ChatTextParser
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.channel.model.SubscriberBadgeImage
import com.lechixy.kick.data.chat.model.ChatBadge
import com.lechixy.kick.data.chat.model.ChatIdentity
import com.lechixy.kick.data.chat.model.ChatSenderBadge

class ChatBadgeTest {

    @Test
    fun resolveBadges_resolvesVipBadgeCorrectly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "vip",
                    text = "VIP",
                    active = true,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        val vipBadge = resolved.first()
        assertEquals("badge_local_vip", vipBadge.key)
        assertEquals("VIP", vipBadge.name)
        assertEquals(R.drawable.vip_badge, vipBadge.drawableRes)
        assertEquals(5, vipBadge.sortOrder)
    }

    @Test
    fun resolveBadges_skipsInactiveVipBadge() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "vip",
                    text = "VIP",
                    active = false,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun resolveBadges_sortsBadgesProperly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(type = "vip", text = "VIP", sortOrder = 5),
                ChatSenderBadge(type = "broadcaster", text = "Broadcaster", sortOrder = 1),
                ChatSenderBadge(type = "moderator", text = "Moderator", sortOrder = 3)
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(3, resolved.size)
        assertEquals("Broadcaster", resolved[0].name)
        assertEquals("Moderator", resolved[1].name)
        assertEquals("VIP", resolved[2].name)
    }

    @Test
    fun resolveBadges_prefersV2BadgeOverLocalVip() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(type = "vip", text = "VIP", sortOrder = 5)
            ),
            badgesV2 = listOf(
                ChatBadge(
                    name = "vip",
                    imageUrl = "https://example.com/vip.png",
                    selected = true,
                    sortOrder = 5
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        assertEquals("https://example.com/vip.png", resolved[0].imageUrl)
    }

    @Test
    fun resolveBadges_resolvesFounderBadgeCorrectly() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(
                    type = "founder",
                    text = "Founder"
                )
            )
        )

        val resolved = ChatTextParser.resolveBadges(identity, emptyList())
        assertEquals(1, resolved.size)
        val founderBadge = resolved.first()
        assertEquals("badge_local_founder", founderBadge.key)
        assertEquals("Founder", founderBadge.name)
        assertEquals(R.drawable.founder_badge, founderBadge.drawableRes)
        assertEquals(6, founderBadge.sortOrder)
    }

    @Test
    fun resolveBadges_subscriberMonths_localizedEnglish() {
        val identity16 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber", count = 16))
        )
        val resolved16 = ChatTextParser.resolveBadges(identity16, emptyList(), locale = Locale.ENGLISH)
        assertEquals(1, resolved16.size)
        assertEquals("16-Months Subscriber", resolved16.first().name)
        assertEquals(16, resolved16.first().months)

        val identity1 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber", count = 1))
        )
        val resolved1 = ChatTextParser.resolveBadges(identity1, emptyList(), locale = Locale.ENGLISH)
        assertEquals("1-Month Subscriber", resolved1.first().name)

        val identityNoCount = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber"))
        )
        val resolvedNoCount = ChatTextParser.resolveBadges(identityNoCount, emptyList(), locale = Locale.ENGLISH)
        assertEquals("Subscriber", resolvedNoCount.first().name)
    }

    @Test
    fun resolveBadges_subscriberMonths_withoutContextUsesDefaultNames() {
        val identity16 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber", count = 16))
        )
        val resolved16 = ChatTextParser.resolveBadges(identity16, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals(1, resolved16.size)
        assertEquals("16-Months Subscriber", resolved16.first().name)
        assertEquals(16, resolved16.first().months)

        val identity1 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber", count = 1))
        )
        val resolved1 = ChatTextParser.resolveBadges(identity1, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals("1-Month Subscriber", resolved1.first().name)

        val identityNoCount = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber"))
        )
        val resolvedNoCount = ChatTextParser.resolveBadges(identityNoCount, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals("Subscriber", resolvedNoCount.first().name)
    }

    @Test
    fun resolveBadges_gifterCount_localizedEnglish() {
        val identity5 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "sub_gifter", count = 5))
        )
        val resolved5 = ChatTextParser.resolveBadges(identity5, emptyList(), locale = Locale.ENGLISH)
        assertEquals(1, resolved5.size)
        assertEquals("Gifted 5 sub", resolved5.first().name)
        assertEquals(5, resolved5.first().count)

        val identity1 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "gifter", count = 1))
        )
        val resolved1 = ChatTextParser.resolveBadges(identity1, emptyList(), locale = Locale.ENGLISH)
        assertEquals("Gifted 1 sub", resolved1.first().name)

        val identityNoCount = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subgifter"))
        )
        val resolvedNoCount = ChatTextParser.resolveBadges(identityNoCount, emptyList(), locale = Locale.ENGLISH)
        assertEquals("Sub Gifter", resolvedNoCount.first().name)
    }

    @Test
    fun resolveBadges_gifterCount_withoutContextUsesDefaultNames() {
        val identity5 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "sub_gifter", count = 5))
        )
        val resolved5 = ChatTextParser.resolveBadges(identity5, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals(1, resolved5.size)
        assertEquals("Gifted 5 sub", resolved5.first().name)
        assertEquals(5, resolved5.first().count)

        val identity1 = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "gifter", count = 1))
        )
        val resolved1 = ChatTextParser.resolveBadges(identity1, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals("Gifted 1 sub", resolved1.first().name)

        val identityNoCount = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subgifter"))
        )
        val resolvedNoCount = ChatTextParser.resolveBadges(identityNoCount, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals("Sub Gifter", resolvedNoCount.first().name)
    }

    @Test
    fun resolveBadges_withoutContextDoesNotTranslateBadgesFromLocaleAlone() {
        val identity = ChatIdentity(
            badges = listOf(
                ChatSenderBadge(type = "broadcaster"),
                ChatSenderBadge(type = "moderator"),
                ChatSenderBadge(type = "verified"),
                ChatSenderBadge(type = "founder"),
                ChatSenderBadge(type = "vip"),
                ChatSenderBadge(type = "og")
            )
        )
        val resolved = ChatTextParser.resolveBadges(identity, emptyList(), locale = Locale.forLanguageTag("tr"))
        assertEquals(6, resolved.size)
        assertEquals("Broadcaster", resolved[0].name)
        assertEquals("Verified", resolved[1].name)
        assertEquals("Moderator", resolved[2].name)
        assertEquals("OG", resolved[3].name)
        assertEquals("VIP", resolved[4].name)
        assertEquals("Founder", resolved[5].name)
    }

    @Test
    fun resolveBadges_subscriberMatchesChannelBadgesWithMonths() {
        val channelBadges = listOf(
            SubscriberBadge(id = 10, months = 1, badgeImage = SubscriberBadgeImage(src = "https://example.com/sub1.png")),
            SubscriberBadge(id = 20, months = 3, badgeImage = SubscriberBadgeImage(src = "https://example.com/sub3.png")),
            SubscriberBadge(id = 30, months = 12, badgeImage = SubscriberBadgeImage(src = "https://example.com/sub12.png"))
        )

        val identity = ChatIdentity(
            badges = listOf(ChatSenderBadge(type = "subscriber", count = 16))
        )
        val resolved = ChatTextParser.resolveBadges(identity, channelBadges, locale = Locale.ENGLISH)
        assertEquals(1, resolved.size)
        assertEquals("16-Months Subscriber", resolved.first().name)
        assertEquals("https://example.com/sub12.png", resolved.first().imageUrl)
        assertEquals("badge_sub_30_16", resolved.first().key)
    }
}
