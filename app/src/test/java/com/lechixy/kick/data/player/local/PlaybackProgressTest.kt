package com.lechixy.kick.data.player.local

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.VideoIdentity
import com.lechixy.kick.data.player.model.toRecording

class PlaybackProgressTest {
    @Test fun progressIsIsolatedPerRecordingAndCanMoveBackwards() {
        PlaybackProgressStore.save("test-a", 30_000, 100_000)
        PlaybackProgressStore.save("test-b", 80_000, 200_000)
        PlaybackProgressStore.save("test-a", 10_000, 100_000)
        assertEquals(10_000L, PlaybackProgressStore.get("test-a")?.positionMs)
        assertEquals(80_000L, PlaybackProgressStore.get("test-b")?.positionMs)
        assertNull(PlaybackProgressStore.get("test-unwatched"))
    }

    @Test fun unknownTimelineDurationDoesNotEraseKnownDuration() {
        PlaybackProgressStore.save("test-duration", 10_000, 100_000)
        PlaybackProgressStore.save("test-duration", 20_000, -1)
        assertEquals(100_000L, PlaybackProgressStore.get("test-duration")?.durationMs)
        assertEquals(0.2f, PlaybackProgressStore.get("test-duration")!!.fraction, 0.0001f)
    }

    @Test fun completedRecordingsRestartWhileUnfinishedOnesResume() {
        assertEquals(40_000L, PlaybackProgress(40_000, 100_000).resumePosition())
        assertEquals(0L, PlaybackProgress(100_000, 100_000).resumePosition())
        assertEquals(0L, PlaybackProgress(99_500, 100_000).resumePosition())
        assertEquals(0L, PlaybackProgress(80_000, 100_000).resumePosition(60_000))
        assertEquals(40_000L, PlaybackProgress(40_000, 0).resumePosition())
    }

    @Test fun invalidPositionsAreIgnoredAndCardProgressIsBounded() {
        PlaybackProgressStore.save("test-bounds", 150_000, 100_000)
        PlaybackProgressStore.save("test-bounds", -10, 100_000)
        assertEquals(100_000L, PlaybackProgressStore.get("test-bounds")?.positionMs)
        assertEquals(1f, PlaybackProgressStore.get("test-bounds")!!.fraction, 0f)
        assertEquals(0f, PlaybackProgress(0, 100_000).fraction, 0f)
        assertEquals(0f, PlaybackProgress(40_000, 0).fraction, 0f)
    }

    @Test fun serializedProgressRetainsPositionsAcrossReload() {
        val saved = mapOf("video-a" to PlaybackProgress(12_345, 80_000), "video-b" to PlaybackProgress(90_000, 90_000))
        val restored = Json.decodeFromString<Map<String, PlaybackProgress>>(Json.encodeToString(saved))
        assertEquals(saved, restored)
        assertEquals(12_345L, restored.getValue("video-a").resumePosition())
        assertEquals(0L, restored.getValue("video-b").resumePosition())
    }

    @Test fun addingVodIdToApiResponseDoesNotChangeProgressKey() {
        val legacy = ChannelVideo(id = 42, video = VideoIdentity("legacy-video-uuid"))
        assertEquals("legacy-video-uuid", legacy.toRecording()?.progressId)
        assertEquals(legacy.toRecording()?.progressId, legacy.copy(vodId = "stream-vod-id").toRecording()?.progressId)
    }
}
