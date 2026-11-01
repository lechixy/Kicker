package com.lechixy.kick.service.player

import org.junit.Assert.*
import org.junit.Test

class KickLivePlaylistTest {
    private val live = """
        #EXTM3U
        #EXT-X-TARGETDURATION:6
        #EXT-X-MEDIA-SEQUENCE:42
        #EXT-X-PROGRAM-DATE-TIME:2026-10-29T19:49:11.770Z
        #EXTINF:2.000,live
        complete-42.ts
        #EXTINF:2.000,live
        complete-43.ts
        #EXT-X-PREFETCH:active-44.ts
        #EXT-X-PREFETCH:future-45.ts
    """.trimIndent()

    @Test fun ivsLowLatencyKeepsReserveAndLoadsOnlyActivePrefetch() {
        val result = transformKickLivePlaylist(live, true)
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-5.000,PRECISE=YES\n"))
        assertTrue(result.contains("#EXT-X-TARGETDURATION:2\n"))
        assertTrue(result.contains("#EXT-X-MEDIA-SEQUENCE:42\n"))
        assertTrue(result.contains("#EXT-X-PROGRAM-DATE-TIME:2026-10-29T19:49:11.770Z"))
        assertEquals(3, result.lineSequence().count { it.startsWith("#EXTINF:") })
        assertTrue(result.contains("active-44.ts"))
        assertFalse(result.contains("future-45.ts"))
        assertFalse(result.contains("#EXT-X-PREFETCH:"))
    }

    @Test fun normalModeKeepsConservativeStartAndNeverRequestsPrefetch() {
        val result = transformKickLivePlaylist(live, false)
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-5.000\n"))
        assertFalse(result.contains("PRECISE=YES"))
        assertFalse(result.contains("active-44.ts"))
        assertFalse(result.contains("future-45.ts"))
    }

    @Test fun targetDurationAccommodatesLongestSegmentRatherThanOnlyLast() {
        val mixed = live.replace("#EXTINF:2.000,live\ncomplete-42.ts", "#EXTINF:4.167,live\ncomplete-42.ts")
        val result = transformKickLivePlaylist(mixed, true)
        assertTrue(result.contains("#EXT-X-TARGETDURATION:4\n"))
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-6.000,PRECISE=YES\n"))
    }

    @Test fun longSegmentHasPreciseTargetWithoutInvalidThreeSecondDuration() {
        val result = transformKickLivePlaylist(live.replace("2.000", "4.167"), true)
        assertTrue(result.contains("#EXT-X-TARGETDURATION:4\n"))
        val offset = result.lineSequence().first { it.startsWith("#EXT-X-START:") }
            .substringAfter("TIME-OFFSET=").substringBefore(',').toDouble()
        assertEquals(-8.2505, offset, 0.001)
        assertTrue(result.contains(",PRECISE=YES\n"))
    }

    @Test fun existingStartIsPreservedWithoutAddingSecondPolicy() {
        val result = transformKickLivePlaylist(live.replace("#EXTM3U", "#EXTM3U\n#EXT-X-START:TIME-OFFSET=-8"), true)
        assertEquals(1, result.lineSequence().count { it.startsWith("#EXT-X-START:") })
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-8\n"))
    }

    @Test fun vodEndedMasterAndNativeLowLatencyPlaylistsAreUnchanged() {
        val examples = listOf(
            "$live\n#EXT-X-ENDLIST",
            live.replace("#EXTM3U", "#EXTM3U\n#EXT-X-PLAYLIST-TYPE:VOD"),
            live.replace("#EXTM3U", "#EXTM3U\n#EXT-X-PART-INF:PART-TARGET=0.333"),
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000\nmain.m3u8",
            "#EXTM3U\n#EXTINF:NaN,live\nsegment.ts"
        )
        examples.forEach { assertEquals(it, transformKickLivePlaylist(it, true)) }
    }

    @Test fun windowsLineEndingsDoNotDisablePreciseStart() {
        val result = transformKickLivePlaylist(live.replace("\n", "\r\n"), true)
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-5.000,PRECISE=YES\n"))
    }

    @Test fun smallEncoderTimingDriftDoesNotAddASecondToReloadInterval() {
        val result = transformKickLivePlaylist(live.replace("2.000", "2.001"), true)
        assertTrue(result.contains("#EXT-X-TARGETDURATION:2\n"))
    }

    @Test fun longerEarlierSegmentKeepsReloadAndStartupReserveConsistent() {
        val mixed = live.replace("#EXTINF:2.000,live\ncomplete-42.ts", "#EXTINF:2.783,live\ncomplete-42.ts")
        val result = transformKickLivePlaylist(mixed, true)
        assertTrue(result.contains("#EXT-X-TARGETDURATION:3\n"))
        assertTrue(result.contains("#EXT-X-START:TIME-OFFSET=-5.000,PRECISE=YES\n"))
    }
}
