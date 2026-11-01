package com.lechixy.kick.data.player.local

import com.lechixy.kick.data.player.model.RecordedPlayback
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WatchHistoryTest {
    private fun entry(key: String) = WatchHistoryEntry(key, "channel", "Channel", "Broadcast")

    @Test fun revisitingMovesToFrontWithoutDuplicatesAndRetainsOtherPositions() {
        val first = entry("first").copy(positionMs = 30_000)
        val second = entry("second").copy(positionMs = 90_000)
        val updated = first.copy(positionMs = 10_000)
        val entries = mergeWatchHistory(listOf(second, first), updated)
        assertEquals(listOf("first", "second"), entries.map { it.key })
        assertEquals(10_000L, entries.first().positionMs)
        assertEquals(90_000L, entries.last().positionMs)
    }

    @Test fun historyIsBoundedAndReopeningLiveAsRecordingDoesNotDuplicateIt() {
        assertEquals(20, mergeWatchHistory((0..30).map { entry("$it") }, entry("new")).size)
        val live = entry("live").copy(vodId = "vod", livestreamId = 42)
        val recording = entry("recording").copy(recording = RecordedPlayback("vod", "Title", 100_000))
        assertEquals(listOf(recording), mergeWatchHistory(listOf(live), recording))
    }

    @Test fun savedHistoryRestoresMetadataAndExactPosition() {
        val entry = entry("recording").copy(positionMs = 123_456, durationMs = 300_000,
            recording = RecordedPlayback("vod", "Title", 300_000, progressId = "video-uuid"))
        assertEquals(listOf(entry), Json.decodeFromString<List<WatchHistoryEntry>>(Json.encodeToString(listOf(entry))))
    }
}
