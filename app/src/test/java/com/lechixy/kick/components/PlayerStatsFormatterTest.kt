package com.lechixy.kick.components

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStatsFormatterTest {
    private val format = PlayerStatsFormatter(Locale.US)

    @Test
    fun bitRatesUseDecimalUnitsAndPromoteRoundedValues() {
        assertEquals("—", format.bitRate(null))
        assertEquals("999 bps", format.bitRate(999))
        assertEquals("1.5 kbps", format.bitRate(1_500))
        assertEquals("1 Mbps", format.bitRate(999_999))
        assertEquals("2.5 Gbps", format.bitRate(2_500_000_000))
    }

    @Test
    fun networkActivityKeepsByteUnitsAndLocalizesDecimals() {
        val turkish = PlayerStatsFormatter(Locale.forLanguageTag("tr-TR"))
        assertEquals("0 B/s", turkish.byteRate(0))
        assertEquals("750 B/s", turkish.byteRate(750))
        assertEquals("25,5 KB/s", turkish.byteRate(25_500))
        assertEquals("1,25 MB/s", turkish.byteRate(1_250_000))
    }

    @Test
    fun durationsKeepSubsecondPrecisionAndSignedOffsets() {
        assertEquals("—", format.duration(null))
        assertEquals("0 s", format.duration(0))
        assertEquals("-12.5 ms", format.duration(-12.5))
        assertEquals("12.74 s", format.duration(12_740))
        assertEquals("1.5 min", format.duration(90_000))
        assertEquals("2 h", format.duration(7_200_000))
    }
}
