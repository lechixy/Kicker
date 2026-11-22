package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

internal val chatOverlayShape = RoundedCornerShape(12.dp)
internal val chatOverlayFadeIn = fadeIn(animationSpec = tween(durationMillis = 120))
internal val chatOverlayScaleSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
internal val chatOverlaySizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

internal fun chatOverlayExit(side: Int = 1) = slideOutHorizontally(
    animationSpec = tween(durationMillis = 260),
    targetOffsetX = { fullWidth -> side * (fullWidth + 40) }
) + fadeOut(animationSpec = tween(durationMillis = 200))

@Composable
internal fun ChatOverlayCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = chatOverlayShape,
        color = colors.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 4.dp,
        content = content
    )
}
