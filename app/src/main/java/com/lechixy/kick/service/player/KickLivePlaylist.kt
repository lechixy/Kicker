package com.lechixy.kick.service.player

import java.util.Locale
import kotlin.math.roundToLong

/** Give standard live playback more reserve while honoring explicit upstream start positions. */
internal fun transformKickLivePlaylist(text: String, lowLatency: Boolean): String {
    val lines = text.lineSequence().map { it.trimEnd('\r') }.toList()
    if (lines.none { it.startsWith("#EXTINF:") } ||
        lines.any { it == "#EXT-X-ENDLIST" || it == "#EXT-X-PLAYLIST-TYPE:VOD" }
    ) return text

    val nativeLowLatency = lines.any {
        it.startsWith("#EXT-X-PART:") || it.startsWith("#EXT-X-PART-INF:")
    }
    val hasStart = lines.any { it.startsWith("#EXT-X-START:") }
    if (nativeLowLatency && (lowLatency || hasStart)) return text

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
    val lowLatencyOffset = maxOf(lastDuration * 1.5, targetDuration.toDouble()) + 2.0
    val startOffset = if (lowLatency) lowLatencyOffset else {
        maxOf(targetDuration * 3.0, lowLatencyOffset + 3.0)
    }
    var convertedPrefetch = false
    return buildString(text.length + 256) {
        for (line in lines) {
            when {
                line == "#EXTM3U" -> {
                    appendLine(line)
                    if (!hasStart) {
                        // Use the chosen position even when it falls between keyframes.
                        appendLine(String.format(Locale.US, "#EXT-X-START:TIME-OFFSET=-%.3f,PRECISE=YES", startOffset))
                    }
                }
                nativeLowLatency -> appendLine(line)
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
