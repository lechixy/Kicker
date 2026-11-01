package com.lechixy.kick.data.chat.model

import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RecordedChatTimelineTest {
    private val start = Instant.parse("2026-10-31T22:17:30Z")
    private fun message(id: String, second: Int) = ChatMessage(
        id = id, content = "message $id", createdAt = start.plusSeconds(second.toLong()).toString())

    @Test fun historyRequestTimesFollowRecordingClockInFiveSecondSteps() {
        val timeline = RecordedChatTimeline(start)
        assertEquals("2026-10-31T22:17:47.000Z", timeline.requestTime(17_000))
        assertEquals("2026-10-31T22:17:52.000Z", timeline.requestTime(22_000))
        assertEquals(start, recordingStartInstant("2026-10-31 22:17:30"))
        assertEquals(start, recordingStartInstant("2026-10-31T22:17:30.000Z"))
        assertNull(recordingStartInstant("invalid"))
    }

    @Test fun messagesAppearAtTheirTimeAndDuplicateUnsortedBatchesAreMerged() {
        val timeline = RecordedChatTimeline(start)
        timeline.add(listOf(message("future", 16), message("now", 15), message("earlier", 14)), 15_000)
        timeline.add(listOf(message("now", 15)), 15_000)
        assertEquals(listOf("earlier"), timeline.visible(14_999).map { it.id })
        assertEquals(listOf("earlier", "now"), timeline.visible(15_000).map { it.id })
        assertEquals(15_000L, timeline.visible(15_000).last().recordingOffsetMs)
        assertEquals(3, timeline.visible(16_000).size)
        // A paused clock gives the same result; seeking back hides later messages immediately.
        assertEquals(timeline.visible(15_000), timeline.visible(15_000))
        assertEquals(listOf("earlier"), timeline.visible(14_000).map { it.id })
    }

    @Test fun discontinuousSeekClearsPreviousTimelineAndRejectsOutOfWindowMessages() {
        val timeline = RecordedChatTimeline(start)
        timeline.add(listOf(message("old", 5)), 5_000)
        timeline.clear()
        timeline.add(listOf(message("old", 5), message("seek", 300), message("tooFar", 600),
            ChatMessage(id = "invalid", createdAt = "invalid")), 300_000)
        assertEquals(listOf("seek"), timeline.visible(300_000).map { it.id })
    }

    @Test fun replayUsesExistingHistoryParserIncludingEncodedMetadataAndSelectedBadges() {
        val json = Json { ignoreUnknownKeys = true }
        val response = json.decodeFromString<ChatHistoryResponse>("""{"data":{"messages":[{"id":"sample","chat_id":27496659,"user_id":121013363,"content":"hello [emote:1:Hi]","type":"message","metadata":"{\"message_ref\":\"1793485064512\"}","sender":{"id":121013363,"slug":"ekiiny","username":"ekiiny","identity":{"color":"#FEA0CF","badges":[],"badges_v2":[{"name":"level","badge_type":"global","selected":true,"metadata":{"level":33},"sort_order":1},{"name":"hidden","selected":false,"sort_order":2}]}},"created_at":"2026-10-31T22:17:45Z","thread_parent_id":null}],"cursor":"1793485075782353","pinned_message":null},"message":"OK"}""")
        val timeline = RecordedChatTimeline(start)
        timeline.add(response.data!!.messages, 15_000)
        val message = timeline.visible(15_000).single()
        assertEquals(15_000L, message.recordingOffsetMs)
        assertEquals("1793485064512", message.metadata?.messageRef)
        assertEquals(listOf("level"), message.sender?.identity?.badgesV2?.map { it.name })
        assertEquals("hello [emote:1:Hi]", message.content)
    }
}
