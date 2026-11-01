package com.lechixy.kick.service.player

import java.util.Locale
import kotlin.math.ceil

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
    // The refresh interval must remain valid for *all* segments, including a longer earlier one.
    val targetDuration = ceil(durations.max()).toLong().coerceAtLeast(1L)
    val startOffset = lastDuration * if (lowLatency) 1.5 else 2.5
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
