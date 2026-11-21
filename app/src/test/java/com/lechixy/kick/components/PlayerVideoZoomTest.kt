package com.lechixy.kick.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVideoZoomTest {
    private val viewport = IntSize(400, 200)

    @Test fun offCenterPinchKeepsThePointUnderTheMovingCentroid() {
        val zoom = PlayerVideoZoom().transform(viewport, Offset(300f, 50f), Offset(20f, -20f), 2f)

        assertEquals(2f, zoom.scale, 0f)
        assertEquals(-0.2f, zoom.normalizedTranslation.x, 0.0001f)
        assertEquals(0.15f, zoom.normalizedTranslation.y, 0.0001f)
    }

    @Test fun draggingAtConstantScaleStaysWithinTheZoomedVideo() {
        val zoom = PlayerVideoZoom().transform(viewport, Offset(200f, 100f), Offset.Zero, 10f)
        val panned = zoom.transform(viewport, Offset(200f, 100f), Offset(1_000f, -1_000f), 1f)

        assertEquals(4f, panned.scale, 0f)
        assertEquals(Offset(1.5f, -1.5f), panned.normalizedTranslation)
    }

    @Test fun zoomingOutToOneClearsThePan() {
        val zoom = PlayerVideoZoom().transform(viewport, Offset(300f, 50f), Offset.Zero, 2f)

        assertEquals(PlayerVideoZoom(), zoom.transform(viewport, Offset(300f, 50f), Offset.Zero, 0.75f))
    }
}
