package com.lechixy.kick.util

import android.text.format.DateUtils
import android.icu.number.NumberFormatter
import android.icu.number.Notation
import android.icu.number.Precision
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

import com.lechixy.kick.R
import com.lechixy.kick.data.channel.model.VideoThumbnail

object FormatUtils {

    private val compactNumbers by lazy {
        NumberFormatter.with().notation(Notation.compactShort()).precision(Precision.maxFraction(1))
    }

    fun formatViewersCount(count: Int?, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(count ?: 0)

    fun formatCompactCount(count: Long, locale: Locale): String =
        compactNumbers.locale(locale).format(count).toString()

    fun formatFollowersCount(countStr: String?, locale: Locale = Locale.getDefault()): String {
        val count = countStr?.toBigDecimalOrNull() ?: return NumberFormat.getIntegerInstance(locale).format(0)
        return compactNumbers.locale(locale).format(count).toString()
    }

    // Tarihi UTC olarak parse eder ve Epoch millis döndürür
    fun parseUtcStartTimeToMillis(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        val normalized = dateString
            .trim()
            .replace('T', ' ')
            .substringBefore('.')
            .trimEnd('Z')
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC") // Kritik nokta: UTC olarak yorumla
            }
            sdf.parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    fun formatDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatLiveOffset(offsetMs: Long): String {
        val totalSeconds = (offsetMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.US, "-%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "-%02d:%02d", minutes, seconds)
            else -> String.format(Locale.US, "-00:%02d", seconds)
        }
    }

    fun formatLastLive(
        context: android.content.Context,
        startTimeStr: String?,
        showPrefix: Boolean = false
    ): String {
        val relativeTime = formatRelativeTime(context, startTimeStr)
            ?: return context.getString(if (showPrefix) R.string.channel_no_recent_live else R.string.channel_live_unknown)
        return if (showPrefix) context.getString(R.string.channel_last_live, relativeTime) else relativeTime
    }

    fun formatRelativeTime(context: android.content.Context, startTimeStr: String?): String? {
        val start = com.lechixy.kick.data.chat.model.recordingStartInstant(startTimeStr) ?: return null
        val seconds = (System.currentTimeMillis() - start.toEpochMilli()) / 1000.0
        val magnitude = kotlin.math.abs(seconds)
        val (divisor, unit) = when {
            magnitude < 3600 -> 60.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.MINUTE
            magnitude < 86400 -> 3600.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.HOUR
            magnitude < 604800 -> 86400.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.DAY
            magnitude < 2592000 -> 604800.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.WEEK
            magnitude < 31536000 -> 2592000.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.MONTH
            else -> 31536000.0 to android.icu.text.RelativeDateTimeFormatter.RelativeDateTimeUnit.YEAR
        }
        val locale = context.resources.configuration.locales[0]
        return android.icu.text.RelativeDateTimeFormatter.getInstance(locale)
            .formatNumeric(-(seconds / divisor).toLong().toDouble(), unit)
    }

    fun extractVideoThumbnailUrl(thumbnail: VideoThumbnail?): String? {
        if (thumbnail == null) return null
        if (!thumbnail.srcset.isNullOrBlank()) {
            val parts = thumbnail.srcset.split(",")
            if (parts.isNotEmpty()) {
                val candidate = parts.firstOrNull { it.contains("720.webp") } ?: parts.first()
                return candidate.trim().split(" ").firstOrNull()
            }
        }
        if (!thumbnail.src.isNullOrBlank()) {
            return if (thumbnail.src.startsWith("http")) {
                thumbnail.src
            } else {
                "https://images.kick.com/video_thumbnails/${thumbnail.src}"
            }
        }
        return null
    }
}
