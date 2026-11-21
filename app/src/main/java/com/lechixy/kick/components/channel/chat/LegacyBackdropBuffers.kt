package com.lechixy.kick.components.channel.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp
import kotlin.math.roundToInt

internal class LegacyBackdropBuffers(
    val size: IntSize,
    downsample: Boolean,
    frosted: Boolean,
) {
    val raw = bitmaps(size)
    private val soft = if (downsample) {
        val scale = (56f / maxOf(size.width, size.height)).coerceAtMost(1f)
        bitmaps(IntSize(
            (size.width * scale).roundToInt().coerceAtLeast(1),
            (size.height * scale).roundToInt().coerceAtLeast(1),
        ))
    } else null
    private val blurred = if (frosted) bitmaps(size) else null
    private val pixels = if (frosted) IntArray(size.width * size.height) else IntArray(0)
    private val horizontal = IntArray(pixels.size)
    private val filter = Paint(Paint.FILTER_BITMAP_FLAG)

    fun prepare(source: Bitmap, index: Int, radius: Float): Pair<Bitmap, Bitmap?> {
        val softSample = soft?.get(index)?.also {
            Canvas(it).drawBitmap(source, null, Rect(0, 0, it.width, it.height), filter)
        } ?: source
        val frostedSample = blurred?.get(index)?.also {
            val sigma = radius.coerceAtLeast(0.5f)
            val reach = (sigma * 2f).roundToInt().coerceAtLeast(1)
            val weights = FloatArray(reach * 2 + 1) { tap ->
                val distance = (tap - reach) / sigma
                exp(-0.5f * distance * distance)
            }
            val total = weights.sum()
            for (tap in weights.indices) weights[tap] /= total
            source.getPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
            blurPass(pixels, horizontal, weights, across = true)
            blurPass(horizontal, pixels, weights, across = false)
            it.setPixels(pixels, 0, size.width, 0, 0, size.width, size.height)
        }
        return softSample to frostedSample
    }

    private fun blurPass(source: IntArray, output: IntArray, weights: FloatArray, across: Boolean) {
        val stride = if (across) 1 else size.width
        val length = if (across) size.width else size.height
        val reach = weights.size / 2
        val lines = if (across) size.height else size.width
        for (line in 0 until lines) {
            val start = if (across) line * size.width else line
            for (position in 0 until length) {
                var alpha = 0f
                var red = 0f
                var green = 0f
                var blue = 0f
                for (tap in weights.indices) {
                    val reflected = (position + tap - reach).mod(2 * length)
                    val sample = if (reflected < length) reflected else 2 * length - 1 - reflected
                    val color = source[start + sample * stride]
                    val weight = weights[tap]
                    alpha += (color ushr 24) * weight
                    red += ((color ushr 16) and 255) * weight
                    green += ((color ushr 8) and 255) * weight
                    blue += (color and 255) * weight
                }
                output[start + position * stride] =
                    (alpha.roundToInt() shl 24) or (red.roundToInt() shl 16) or
                        (green.roundToInt() shl 8) or blue.roundToInt()
            }
        }
    }

    private fun bitmaps(size: IntSize) = List(2) {
        Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    }
}
