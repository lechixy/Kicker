package com.lechixy.kick.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R

internal class RecordingSkipFeedback(val forward: Boolean)

@Composable
internal fun RecordingSkipOverlay(feedback: RecordingSkipFeedback, modifier: Modifier, onFinished: () -> Unit) {
    val progress = remember(feedback) { Animatable(0f) }
    LaunchedEffect(feedback) {
        progress.animateTo(1f, tween(durationMillis = 620, easing = LinearEasing))
        onFinished()
    }
    val side = if (feedback.forward) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft
    val direction = if (feedback.forward) 1f else -1f
    Box(modifier.clipToBounds()) {
        Column(
            Modifier.align(side).fillMaxWidth(.5f).graphicsLayer {
                val time = progress.value
                alpha = minOf(time * 7f, (1f - time) * 4f, 1f)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                painterResource(R.drawable.keyboard_double_arrow_right_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                modifier = Modifier.size(34.dp).graphicsLayer {
                    scaleX = direction
                    translationX = direction * 32.dp.toPx() *
                        (FastOutSlowInEasing.transform(progress.value) - .5f)
                },
                tint = Color.White,
            )
            Text(
                stringResource(R.string.player_skip_ten_seconds),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(shadow = Shadow(Color.Black, blurRadius = 4f)),
            )
        }
    }
}
