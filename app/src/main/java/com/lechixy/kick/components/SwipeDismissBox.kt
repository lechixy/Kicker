package com.lechixy.kick.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class SwipeDismissDirection { Left, Right, Up }

/** Owns only the card gesture; taps remain with its content. Vertical chat scrolling stays available. */
@Composable
internal fun SwipeDismissBox(
    allowUp: Boolean,
    onDismiss: (SwipeDismissDirection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val latestDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maximumThreshold = with(density) { 88.dp.toPx() }
    val upThreshold = with(density) { 40.dp.toPx() }
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var displacement by remember { mutableStateOf(Offset.Zero) }
    var dismissing by remember { mutableStateOf(false) }
    var horizontal by remember { mutableStateOf<Boolean?>(null) }
    var motion by remember { mutableStateOf<Job?>(null) }

    fun settle(cancelled: Boolean, forced: SwipeDismissDirection? = null) {
        if (dismissing) return
        val direction = forced ?: if (cancelled) null else when {
            abs(displacement.x) >= minOf(dimensions.width * .25f, maximumThreshold) && displacement.x != 0f ->
                if (displacement.x < 0) SwipeDismissDirection.Left else SwipeDismissDirection.Right
            allowUp && displacement.y <= -minOf(dimensions.height * .5f, upThreshold) && displacement.y < 0 -> SwipeDismissDirection.Up
            else -> null
        }
        val target = when (direction) {
            SwipeDismissDirection.Left -> Offset(-dimensions.width * 1.25f, 0f)
            SwipeDismissDirection.Right -> Offset(dimensions.width * 1.25f, 0f)
            SwipeDismissDirection.Up -> Offset(0f, -dimensions.height * 2f)
            null -> Offset.Zero
        }
        motion?.cancel()
        dismissing = direction != null
        motion = scope.launch {
            val start = displacement
            animate(0f, 1f, animationSpec = tween(220, easing = FastOutSlowInEasing)) { fraction, _ ->
                displacement = start + (target - start) * fraction
            }
            if (direction != null) latestDismiss(direction)
        }
    }
    fun start() {
        if (!dismissing) { motion?.cancel(); horizontal = null }
    }
    Box(modifier.onSizeChanged { dimensions = it }
        .graphicsLayer {
            translationX = displacement.x; translationY = displacement.y
            val distance = max(abs(displacement.x) / dimensions.width.coerceAtLeast(1), abs(displacement.y) / dimensions.height.coerceAtLeast(1))
            alpha = (1f - distance / 1.25f).coerceIn(0f, 1f)
        }
        .semantics { dismiss { settle(false, SwipeDismissDirection.Right); true } }
        .pointerInput(allowUp) {
            if (allowUp) detectDragGestures(
                onDragStart = { start() }, onDragEnd = { settle(false) }, onDragCancel = { settle(true) },
                onDrag = { change, delta ->
                    if (!dismissing) {
                        if (horizontal == null) horizontal = abs(delta.x) >= abs(delta.y)
                        displacement = if (horizontal == true) Offset(displacement.x + delta.x, 0f)
                            else Offset(0f, (displacement.y + delta.y).coerceAtMost(0f))
                        change.consume()
                    }
                })
            else detectHorizontalDragGestures(
                onDragStart = { start() }, onDragEnd = { settle(false) }, onDragCancel = { settle(true) },
                onHorizontalDrag = { change, delta ->
                    if (!dismissing) { displacement = Offset(displacement.x + delta, 0f); change.consume() }
                })
        }) { content() }
}
