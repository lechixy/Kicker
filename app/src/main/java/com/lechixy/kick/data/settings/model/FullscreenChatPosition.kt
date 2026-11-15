package com.lechixy.kick.data.settings.model

internal data class FullscreenChatPosition(
    val x: Float,
    val y: Float,
    val widthFraction: Float? = null,
    val heightFraction: Float? = null,
) {
    fun encode(): String = if (widthFraction == null && heightFraction == null) "$x,$y"
        else "$x,$y,${widthFraction ?: ""},${heightFraction ?: ""}"

    fun snapToEdges(travelX: Float, travelY: Float, threshold: Float): FullscreenChatPosition =
        copy(x = snapAxis(x, travelX, threshold), y = snapAxis(y, travelY, threshold))

    fun resize(
        viewport: Float,
        size: Float,
        minimum: Float,
        delta: Float,
        fromStart: Boolean,
        horizontalAxis: Boolean,
    ): FullscreenChatPosition {
        if (viewport <= 0f || delta == 0f) return this
        val position = if (horizontalAxis) x else y
        val start = (viewport - size) * position
        val end = start + size
        val minSize = minOf(size, minimum)
        val newStart = if (fromStart) (start + delta).coerceIn(0f, (end - minSize).coerceAtLeast(0f)) else start
        val newEnd = if (fromStart) end else (end + delta).coerceIn((start + minSize).coerceAtMost(viewport), viewport)
        val newSize = newEnd - newStart
        val travel = viewport - newSize
        val newPosition = if (travel > 0f) (newStart / travel).coerceIn(0f, 1f) else position
        val fraction = (newSize / viewport).coerceIn(0f, 1f)
        return if (horizontalAxis) copy(x = newPosition, widthFraction = fraction)
        else copy(y = newPosition, heightFraction = fraction)
    }

    companion object {
        val VerticalDefault = FullscreenChatPosition(0f, 1f)
        val HorizontalDefault = FullscreenChatPosition(0f, 0f)

        private fun snapAxis(position: Float, travel: Float, threshold: Float): Float {
            if (travel <= 0f) return position
            val nearestEdge = if (position <= 0.5f) 0f else 1f
            return if (kotlin.math.abs(position - nearestEdge) * travel <= threshold) nearestEdge else position
        }

        fun decode(value: String): FullscreenChatPosition? {
            val parts = value.split(',')
            if (parts.size != 2 && parts.size != 4) return null
            val x = parts[0].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
            val y = parts[1].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
            if (parts.size == 2) return FullscreenChatPosition(x, y)
            val width = parts[2].toFloatOrNull()?.takeIf { it > 0f && it <= 1f }
            val height = parts[3].toFloatOrNull()?.takeIf { it > 0f && it <= 1f }
            if ((parts[2].isNotEmpty() && width == null) || (parts[3].isNotEmpty() && height == null)) return null
            return FullscreenChatPosition(x, y, width, height)
        }
    }
}
