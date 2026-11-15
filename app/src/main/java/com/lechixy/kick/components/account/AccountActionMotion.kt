package com.lechixy.kick.components.account

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
internal fun Modifier.accountActionMotion(selected: Boolean, interactions: MutableInteractionSource): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(120), label = "action press")
    val selectionScale = remember { Animatable(1f) }
    var previousSelection by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (previousSelection != selected) {
            previousSelection = selected
            selectionScale.animateTo(1f, keyframes {
                durationMillis = 260
                (if (selected) 1.08f else 0.94f) at 100
            })
        }
    }
    return graphicsLayer {
        scaleX = pressScale * selectionScale.value
        scaleY = scaleX
    }
}
