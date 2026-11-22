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
        val merged = mergeWatchHistory(listOf(live), recording).single()
        assertEquals(recording.key, merged.key)
        assertEquals(recording.recording, merged.recording)
        assertEquals(42L, merged.livestreamId)
    }

    @Test fun archiveWithDifferentPlaybackIdsMatchesLiveByLivestreamId() {
        val live = entry("live").copy(livestreamId = 42, positionMs = 85_000, fallbackThumbnail = "banner")
        val archived = entry("recording").copy(positionMs = 95_000,
            recording = RecordedPlayback("vod", "Title", 100_000, thumbnail = "archive",
                progressId = "video-uuid", livestreamId = 42))
        assertTrue(live.matchesBroadcast(archived))
        val merged = mergeWatchHistory(listOf(live), archived).single()
        assertEquals(95_000L, merged.positionMs)
        assertEquals("archive", merged.thumbnail)
        assertEquals("banner", merged.fallbackThumbnail)
    }

    @Test fun legacyArchiveMatchesByBroadcastTimeWithoutMergingClipsOrOtherChannels() {
        val live = entry("live").copy(livestreamId = 42, startTime = "2026-10-09 16:29:59")
        val archived = entry("recording").copy(
            recording = RecordedPlayback("video-uuid", "Title", 100_000, startTime = "2026-10-09T16:29:59Z"))
        assertTrue(live.matchesBroadcast(archived))
        assertFalse(live.matchesBroadcast(archived.copy(channelSlug = "another-channel")))
        assertFalse(live.matchesBroadcast(archived.copy(recording = archived.recording!!.copy(id = "clip_123"))))
        assertFalse(live.matchesBroadcast(archived.copy(recording = archived.recording.copy(livestreamId = 43))))
    }

    @Test fun savedHistoryRestoresMetadataAndExactPosition() {
        val entry = entry("recording").copy(positionMs = 123_456, durationMs = 300_000,
            recording = RecordedPlayback("vod", "Title", 300_000, progressId = "video-uuid"))
        assertEquals(listOf(entry), Json.decodeFromString<List<WatchHistoryEntry>>(Json.encodeToString(listOf(entry))))
    }
}
