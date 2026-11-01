package com.lechixy.kick.components.channel.chat

import com.lechixy.kick.data.channel.model.*
import com.lechixy.kick.data.chat.model.*
import org.junit.Assert.*
import org.junit.Test

class ProfileSubscriberBadgeTest {
    private val assets = listOf(1, 3, 12, 24).map {
        SubscriberBadge(it.toLong(), months = it, badgeImage = SubscriberBadgeImage(src = "https://example.com/$it.webp"))
    }
    @Test fun channelAssetsAreSelectedByActualSubscriptionMonths() {
        for ((months, tier) in listOf(1 to 1, 2 to 1, 3 to 3, 16 to 12, 26 to 24)) {
            val identity = ChatIdentity(badges = listOf(ChatSenderBadge("subscriber", count = months)))
            val badge = resolveProfileBadges(identity, months, assets).single()
            assertEquals("https://example.com/$tier.webp", badge.imageUrl)
            assertEquals(months, badge.months)
        }
    }
    @Test fun subscriptionResponseCanSupplyMissingLegacyBadgeAndCount() {
        assertEquals("https://example.com/24.webp", resolveProfileBadges(ChatIdentity(), 26, assets).single().imageUrl)
        val identity = ChatIdentity(badges = listOf(ChatSenderBadge("subscriber")))
        assertEquals("https://example.com/12.webp", resolveProfileBadges(identity, 16, assets).single().imageUrl)
    }
    @Test fun historicalMonthsDoNotInventInactiveSubscriptionBadges() {
        assertTrue(resolveProfileBadges(ChatIdentity(), 26, assets, isSubscribed = false).isEmpty())
        assertTrue(resolveProfileBadges(ChatIdentity(badges = listOf(ChatSenderBadge("subscriber", count = 26, active = false))), 26, assets).isEmpty())
    }
}
