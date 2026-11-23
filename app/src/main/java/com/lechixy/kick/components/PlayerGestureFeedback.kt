package com.lechixy.kick.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.FullscreenChatVideoFrame

internal class PlayerZoomMotion(
    val from: PlayerVideoZoom,
    val durationMillis: Int,
    val snappingToFill: Boolean,
) {
    val progress = Animatable(0f)
}

internal suspend fun PointerInputScope.detectPlayerPinch(
    onTransform: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onPinchEnd: () -> Unit,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var claimed = false
    var pinching = false
    try {
        do {
            // Claim both pointers before tap, hold and host drag handlers see the event.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val hasMultiplePointers = event.changes.count { it.pressed } >= 2
            if (hasMultiplePointers) claimed = true
            if (claimed) {
                if (hasMultiplePointers && event.changes.count { it.pressed && it.previousPressed } >= 2) {
                    val factor = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (factor.isFinite() && factor > 0f && (factor != 1f || pan != Offset.Zero)) {
                        onTransform(event.calculateCentroid(useCurrent = false), pan, factor)
                    }
                }
                event.changes.forEach { it.consume() }
            }
            if (pinching && !hasMultiplePointers) onPinchEnd()
            pinching = hasMultiplePointers
        } while (event.changes.any { it.pressed })
    } finally {
        if (pinching) onPinchEnd()
    }
}

@Composable
internal fun PlayerZoomResetButton(
    label: String?,
    onReset: (() -> Unit)?,
    frame: State<FullscreenChatVideoFrame?>?,
    backdrop: FrostedPlayerRegions?,
    videoReady: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastLabel by remember { mutableStateOf(label.orEmpty()) }
    SideEffect { if (label != null) lastLabel = label }
    AnimatedVisibility(
        visible = label != null,
        modifier = modifier,
        enter = playbackButtonEnter + fadeIn(tween(200)),
        exit = playbackButtonExit + fadeOut(tween(160)),
    ) {
        val resetModifier = if (onReset == null) Modifier else {
            Modifier.clickable(
                enabled = label != null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.player_reset_zoom),
                onClick = onReset,
            )
        }
        Box(
            Modifier.clip(CircleShape).then(resetModifier),
            contentAlignment = Alignment.Center,
        ) {
            FrostedPlayerControl(frame, backdrop, videoReady, followsControlsOpacity = false)
            Text(label ?: lastLabel, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun PlayerFastForwardFeedback(
    label: String,
    frame: State<FullscreenChatVideoFrame?>?,
    backdrop: FrostedPlayerRegions,
    videoReady: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        FrostedPlayerControl(frame, backdrop, videoReady, followsControlsOpacity = false)
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val transition = rememberInfiniteTransition(label = "fast forward")
            val pulse = transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(430, easing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "fast forward pulse",
            )
            Icon(
                painterResource(R.drawable.fast_forward_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                contentDescription = null,
                modifier = Modifier.size(22.dp).graphicsLayer {
                    alpha = 0.35f + 0.65f * pulse.value
                    translationX = (3.dp.toPx()) * (pulse.value - 0.5f)
                },
                tint = Color.White,
            )
            Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}
