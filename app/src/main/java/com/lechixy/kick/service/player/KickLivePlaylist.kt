package com.lechixy.kick.service.player

import java.util.Locale
import kotlin.math.roundToLong

/** Adapt IVS prefetch playlists without changing native LL-HLS, VOD, or upstream start policy. */
internal fun transformKickLivePlaylist(text: String, lowLatency: Boolean): String {
    val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
    if (lines.none { it.startsWith("#EXTINF:") } ||
        lines.any { it == "#EXT-X-ENDLIST" || it == "#EXT-X-PLAYLIST-TYPE:VOD" } ||
        lines.any { it.startsWith("#EXT-X-PART:") || it.startsWith("#EXT-X-PART-INF:") }
    ) return text

    val durations = lines.filter { it.startsWith("#EXTINF:") }
        .mapNotNull { it.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() }
        .filter { it.isFinite() && it > 0.0 }
    if (durations.isEmpty()) return text

    val lastDuration = durations.last()
    // RFC 8216 compares rounded EXTINF durations with TARGETDURATION. Ceiling a
    // 2.001s segment to 3s unnecessarily postpones discovery of the next segment.
    val targetDuration = durations.max().roundToLong().coerceAtLeast(1L)
    // PREFETCH advertises a full segment that is still being produced. Keep an
    // two-second reserve, including when an earlier long segment slows reloads.
    val startOffset = if (lowLatency) {
        maxOf(lastDuration * 1.5, targetDuration.toDouble()) + 2.0
    } else lastDuration * 2.5
    val hasStart = lines.any { it.startsWith("#EXT-X-START:") }
    var convertedPrefetch = false
    return buildString(text.length + 256) {
        for (line in lines) {
            when {
                line == "#EXTM3U" -> {
                    appendLine(line)
                    if (!hasStart) {
                        // Decode from the preceding keyframe, but discard samples before the
                        // actual target instead of rounding a 3s target back to a 4s boundary.
                        append(String.format(Locale.US, "#EXT-X-START:TIME-OFFSET=-%.3f", startOffset))
                        if (lowLatency) append(",PRECISE=YES")
                        append('\n')
                    }
                }
                line.startsWith("#EXT-X-TARGETDURATION:") ->
                    appendLine("#EXT-X-TARGETDURATION:$targetDuration")
                line.startsWith("#EXT-X-PREFETCH:") -> {
                    val url = line.substringAfter(':').trim()
                    if (lowLatency && !convertedPrefetch && url.isNotEmpty()) {
                        appendLine(String.format(Locale.US, "#EXTINF:%.6f,live", lastDuration))
                        appendLine(url)
                        convertedPrefetch = true
                    }
                }
                else -> appendLine(line)
            }
        }
    }
}
