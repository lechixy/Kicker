package com.lechixy.kick.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVideoZoomTest {
    private val viewport = IntSize(400, 200)

    @Test fun asymmetricBarCropFitsAndCentersTheContentBeforeZoom() {
        val source = Rect(0f, 25f, 400f, 175f)
        val crop = VideoBars(top = 0.2f, left = 0.25f)
        val geometry = videoCropGeometry(source, viewport, crop)

        assertEquals(Rect(100f, 55f, 400f, 175f), geometry.source)
        assertEquals(0f, geometry.fitted.left, 0.001f)
        assertEquals(20f, geometry.fitted.top, 0.001f)
        assertEquals(180f, geometry.fitted.bottom, 0.001f)
        assertEquals(1.25f, playerFillScale(viewport, IntSize(400, 160)), 0f)
        assertEquals(geometry.fitted, PlayerVideoZoom().mapBounds(geometry.fitted, viewport))
    }

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

        assertEquals(PlayerVideoZoom(), zoom.transform(viewport, Offset(300f, 50f), Offset.Zero, 0.5f))
    }

    @Test fun zoomedBoundsIncludeViewportRelativePanAndLetterboxing() {
        val video = Rect(0f, 25f, 400f, 175f)
        val zoom = PlayerVideoZoom(scale = 2f, normalizedTranslation = Offset(0.25f, -0.25f))

        assertEquals(Rect(-100f, -100f, 700f, 200f), zoom.mapBounds(video, viewport))
        assertEquals(video, PlayerVideoZoom().mapBounds(video, viewport))
    }

    @Test fun pinchSnapsToFillThenContinuesPastItWithOneTransitionEach() {
        val gesture = PlayerPinchZoom()
        val videoSize = IntSize(400, 160)
        val focus = Offset(300f, 100f)
        val pan = Offset(20f, 0f)
        val fit = PlayerVideoZoom()
        assertEquals(fit to null, gesture.transform(fit, viewport, videoSize, focus, pan, 1.025f))

        val (filled, fillTransition) = gesture.transform(fit, viewport, videoSize, focus, pan, 1.025f)
        assertEquals(PlayerVideoZoom(1.25f, isFill = true), filled)
        assertEquals(PlayerZoomTransition.Fill, fillTransition)
        assertEquals(filled to null, gesture.transform(filled, viewport, videoSize, focus, pan, 1f))
        assertEquals(filled to null, gesture.transform(filled, viewport, videoSize, focus, pan, 1.04f))

        assertEquals(filled to null,
            gesture.transform(filled, viewport, videoSize, focus, pan, 1.13f, fillSettled = false))
        val (free, freeTransition) = gesture.transform(filled, viewport, videoSize, focus, pan, 1f)
        assertEquals(PlayerZoomTransition.Free, freeTransition)
        assertTrue(free.scale > filled.scale)
        assertTrue(free.normalizedTranslation.x < 0f)
        val (panned, transition) = gesture.transform(free, viewport, videoSize, focus, pan, 1f)
        assertTrue(panned.normalizedTranslation.x > free.normalizedTranslation.x)
        assertEquals(null, transition)

        assertEquals(filled to PlayerZoomTransition.Fill,
            gesture.transform(free, viewport, videoSize, focus, pan, 0.9f))
        assertEquals(fit to PlayerZoomTransition.Fit,
            gesture.transform(filled, viewport, videoSize, focus, pan, 0.92f))
    }

    @Test fun pinchBelowOneReportsTheLimitOnceUntilTheNextGesture() {
        val gesture = PlayerPinchZoom()
        val fit = PlayerVideoZoom()
        val center = Offset(200f, 100f)

        assertEquals(fit to PlayerZoomTransition.Minimum,
            gesture.transform(fit, viewport, viewport, center, Offset.Zero, 0.94f))
        assertEquals(fit to null, gesture.transform(fit, viewport, viewport, center, Offset.Zero, 0.9f))
        gesture.reset()
        assertEquals(fit to PlayerZoomTransition.Minimum,
            gesture.transform(fit, viewport, viewport, center, Offset.Zero, 0.94f))
    }
}
