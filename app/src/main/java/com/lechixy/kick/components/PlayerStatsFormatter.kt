package com.lechixy.kick.components

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal class PlayerStatsFormatter(locale: Locale) {
    private val numbers = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }

    fun number(value: Number?) = value?.let(numbers::format) ?: UNAVAILABLE

    fun bitRate(bitsPerSecond: Long?) = rate(bitsPerSecond, BIT_RATE_UNITS)

    fun byteRate(bytesPerSecond: Long?) = rate(bytesPerSecond, BYTE_RATE_UNITS)

    fun duration(milliseconds: Number?): String {
        val value = milliseconds?.toDouble() ?: return UNAVAILABLE
        val magnitude = abs(value)
        return when {
            magnitude >= 3_600_000 -> "${number(value / 3_600_000)} h"
            magnitude >= 60_000 -> "${number(value / 60_000)} min"
            magnitude >= 1_000 || value == 0.0 -> "${number(value / 1_000)} s"
            else -> "${number(value)} ms"
        }
    }

    private fun rate(value: Long?, units: List<String>): String {
        var scaled = value?.toDouble() ?: return UNAVAILABLE
        var unit = 0
        while (scaled >= 999.995 && unit < units.lastIndex) {
            // At two decimals, 999.995 would otherwise display as 1,000 of the smaller unit.
            scaled /= 1_000
            unit++
        }
        return "${number(scaled)} ${units[unit]}"
    }

    private companion object {
        const val UNAVAILABLE = "—"
        val BIT_RATE_UNITS = listOf("bps", "kbps", "Mbps", "Gbps", "Tbps", "Pbps", "Ebps")
        val BYTE_RATE_UNITS = listOf("B/s", "KB/s", "MB/s", "GB/s", "TB/s", "PB/s", "EB/s")
    }
}
