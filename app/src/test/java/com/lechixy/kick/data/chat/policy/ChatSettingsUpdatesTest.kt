package com.lechixy.kick.data.chat.policy

import com.lechixy.kick.data.chat.model.CentrifugoPushFrame

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.chat.model.ChatSettings
import com.lechixy.kick.data.chat.policy.ChatSettingsUpdates
import com.lechixy.kick.data.chat.model.Mode

class ChatSettingsUpdatesTest {
    private val initial = ChatSettings(true, false, false, Mode(false), Mode(false), Mode(false), "Be kind")
    private fun objectOf(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test fun legacyChatroomSettingsConvertMinutesAndKeepUnspecifiedFields() {
        val patch = ChatSettingsUpdates.normalize(objectOf("""{
            "id":27208342,"subscribers_mode":{"enabled":true},"emotes_mode":{"enabled":false},
            "slow_mode":{"enabled":true,"message_interval":33},
            "followers_mode":{"enabled":true,"min_duration":1440},
            "account_age":{"enabled":true,"min_duration":1440}}
        """))
        val settings = ChatSettingsUpdates.apply(initial, patch)
        assertTrue(settings.subscribersOnly)
        assertFalse(settings.emotesOnly)
        assertEquals(Mode(true, 33), settings.slowMode)
        assertEquals(Mode(true, 86400), settings.followersOnly)
        assertEquals(Mode(true, 86400), settings.minimumAccountAge)
        assertEquals("Be kind", settings.rules)
        assertTrue(settings.allowLinks)
    }

    @Test fun blankEventNameFallsBackToTagAndModernSettingsUseSeconds() {
        val frame = Json.decodeFromString<CentrifugoPushFrame>("""{
            "channel":"channel_27496659","pub":{"data":{"event":" ","data":{
                "channel":{"id":27496659},"settings":{"rules":"","allow_links":{"enabled":false},
                "emotes_only_mode":{"enabled":true},"followers_only_mode":{"enabled":true,"duration_seconds":86400},
                "slow_mode":{"enabled":true,"duration_seconds":33}}}},"tags":{"event":"ChatSettingsChanged"}}}
        """)
        assertEquals("ChatSettingsChanged", frame.pub?.eventName)
        val patch = ChatSettingsUpdates.normalize(frame.pub!!.data!!.data!!.jsonObject)
        val settings = ChatSettingsUpdates.apply(initial, patch)
        assertEquals("", settings.rules)
        assertFalse(settings.allowLinks)
        assertTrue(settings.emotesOnly)
        assertEquals(Mode(true, 86400), settings.followersOnly)
        assertEquals(Mode(true, 33), settings.slowMode)
    }

    @Test fun partialDisablePreservesDurationAndOtherModes() {
        val original = objectOf("""{"slow_mode":{"enabled":true,"duration_seconds":33},"rules":"Rules"}""")
        val patch = objectOf("""{"slow_mode":{"enabled":false},"emotes_only_mode":{"enabled":true}}""")
        val settings = ChatSettingsUpdates.apply(initial, ChatSettingsUpdates.merge(original, patch))
        assertEquals(Mode(false, 33), settings.slowMode)
        assertTrue(settings.emotesOnly)
        assertEquals("Rules", settings.rules)
    }
}
