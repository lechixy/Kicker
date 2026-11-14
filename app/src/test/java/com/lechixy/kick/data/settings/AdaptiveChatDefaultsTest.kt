package com.lechixy.kick.data.settings

import com.lechixy.kick.data.settings.model.SettingsKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveChatDefaultsTest {
    @Test fun readingSizeAndSpacingIncreaseOnTabletsUsingTheSamePreferenceKeys() {
        val phoneFont = SettingsKeys.chatFontSize(411)
        val tabletFont = SettingsKeys.chatFontSize(1024)
        assertEquals(phoneFont.key, tabletFont.key)
        assertEquals(15, phoneFont.defaultValue)
        assertEquals(16, tabletFont.defaultValue)
        assertEquals(3, SettingsKeys.chatMessageSpacing(411).defaultValue)
        assertEquals(4, SettingsKeys.chatMessageSpacing(1024).defaultValue)
    }

    @Test fun sideChatUsesLessOfAnExpandedLandscapeWindow() {
        val phone = SettingsKeys.chatWidth(840).defaultValue
        val tablet = SettingsKeys.chatWidth(1690).defaultValue
        assertTrue(tablet < phone)
        assertEquals(280f, phone * 840, 1f)
        assertEquals(360f, tablet * 1690, 1f)
        assertTrue(SettingsKeys.CHAT_WIDTH_FRACTION.validator!!.invoke(tablet))
    }
}
