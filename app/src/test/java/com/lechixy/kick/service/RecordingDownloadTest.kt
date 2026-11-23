package com.lechixy.kick.service

import com.lechixy.kick.components.parseDownloadTime
import com.lechixy.kick.data.player.local.RecordingDownloadTask
import com.lechixy.kick.data.player.model.RecordingDownloadRange
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingDownloadTest {
    @Test fun snapshotKeepsCompleteSegmentsAndTheirDecodingInformation() {
        val playlist = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-TARGETDURATION:6
            #EXT-X-START:TIME-OFFSET=-2
            #EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES
            #EXT-X-KEY:METHOD=AES-128,URI="key"
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-PART:DURATION=1.0,URI="42.1.m4s"
            #EXTINF:6.000,
            #EXT-X-BYTERANGE:1000@0
            42.m4s
            #EXT-X-PART:DURATION=1.0,URI="43.1.m4s"
            #EXT-X-PRELOAD-HINT:TYPE=PART,URI="43.2.m4s"
            #EXT-X-PREFETCH:43.m4s
        """.trimIndent()
        val expected = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-TARGETDURATION:6
            #EXT-X-KEY:METHOD=AES-128,URI="key"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.000,
            #EXT-X-BYTERANGE:1000@0
            42.m4s
            #EXT-X-ENDLIST
        """.trimIndent() + "\n"
        assertEquals(expected, freezeLiveDownloadPlaylist(playlist))
        assertEquals(expected, freezeLiveDownloadPlaylist(expected))
    }

    @Test fun timeInputHandlesHoursAndRejectsOverflow() {
        assertEquals(3_723_000L, parseDownloadTime("1:02:03"))
        assertEquals(5_402_000L, parseDownloadTime("90:02"))
        assertEquals(45_000L, parseDownloadTime("45"))
        listOf("", "1:", "-1", "1:60", "1:60:00", "2562047788015:59:59").forEach {
            assertNull(parseDownloadTime(it))
        }
    }

    @Test fun legacyQueueEntriesStayWholeAndNewEntriesKeepTheirSelection() {
        val original = Json.decodeFromString<RecordingDownloadTask>(
            """{"id":"task","recording":{"id":"recording","title":"VOD","durationMs":90000}}"""
        )
        assertNull(original.selection)
        val partial = original.copy(selection = RecordingDownloadSelection(
            range = RecordingDownloadRange(15_000, 45_000),
            estimatedBytes = 1_000_000,
        ))
        assertEquals(partial, Json.decodeFromString<RecordingDownloadTask>(Json.encodeToString(partial)))
    }
}
