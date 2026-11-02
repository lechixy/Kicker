package com.lechixy.kick.integration

import android.content.res.Configuration
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.Locale

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.chatUserMessage
import com.lechixy.kick.components.channel.chat.localized
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.chat.policy.ChatIssue
import com.lechixy.kick.data.chat.policy.ChatPolicyException
import com.lechixy.kick.data.chat.policy.ChatRestriction

@RunWith(AndroidJUnit4::class)
class ChatLocalizationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun localizedContext(language: String) = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    @Test fun chatAndComposerMessagesFollowTheSelectedLocale() {
        val en = localizedContext("en")
        val tr = localizedContext("tr")
        assertEquals("Active chatters", en.getString(R.string.active_chatters_title))
        assertEquals("Aktif sohbetçiler", tr.getString(R.string.active_chatters_title))
        assertEquals("Search username", en.getString(R.string.active_chatters_search))
        assertEquals("Kullanıcı adı ara", tr.getString(R.string.active_chatters_search))
        assertEquals("Message must be 500 characters or fewer.", ChatRestriction(ChatIssue.TOO_LONG).localized(en))
        assertEquals("Mesaj en fazla 500 karakter olabilir.", ChatRestriction(ChatIssue.TOO_LONG).localized(tr))
        assertTrue(ChatRestriction(ChatIssue.RATE_LIMIT, 12).localized(en).contains("12 seconds"))
        assertTrue(ChatRestriction(ChatIssue.RATE_LIMIT, 12).localized(tr).contains("12 saniye"))
        assertEquals("1 month", en.getString(R.string.chatter_subscription_month, 1))
        assertEquals("26 months", en.getString(R.string.chatter_subscription_months, 26))
        assertEquals("26 ay", tr.getString(R.string.chatter_subscription_months, 26))
        assertEquals(en.getString(R.string.chat_message_empty), ChatPolicyException(ChatRestriction(ChatIssue.EMPTY)).chatUserMessage(en))
        assertEquals(tr.getString(R.string.chat_network_error), java.io.IOException().chatUserMessage(tr))
    }

    @Test fun allSixTierAssetsDecodeAsTransparentLoopingAnimations() {
        for (tier in 40..90 step 10) {
            val bytes = context.assets.open("tier/animation/level-$tier.webp").use { it.readBytes() }
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            assertTrue("Tier $tier must animate", drawable is AnimatedImageDrawable)
            assertEquals(516, drawable.intrinsicWidth)
            assertEquals(230, drawable.intrinsicHeight)
            assertEquals(AnimatedImageDrawable.REPEAT_INFINITE, (drawable as AnimatedImageDrawable).repeatCount)
        }
        assertFalse(context.assets.list("tier/animation").orEmpty().any { it.contains("100") })
    }
}
