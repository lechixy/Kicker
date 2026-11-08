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
    var travelPx: Float = 0f
    private var animation: Job? = null
    private var target = 0f

    /** Positive pixels collapse; return precisely the distance consumed by the header. */
    fun drag(pixels: Float): Float {
        if (travelPx <= 0f) return 0f
        animation?.cancel()
        animation = null
        val previous = progress
        progress = (progress + pixels / travelPx).coerceIn(0f, 1f)
        return (progress - previous) * travelPx
    }

    fun settle() {
        if (animation?.isActive == true) return
        val threshold = if (target == 0f) 0.35f else 0.65f
        animateTo(if (progress >= threshold) 1f else 0f)
    }

    fun toggle() {
        animateTo(1f - target)
    }

    fun animateTo(target: Float) {
        animation?.cancel()
        this.target = target
        if (progress == target) return
        animation = scope.launch {
            animate(progress, target, animationSpec = tween(440)) { value, _ -> progress = value }
        }
    }
}
