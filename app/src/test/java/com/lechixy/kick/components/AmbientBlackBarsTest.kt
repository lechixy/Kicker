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
    }
}
