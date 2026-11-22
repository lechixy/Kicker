package com.lechixy.kick.components

import com.lechixy.kick.data.settings.repository.LocalBlurEffectsEnabled

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val RECORDING_SKIP_SEQUENCE_TIMEOUT_MS = 420L

internal class RecordingSkipFeedback(
    val forward: Boolean,
    val seconds: Int = 10,
    val atMillis: Long = SystemClock.uptimeMillis(),
)

internal suspend fun PointerInputScope.detectRecordingTaps(
    onTap: () -> Unit,
    onHoldStart: (() -> Unit)? = null,
    onHoldEnd: () -> Unit = {},
    onSkip: (forward: Boolean, continued: Boolean) -> Unit,
) = coroutineScope {
    var singleTap: Job? = null
    var lastForward = false
    var lastUpMillis = 0L
    var skipping = false
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        val canHold = onHoldStart != null && (down.position.x < size.width / 3f || down.position.x > size.width * 2f / 3f)
        val up = if (canHold) {
            var timedOut = true
            val release = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                awaitRecordingPressRelease(down).also { timedOut = false }
            }
            if (timedOut) {
                singleTap?.cancel()
                singleTap = null
                skipping = false
                try {
                    onHoldStart()
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                } finally {
                    onHoldEnd()
                }
            }
            release
        } else {
            waitForUpOrCancellation()
        } ?: return@awaitEachGesture
        up.consume()

        val forward = up.position.x >= size.width / 2f
        val elapsed = down.uptimeMillis - lastUpMillis
        val startsSkipping = singleTap?.isActive == true && elapsed <= viewConfiguration.doubleTapTimeoutMillis
        val continuesSkipping = skipping && elapsed <= RECORDING_SKIP_SEQUENCE_TIMEOUT_MS
        if (forward == lastForward && (startsSkipping || continuesSkipping)) {
            singleTap?.cancel()
            singleTap = null
            skipping = true
            onSkip(forward, continuesSkipping)
        } else {
            if (singleTap?.isActive == true) {
                singleTap?.cancel()
                onTap()
            }
            skipping = false
            val timeout = viewConfiguration.doubleTapTimeoutMillis
            singleTap = launch {
                delay(timeout)
                onTap()
            }
        }
        lastForward = forward
        lastUpMillis = up.uptimeMillis
    }
}

private suspend fun AwaitPointerEventScope.awaitRecordingPressRelease(down: PointerInputChange): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent()
        val pointer = event.changes.firstOrNull { it.id == down.id } ?: return null
        if (event.changes.any { it.isConsumed || (it.id != down.id && it.pressed) } ||
            pointer.isOutOfBounds(size, extendedTouchPadding) ||
            (pointer.position - down.position).getDistance() > viewConfiguration.touchSlop
        ) return null
        if (!pointer.pressed) return pointer
        if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) return null
    }
}

@Composable
internal fun RecordingSkipOverlay(feedback: RecordingSkipFeedback, modifier: Modifier, onFinished: () -> Unit) {
    val progress = remember(feedback) { Animatable(if (feedback.seconds > 10) 0.15f else 0f) }
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
                (if (feedback.forward) "+" else "−") + stringResource(R.string.player_skip_seconds, feedback.seconds),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(
                    shadow = if (LocalBlurEffectsEnabled.current) Shadow(Color.Black, blurRadius = 4f) else null,
                ),
            )
        }
    }
}
