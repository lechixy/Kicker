package com.lechixy.kick.components.channel

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
internal class OfflineBannerState(private val scope: CoroutineScope) : State<Float> {
    private var progress by mutableFloatStateOf(0f)
    override val value get() = progress
    var travelPx: Float = 1f
    private var animation: Job? = null

    /** Positive pixels collapse; return precisely the distance consumed by the header. */
    fun drag(pixels: Float): Float {
        animation?.cancel()
        val previous = progress
        progress = (progress + pixels / travelPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return (progress - previous) * travelPx.coerceAtLeast(1f)
    }

    fun settle() {
        when {
            progress < 0.06f -> animateTo(0f)
            progress > 0.94f -> animateTo(1f)
        }
    }

    fun animateTo(target: Float) {
        animation?.cancel()
        animation = scope.launch {
            animate(progress, target, animationSpec = tween(440)) { value, _ -> progress = value }
        }
    }
}
