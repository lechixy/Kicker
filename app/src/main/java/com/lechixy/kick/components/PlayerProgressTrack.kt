package com.lechixy.kick.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/** One interruptible amplitude animation; rapid toggles always retarget the current shape. */
@Composable
internal fun PlayerProgressTrack(progress: () -> Float, playing: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val amplitude = animateFloatAsState(if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 2500f), label = "player wave amplitude")
    val phase = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) while (isActive) {
            val start = phase.value % (2 * PI).toFloat()
            phase.snapTo(start)
            phase.animateTo(start + (2 * PI).toFloat(), tween(1000, easing = LinearEasing))
        }
    }
    val color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .38f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (enabled) 1f else .38f)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val path = remember { Path() }
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val gap = 3.dp.toPx()
        val center = size.height / 2f
        val fraction = progress().coerceIn(0f, 1f)
        val thumb = size.width * fraction
        val end = (thumb - gap).coerceIn(0f, size.width)
        val trackStart = (thumb + gap).coerceIn(0f, size.width)
        val waveHeight = 4.dp.toPx() * amplitude.value.coerceIn(0f, 1f)
        val wavelength = 24.dp.toPx()
        withTransform({ if (rtl) scale(-1f, 1f, pivot = Offset(size.width / 2f, center)) }) {
            if (trackStart < size.width) drawLine(trackColor, Offset(trackStart, center),
                Offset(size.width, center), strokeWidth = stroke, cap = StrokeCap.Round)
            if (end > 0f) {
                path.reset()
                var x = 0f
                fun y(at: Float) = center + waveHeight * sin(at / wavelength * (2 * PI).toFloat() - phase.value)
                path.moveTo(0f, y(0f))
                val step = 1.dp.toPx()
                while (x < end) {
                    x = (x + step).coerceAtMost(end)
                    path.lineTo(x, y(x))
                }
                drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }
}
