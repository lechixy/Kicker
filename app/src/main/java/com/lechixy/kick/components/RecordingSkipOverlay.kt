package com.lechixy.kick.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import kotlin.math.sqrt

internal class RecordingSkipFeedback(val tap: Offset, val forward: Boolean)

@Composable
internal fun RecordingSkipOverlay(feedback: RecordingSkipFeedback, modifier: Modifier, onFinished: () -> Unit) {
    val progress = remember(feedback) { Animatable(0f) }
    LaunchedEffect(feedback) {
        progress.animateTo(1f, tween(durationMillis = 600, easing = LinearEasing))
        onFinished()
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val side = if (feedback.forward != rtl) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier.clipToBounds()) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.width / 2f
            val time = progress.value
            val expansion = sqrt((time * 1.5f).coerceAtMost(1f))
            clipRect(left = if (feedback.forward) half else 0f, right = if (feedback.forward) size.width else half) {
                drawCircle(Color.White, radius = size.maxDimension * expansion,
                    center = feedback.tap, alpha = 0.22f * (1f - time))
            }
        }
        Box(Modifier.align(side).fillMaxWidth(.5f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.graphicsLayer {
                    val time = progress.value
                    alpha = minOf(time * 12f, (1f - time) * 4f, 1f)
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { index ->
                        val triangle = remember { Path() }
                        Canvas(Modifier.size(12.dp)) {
                            val tip = if (feedback.forward) size.width else 0f
                            val base = size.width - tip
                            triangle.reset()
                            triangle.moveTo(base, 0f)
                            triangle.lineTo(tip, size.height / 2f)
                            triangle.lineTo(base, size.height)
                            triangle.close()
                            val physicalIndex = if (rtl) 2 - index else index
                            val sequence = if (feedback.forward) physicalIndex else 2 - physicalIndex
                            val phase = (progress.value * 3f - sequence * .25f + 1f) % 1f
                            drawPath(triangle, Color.White, alpha = .35f + .65f * phase)
                        }
                    }
                }
                Text(stringResource(R.string.player_skip_ten_seconds), color = Color.White,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
