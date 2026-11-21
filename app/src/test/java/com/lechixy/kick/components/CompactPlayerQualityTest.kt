package com.lechixy.kick.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactPlayerQualityTest {
    @Test
    fun usesStableResolutionTiersAndCapsLargeWindows() {
        assertEquals(360, compactVideoHeightLimit(widthPx = 0, preferredHeight = -1))
        assertEquals(144, compactVideoHeightLimit(widthPx = 256, preferredHeight = -1))
        assertEquals(240, compactVideoHeightLimit(widthPx = 257, preferredHeight = -1))
        assertEquals(360, compactVideoHeightLimit(widthPx = 500, preferredHeight = -1))
        assertEquals(480, compactVideoHeightLimit(widthPx = 1440, preferredHeight = -1))
    }

    @Test
    fun keepsLowerPreferencesAndCapsHigherOnes() {
        assertEquals(240, compactVideoHeightLimit(widthPx = 640, preferredHeight = 240))
        assertEquals(360, compactVideoHeightLimit(widthPx = 640, preferredHeight = 1080))
    }
}
