package com.lechixy.kick.data.settings.model

internal data class FullscreenChatPosition(val x: Float, val y: Float) {
    fun encode(): String = "$x,$y"

    fun snapToEdges(travelX: Float, travelY: Float, threshold: Float): FullscreenChatPosition =
        FullscreenChatPosition(snapAxis(x, travelX, threshold), snapAxis(y, travelY, threshold))

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
            if (parts.size != 2) return null
            val x = parts[0].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
            val y = parts[1].toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null
            return FullscreenChatPosition(x, y)
        }
    }
}
