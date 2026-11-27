package com.lechixy.kick.components

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class VideoBarOptions(
    val preserve: Boolean = false,
    val removeHorizontal: Boolean = false,
    val removeVertical: Boolean = false,
    val detectColored: Boolean = true,
) {
    val removesBars get() = removeHorizontal || removeVertical

    fun crop(bars: VideoBars) = VideoBars(
        top = if (removeHorizontal) bars.top else 0f,
        bottom = if (removeHorizontal) bars.bottom else 0f,
        left = if (removeVertical) bars.left else 0f,
        right = if (removeVertical) bars.right else 0f,
    )

    fun mask(bars: VideoBars) = if (preserve) {
        VideoBars(
            top = if (removeHorizontal) 0f else bars.top,
            bottom = if (removeHorizontal) 0f else bars.bottom,
            left = if (removeVertical) 0f else bars.left,
            right = if (removeVertical) 0f else bars.right,
        )
    } else VideoBars()
}

internal data class VideoBars(
    val top: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f,
    val right: Float = 0f,
) {
    val isEmpty get() = this == None
    val width get() = 1f - left - right
    val height get() = 1f - top - bottom

    companion object {
        val None = VideoBars()
    }
}

internal data class AmbientBlackBars(
    val top: Int = 0,
    val bottom: Int = 0,
    val left: Int = 0,
    val right: Int = 0,
) {
    fun normalized(width: Int, height: Int) =
        VideoBars(top.toFloat() / height, bottom.toFloat() / height, left.toFloat() / width, right.toFloat() / width)
}

internal fun detectAmbientBlackBars(
    pixels: IntArray,
    width: Int,
    height: Int,
    detectColored: Boolean = false,
    strict: Boolean = false,
): AmbientBlackBars {
    if (width < 4 || height < 4 || width > pixels.size / height) return AmbientBlackBars()

    fun edge(start: Int, inward: Int, along: Int, depth: Int, length: Int, skipStart: Int, skipEnd: Int): Int {
        val minDepth = (depth + 31) / 32
        val maxDepth = minOf(depth / 3, depth - 2)
        val span = length - skipStart - skipEnd
        val margin = if (skipStart == 0 && skipEnd == 0) span / 5 else span / 10
        val count = minOf(17, span - 2 * margin)
        if (count < 3) return 0
        val samples = IntArray(count) { i -> start + (skipStart + margin + i * (span - 2 * margin - 1) / (count - 1)) * along }
        val colors = IntArray(count) { pixels[samples[it]] }
        // Choose a representative edge color without averaging in logos or intersecting bars.
        val uniformity = if (strict) 13 else 18
        val reference = colors.maxBy { color -> colors.count { colorDistance(color, it) <= uniformity } }
        if (!detectColored && colorDistance(reference, 0) > 20) return 0

        val boundaries = IntArray(count)
        val contrast = if (strict) 34 else 26
        for (i in samples.indices) {
            if (colorDistance(reference, colors[i]) > uniformity) continue
            val base = samples[i]
            var line = 1
            while (line <= maxDepth && colorDistance(reference, pixels[base + line * inward]) <= uniformity) line++
            if (line !in minDepth..maxDepth) continue
            // A rescaled frame can contain one blended row at the boundary.
            val boundary = line
            if (colorDistance(reference, pixels[base + line * inward]) < contrast) line++
            if (line + 1 >= depth) continue
            val inside = pixels[base + (boundary - 1) * inward]
            val outside = pixels[base + line * inward]
            if (colorDistance(reference, outside) >= contrast &&
                colorDistance(reference, pixels[base + (line + 1) * inward]) >= contrast &&
                colorDistance(inside, outside) >= contrast - 6
            ) {
                boundaries[i] = boundary
            }
        }

        val agreement = if (strict) 80 else 65
        val tolerance = (depth / 100).coerceAtLeast(1)
        val consensus = boundaries.maxBy { candidate ->
            if (candidate == 0) 0 else boundaries.count { it > 0 && abs(it - candidate) <= tolerance }
        }
        if (consensus == 0) return 0
        val agreeing = boundaries.filter { it > 0 && abs(it - consensus) <= tolerance }
        return if (agreeing.size * 100 >= count * agreement) agreeing.sorted()[agreeing.size / 2] else 0
    }

    fun horizontal(left: Int, right: Int) = AmbientBlackBars(
        top = edge(0, width, 1, height, width, left, right),
        bottom = edge((height - 1) * width, -width, 1, height, width, left, right),
    )
    fun vertical(top: Int, bottom: Int) = AmbientBlackBars(
        left = edge(0, 1, width, width, height, top, bottom),
        right = edge(width - 1, -1, width, width, height, top, bottom),
    )
    val horizontal = horizontal(0, 0)
    val vertical = vertical(horizontal.top, horizontal.bottom)
    val refined = if (vertical.left > 0 || vertical.right > 0) horizontal(vertical.left, vertical.right) else horizontal
    return refined.copy(left = vertical.left, right = vertical.right)
}

private fun colorDistance(a: Int, b: Int) = maxOf(
    abs((a shr 16 and 255) - (b shr 16 and 255)),
    abs((a shr 8 and 255) - (b shr 8 and 255)),
    abs((a and 255) - (b and 255)),
)

internal class VideoBarTracker {
    private val top = Edge()
    private val bottom = Edge()
    private val left = Edge()
    private val right = Edge()

    var bars = VideoBars.None
        private set

    fun update(next: VideoBars, width: Int, height: Int, paused: Boolean): VideoBars {
        bars = VideoBars(
            top.update(next.top, height, paused),
            bottom.update(next.bottom, height, paused),
            left.update(next.left, width, paused),
            right.update(next.right, width, paused),
        )
        return bars
    }

    private class Edge {
        private var confirmed = 0f
        private var candidate = 0f
        private var frames = 0

        fun update(next: Float, depth: Int, paused: Boolean): Float {
            fun agrees(a: Float, b: Float) = when {
                a == 0f || b == 0f -> a == b
                else -> (abs(a - b) * depth).roundToInt() <= 1
            }
            if (paused) {
                frames = 0
                confirmed = next
            } else if (agrees(next, confirmed)) {
                frames = 0
            } else {
                frames = if (frames > 0 && agrees(next, candidate)) frames + 1 else 1
                candidate = next
                if (frames >= if (next == 0f) 2 else 3) {
                    confirmed = next
                    frames = 0
                }
            }
            return confirmed
        }
    }
}
