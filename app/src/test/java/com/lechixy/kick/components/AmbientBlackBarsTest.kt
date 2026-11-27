package com.lechixy.kick.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientBlackBarsTest {
    @Test
    fun detectsAsymmetricBarsOnAllFourEdges() {
        val width = 48
        val height = 32
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (x < 4 || x >= width - 6 || y < 3 || y >= height - 2) 0xff101010.toInt()
            else 0xff30a060.toInt()
        }

        assertEquals(
            AmbientBlackBars(top = 3, bottom = 2, left = 4, right = 6),
            detectAmbientBlackBars(pixels, width, height),
        )
    }

    @Test
    fun preservesFramesWithNoClearBarBoundary() {
        val width = 48
        val height = 32
        val pixels = IntArray(width * height) { index ->
            val shade = index / width
            0xff000000.toInt() or (shade shl 16) or (shade shl 8) or shade
        }

        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height))
        pixels.fill(0xff000000.toInt())
        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height))
        pixels.fill(0xff841a63.toInt())
        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height, detectColored = true))
        repeat(pixels.size) { index ->
            val shade = (index / width * 6).coerceAtMost(255)
            pixels[index] = 0xff000000.toInt() or (shade shl 16) or (shade shl 8) or shade
        }
        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height, detectColored = true))
    }

    @Test
    fun detectsDifferentSolidColorsAcrossBothAxesOnlyWhenEnabled() {
        val width = 96
        val height = 64
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            when {
                y < 8 && x !in 42..50 -> 0xff6a3070.toInt() + (x % 4)
                y < 8 -> 0xfff0f0f0.toInt() // Small logo within the top bar.
                x < 13 -> 0xff226089.toInt() + (y % 3)
                x >= width - 5 -> 0xffa09240.toInt()
                else -> 0xff20b030.toInt()
            }
        }
        assertEquals(
            AmbientBlackBars(top = 8, left = 13, right = 5),
            detectAmbientBlackBars(pixels, width, height, detectColored = true, strict = true),
        )
        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height))
    }

    @Test
    fun rejectsThinEdgesAndLowContrastEvenWithColoredDetection() {
        val width = 96
        val height = 64
        val pixels = IntArray(width * height) { index ->
            when (index / width) {
                0 -> 0xffc0c0c0.toInt()
                in 1..9 -> 0xff090909.toInt()
                else -> 0xff151515.toInt()
            }
        }
        assertEquals(AmbientBlackBars(), detectAmbientBlackBars(pixels, width, height, detectColored = true))
    }

    @Test
    fun confirmsThicknessAndLossIndependentlyPerEdge() {
        val tracker = VideoBarTracker()
        val initial = VideoBars(top = 0.125f, right = 0.1f)
        val change = VideoBars(top = 0.25f)
        repeat(2) {
            assertEquals(VideoBars.None, tracker.update(initial, 100, 80, paused = false))
        }
        assertEquals(initial, tracker.update(initial, 100, 80, paused = false))
        assertEquals(initial, tracker.update(change, 100, 80, paused = false))
        assertEquals(VideoBars(top = initial.top), tracker.update(change, 100, 80, paused = false))
        assertEquals(change, tracker.update(change, 100, 80, paused = false))
        assertEquals(VideoBars.None, tracker.update(VideoBars.None, 100, 80, paused = true))
    }

    @Test
    fun removingOneAxisLeavesTheOtherAxisMasked() {
        val options = VideoBarOptions(preserve = true, removeHorizontal = true)
        val bars = VideoBars(top = 0.10f, bottom = 0.15f, left = 0.05f, right = 0.08f)
        assertEquals(VideoBars(top = 0.10f, bottom = 0.15f), options.crop(bars))
        assertEquals(VideoBars(left = 0.05f, right = 0.08f), options.mask(bars))
    }
}
