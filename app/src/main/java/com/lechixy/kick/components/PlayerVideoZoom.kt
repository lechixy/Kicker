package com.lechixy.kick.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.pow

internal data class PlayerVideoZoom(
    val scale: Float = 1f,
    val normalizedTranslation: Offset = Offset.Zero,
) {
    fun transform(viewport: IntSize, centroid: Offset, pan: Offset, factor: Float): PlayerVideoZoom {
        if (viewport.width <= 0 || viewport.height <= 0) return this

        val adjustedFactor = if (factor < 1f) factor.pow(2.5f) else factor
        val nextScale = (scale * adjustedFactor).coerceIn(1f, 4f)
        if (nextScale == 1f || (factor < 1f && nextScale < 1.06f)) return PlayerVideoZoom()

        val focus = Offset(centroid.x / viewport.width - 0.5f, centroid.y / viewport.height - 0.5f)
        val normalizedPan = Offset(pan.x / viewport.width, pan.y / viewport.height)
        val ratio = nextScale / scale
        // Keep the same video point under the moving pinch centroid.
        val translation = focus + (normalizedTranslation - focus) * ratio + normalizedPan
        val limit = (nextScale - 1f) / 2f
        return PlayerVideoZoom(nextScale, Offset(
            translation.x.coerceIn(-limit, limit),
            translation.y.coerceIn(-limit, limit),
        ))
    }
}
