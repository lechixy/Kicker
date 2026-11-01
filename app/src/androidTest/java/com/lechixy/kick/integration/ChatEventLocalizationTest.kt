package com.lechixy.kick.integration

import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

import com.lechixy.kick.components.channel.chat.giftedEventSegments
import com.lechixy.kick.components.channel.chat.raidEventSegments
import com.lechixy.kick.components.channel.chat.subscriptionEventSegments
import com.lechixy.kick.data.chat.model.GiftedSubscriptionsEvent
import com.lechixy.kick.util.FormatUtils

@RunWith(AndroidJUnit4::class)
class ChatEventLocalizationTest {
    private fun context(language: String) = InstrumentationRegistry.getInstrumentation().targetContext.let { base ->
        base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })
    }

    @Test fun smallGiftsNameRecipientsAndLargeGiftsNameTheCommunity() {
        val event = GiftedSubscriptionsEvent(gifterUsername = "Alice", giftedUsernames = listOf("Bob"), giftedTotal = 1)
        fun text(language: String, gift: GiftedSubscriptionsEvent) =
            giftedEventSegments(context(language), gift, emptyMap(), Color.Green).joinToString("") { it.text }
        assertEquals("Alice gifted 1 sub to Bob.", text("en", event))
        assertEquals("Alice, Bob için 1 abonelik hediye etti.", text("tr", event))
        val small = event.copy(giftedUsernames = listOf("Bob", "Charlie"), giftedTotal = 2)
        assertEquals("Alice gifted 2 subs to Bob and Charlie.", text("en", small))
        assertEquals("Alice, Bob ve Charlie için 2 abonelik hediye etti.", text("tr", small))
        assertEquals("Alice gifted 5 subs to the community.", text("en", small.copy(giftedTotal = 5)))
        assertEquals("Alice topluluğa 5 abonelik hediye etti.", text("tr", small.copy(giftedTotal = 5)))
    }

    @Test fun subscriptionAndRaidSentencesUseLocalePluralRules() {
        fun subscription(language: String, months: Int) = subscriptionEventSegments(context(language), "Alice", months,
            userColor = Color.Green, primaryColor = Color.Green).joinToString("") { it.text }
        assertEquals("Alice has subscribed for 1 month.", subscription("en", 1))
        assertEquals("Alice has subscribed for 2 months.", subscription("en", 2))
        assertEquals("Alice, 2 aydır abone.", subscription("tr", 2))
        assertEquals("Alice raided with 1 viewer!", raidEventSegments(context("en"), "Alice", 1, Color.Green).joinToString("") { it.text })
    }

    @Test fun compactCountsLocalizeSuffixDecimalAndDigits() {
        assertEquals("929.1K", FormatUtils.formatFollowersCount("929056", Locale.ENGLISH))
        assertEquals("1K", FormatUtils.formatFollowersCount("1000", Locale.ENGLISH))
        assertEquals("1\u00a0B", FormatUtils.formatFollowersCount("1000", Locale.forLanguageTag("tr")))
        assertEquals("1万", FormatUtils.formatFollowersCount("10000", Locale.JAPANESE))
        assertEquals("929.056", FormatUtils.formatViewersCount(929056, Locale.forLanguageTag("tr")))
        assertTrue(FormatUtils.formatViewersCount(929056, Locale.forLanguageTag("ar-EG-u-nu-arab")).contains('٩'))
    }
}
