package com.lechixy.kick.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** A one-shot drawing animation: loading content never animates the dialog window's size. */
@Composable
internal fun Modifier.dialogEntrance(): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
    return graphicsLayer {
        alpha = progress.value
        scaleX = .96f + .04f * progress.value
        scaleY = scaleX
    }
}
