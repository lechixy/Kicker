package com.lechixy.kick.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize

private const val MAX_VIDEO_ZOOM = 4f
private const val MIN_FILL_ZOOM = 1.08f
private const val FIT_GESTURE_THRESHOLD = 0.05f
private const val FREE_ZOOM_SCALE_GAP = 0.2f
private const val LEAVE_FILL_THRESHOLD = 0.075f

internal data class PlayerVideoZoom(
    val scale: Float = 1f,
    val normalizedTranslation: Offset = Offset.Zero,
    val isFill: Boolean = false,
) {
    fun mapBounds(bounds: Rect, viewport: IntSize): Rect {
        val center = Offset(viewport.width / 2f, viewport.height / 2f)
        val pan = Offset(normalizedTranslation.x * viewport.width, normalizedTranslation.y * viewport.height)
        val origin = center * (1f - scale) + pan
        return Rect(bounds.topLeft * scale + origin, bounds.bottomRight * scale + origin)
    }

    fun interpolateTo(target: PlayerVideoZoom, fraction: Float): PlayerVideoZoom = target.copy(
        scale = scale + (target.scale - scale) * fraction,
        normalizedTranslation = normalizedTranslation + (target.normalizedTranslation - normalizedTranslation) * fraction,
    )

    fun transform(
        viewport: IntSize,
        centroid: Offset,
        pan: Offset,
        factor: Float,
        videoSize: IntSize = viewport,
    ): PlayerVideoZoom {
        if (viewport.width <= 0 || viewport.height <= 0) return this

        val nextScale = (scale * factor).coerceIn(1f, MAX_VIDEO_ZOOM)
        if (nextScale == 1f) return PlayerVideoZoom()

        val focus = Offset(centroid.x / viewport.width - 0.5f, centroid.y / viewport.height - 0.5f)
        val normalizedPan = Offset(pan.x / viewport.width, pan.y / viewport.height)
        val ratio = nextScale / scale
        // Keep the same video point under the moving pinch centroid.
        val translation = focus + (normalizedTranslation - focus) * ratio + normalizedPan
        val horizontalLimit = ((nextScale * videoSize.width / viewport.width - 1f) / 2f).coerceAtLeast(0f)
        val verticalLimit = ((nextScale * videoSize.height / viewport.height - 1f) / 2f).coerceAtLeast(0f)
        return PlayerVideoZoom(nextScale, Offset(
            translation.x.coerceIn(-horizontalLimit, horizontalLimit),
            translation.y.coerceIn(-verticalLimit, verticalLimit),
        ))
    }
}

internal enum class PlayerZoomTransition { Fill, Free, Fit, Minimum }

internal class PlayerPinchZoom {
    private var pendingFactor = 1f
    private var minimumFeedbackShown = false

    fun transform(
        zoom: PlayerVideoZoom,
        viewport: IntSize,
        videoSize: IntSize,
        centroid: Offset,
        pan: Offset,
        factor: Float,
        fillSettled: Boolean = true,
    ): Pair<PlayerVideoZoom, PlayerZoomTransition?> {
        if (viewport.width <= 0 || viewport.height <= 0) return zoom to null

        val fillScale = maxOf(
            viewport.width.toFloat() / videoSize.width,
            viewport.height.toFloat() / videoSize.height,
        ).coerceIn(MIN_FILL_ZOOM, MAX_VIDEO_ZOOM)
        if (zoom.scale == 1f) {
            pendingFactor *= factor
            if (pendingFactor >= 1f + FIT_GESTURE_THRESHOLD) {
                pendingFactor = 1f
                return PlayerVideoZoom(fillScale, isFill = true) to PlayerZoomTransition.Fill
            }
            if (pendingFactor <= 1f - FIT_GESTURE_THRESHOLD) {
                pendingFactor = 1f - FIT_GESTURE_THRESHOLD
                if (!minimumFeedbackShown) {
                    minimumFeedbackShown = true
                    return zoom to PlayerZoomTransition.Minimum
                }
            }
            return zoom to null
        }

        if (zoom.isFill) {
            val freeZoomThreshold = minOf(zoom.scale + FREE_ZOOM_SCALE_GAP, MAX_VIDEO_ZOOM) / zoom.scale
            val maxPendingFactor = if (zoom.scale < MAX_VIDEO_ZOOM) 1f + (freeZoomThreshold - 1f) * 2 else 1f
            pendingFactor = (pendingFactor * factor).coerceAtMost(maxPendingFactor)
            if (pendingFactor <= 1f - LEAVE_FILL_THRESHOLD) {
                pendingFactor = 1f
                minimumFeedbackShown = true
                return PlayerVideoZoom() to PlayerZoomTransition.Fit
            }
            if (fillSettled && pendingFactor > freeZoomThreshold) {
                val extra = pendingFactor / freeZoomThreshold
                pendingFactor = 1f
                return zoom.transform(viewport, centroid, Offset.Zero, extra, videoSize) to PlayerZoomTransition.Free
            }
            // The fill stop stays centered until the user deliberately pinches beyond it.
            return zoom to null
        }

        val next = zoom.transform(viewport, centroid, pan, factor, videoSize)
        if (zoom.scale > fillScale && next.scale <= fillScale) {
            return PlayerVideoZoom(fillScale, isFill = true) to PlayerZoomTransition.Fill
        }
        if (next.scale == 1f) {
            minimumFeedbackShown = true
            return next to PlayerZoomTransition.Fit
        }
        return next to null
    }

    fun reset() {
        pendingFactor = 1f
        minimumFeedbackShown = false
    }
}
