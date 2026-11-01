package com.lechixy.kick.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** Only the border redraws each frame; the settings content is not recomposed by the shimmer. */
@Composable
internal fun Modifier.animatedCreatorOutline(): Modifier {
    val colors = MaterialTheme.colorScheme
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val rotation: State<Float> = if (animate) rememberInfiniteTransition(label = "creator_outline")
        .animateFloat(0f, 360f, infiniteRepeatable(tween(8_000, easing = LinearEasing)), label = "outline_angle")
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
        val extent = size.maxDimension
        onDrawWithContent {
            drawContent()
            clipPath(ring) {
                rotate(rotation.value, center) {
                    drawRect(brush, center - Offset(extent, extent), Size(extent * 2, extent * 2))
                }
            }
        }
    }
}
