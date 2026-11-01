package com.lechixy.kick.util.player

import java.util.Locale

data class VideoQualityOption(
    val label: String,
    val height: Int,
    val bitrate: Int,
    val dataPerHourText: String
)

object PlayerUtils {
    // Çözünürlük ve bitrate'e göre saatlik yaklaşık veri hesabı: (bitrate bps * 3600s) / (8 * 1024 * 1024)
    fun estimateDataUsagePerHour(bitrate: Int, height: Int): String {
        val effectiveBitrate = if (bitrate > 0) {
            bitrate
        } else {
            when {
                height >= 1080 -> 6_000_000
                height >= 720 -> 3_500_000
                height >= 480 -> 1_500_000
                height >= 360 -> 800_000
                else -> 400_000
            }
        }

        val bytesPerHour = (effectiveBitrate.toDouble() * 3600.0) / 8.0
        val mbPerHour = bytesPerHour / (1024.0 * 1024.0)

        return if (mbPerHour >= 1024) {
            String.format(Locale.US, "%.2f GB/saat", mbPerHour / 1024.0)
        } else {
            String.format(Locale.US, "%.0f MB/saat", mbPerHour)
        }
    }
}
