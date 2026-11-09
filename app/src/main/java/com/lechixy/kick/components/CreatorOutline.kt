package com.lechixy.kick.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Read the animation in drawing so the card keeps a stable layout and click target. */
@Composable
internal fun Modifier.animatedCreatorCard(): Modifier {
    val colors = MaterialTheme.colorScheme
    val lifecycle = LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animate = lifecycle.value.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    val rotation: State<Float> = if (animate) rememberInfiniteTransition(label = "creator_outline")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(3_500, easing = LinearEasing)), label = "outline_angle")
        else remember { mutableFloatStateOf(0f) }
    return drawWithCache {
        val stroke = 2.dp.toPx()
        val radius = 20.dp.toPx()
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
            if (size.minDimension > stroke * 2) addRoundRect(RoundRect(
                Rect(stroke, stroke, size.width - stroke, size.height - stroke), CornerRadius((radius - stroke).coerceAtLeast(0f))))
        }
        val center = Offset(size.width / 2f, size.height / 2f)
        val brush = Brush.sweepGradient(listOf(colors.primary.copy(alpha = .25f), colors.primary,
            colors.tertiary, colors.secondary.copy(alpha = .35f), colors.primary.copy(alpha = .25f)), center)
        val band = size.height * .75f
        val shine = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = .24f), Color.Transparent),
            start = Offset(0f, size.height), end = Offset(band, 0f),
        )
        val extent = size.maxDimension
        onDrawWithContent {
            val angle = rotation.value
            val sweep = (angle / 180f).coerceAtMost(1f)
            if (animate && sweep < 1f) {
                val shift = (size.width + 3 * band) * sweep - 2 * band
                translate(left = shift) {
                    drawRect(shine, topLeft = Offset(-shift, 0f), size = size)
                }
            }
            drawContent()
            clipPath(ring) {
                rotate(angle, center) {
                    drawRect(brush, center - Offset(extent, extent), Size(extent * 2, extent * 2))
                }
            }
        }
    }
}
