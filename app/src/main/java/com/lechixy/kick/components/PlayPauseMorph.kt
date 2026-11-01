package com.lechixy.kick.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter

/** Two matching quadrilaterals form either a triangle or two pause bars. */
@Composable
internal fun rememberPlayPausePainter(isPlaying: Boolean): Painter {
    val progress = animateFloatAsState(if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 2500f), label = "play pause morph")
    return remember(progress) {
        object : Painter() {
            override val intrinsicSize = Size(24f, 24f)
            private val path = Path()
            private val play = arrayOf(
                floatArrayOf(8f, 5f, 13.5f, 8.5f, 13.5f, 15.5f, 8f, 19f),
                floatArrayOf(13.5f, 8.5f, 19f, 12f, 19f, 12f, 13.5f, 15.5f))
            private val pause = arrayOf(
                floatArrayOf(6f, 5f, 10f, 5f, 10f, 19f, 6f, 19f),
                floatArrayOf(14f, 5f, 18f, 5f, 18f, 19f, 14f, 19f))
            override fun DrawScope.onDraw() {
                val fraction = progress.value.coerceIn(0f, 1f)
                scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                    for (part in play.indices) {
                        path.reset()
                        for (point in 0..3) {
                            val i = point * 2
                            val x = play[part][i] + (pause[part][i] - play[part][i]) * fraction
                            val y = play[part][i + 1] + (pause[part][i + 1] - play[part][i + 1]) * fraction
                            if (point == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        path.close()
                        drawPath(path, Color.Black)
                    }
                }
            }
        }
    }
}
