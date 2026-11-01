package com.lechixy.kick.data.chat.model

import com.lechixy.kick.data.settings.model.SettingsKeys
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ChatTimestampModeTest {
    private val message = ChatMessage(createdAt = "2026-11-01T12:34:56Z", recordingOffsetMs = 3_661_000)

    @Test fun defaultAndUnknownPreferencesShowOnlyVideo() {
        assertEquals(ChatTimestampMode.ONLY_VIDEO, ChatTimestampMode.fromValue(SettingsKeys.CHAT_TIMESTAMPS.defaultValue))
        assertEquals(ChatTimestampMode.ONLY_VIDEO, ChatTimestampMode.fromValue("unknown"))
        assertNull(chatTimestamp(message, ChatTimestampMode.ONLY_VIDEO, false))
        assertEquals("1:01:01", chatTimestamp(message, ChatTimestampMode.ONLY_VIDEO, true))
    }

    @Test fun allFourChoicesApplyToTheViewingMode() {
        val expected = mapOf(ChatTimestampMode.LIVE_AND_VIDEO to (true to true),
            ChatTimestampMode.ONLY_LIVE to (true to false),
            ChatTimestampMode.ONLY_VIDEO to (false to true),
            ChatTimestampMode.DONT_SHOW to (false to false))
        expected.forEach { (mode, visibility) ->
            assertEquals(visibility.first, mode.shows(isReplay = false))
            assertEquals(visibility.second, mode.shows(isReplay = true))
        }
    }

    @Test fun liveUsesMessageTimeInLocalZoneAndReplayUsesVideoOffset() {
        assertEquals("15:34", chatTimestamp(message, ChatTimestampMode.LIVE_AND_VIDEO, false, ZoneId.of("Europe/Istanbul")))
        assertEquals("12:34", chatTimestamp(message, ChatTimestampMode.ONLY_LIVE, false, ZoneId.of("UTC")))
        assertEquals("1:01:01", chatTimestamp(message, ChatTimestampMode.LIVE_AND_VIDEO, true))
        assertEquals("00:00", chatTimestamp(message.copy(recordingOffsetMs = 0), ChatTimestampMode.ONLY_VIDEO, true))
    }

    @Test fun missingAndMalformedTimesNeverProduceInventedTimestamps() {
        assertNull(chatTimestamp(message.copy(createdAt = "invalid"), ChatTimestampMode.ONLY_LIVE, false))
        assertNull(chatTimestamp(ChatMessage(), ChatTimestampMode.LIVE_AND_VIDEO, false))
        assertNull(chatTimestamp(ChatMessage(), ChatTimestampMode.ONLY_VIDEO, true))
        assertNull(chatTimestamp(message.copy(recordingOffsetMs = -1), ChatTimestampMode.ONLY_VIDEO, true))
        assertEquals("12:34", chatTimestamp(message.copy(createdAt = "2026-11-01 12:34:56"),
            ChatTimestampMode.ONLY_LIVE, false, ZoneId.of("UTC")))
    }

    @Test fun timestampPreferenceIsValidatedAndExported() {
        ChatTimestampMode.entries.forEach { assertTrue(SettingsKeys.CHAT_TIMESTAMPS.validator!!.invoke(it.value)) }
        assertFalse(SettingsKeys.CHAT_TIMESTAMPS.validator!!.invoke("unknown"))
        assertTrue(SettingsKeys.ALL_KEYS.contains(SettingsKeys.CHAT_TIMESTAMPS))
    }
}
