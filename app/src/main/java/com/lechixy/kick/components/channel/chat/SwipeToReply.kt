package com.lechixy.kick.components.channel.chat

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import com.lechixy.kick.R

/** Claims rightward drags only, leaving leftward page swipes and vertical scrolling to parents. */
@Composable
fun SwipeToReply(enabled: Boolean, onReply: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var displacement by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var reset by remember { mutableStateOf<Job?>(null) }
    val haptic = LocalHapticFeedback.current
    val replyLabel = stringResource(R.string.chat_reply_action)
    val latestReply by rememberUpdatedState(onReply)
    Box(modifier.fillMaxWidth().semantics {
        if (enabled) customActions = listOf(CustomAccessibilityAction(replyLabel) { latestReply(); true })
    }.pointerInput(enabled) {
        if (!enabled) return@pointerInput
        val threshold = 64.dp.toPx()
        val maximum = 88.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var claimed = false
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop > 0f) {
                    reset?.cancel()
                    claimed = true
                    displacement = overSlop.coerceIn(0f, maximum)
                    change.consume()
                }
            }
            if (drag != null && claimed) {
                val completed = horizontalDrag(drag.id) { change ->
                    displacement = (displacement + (change.position.x - change.previousPosition.x)).coerceIn(0f, maximum)
                    change.consume()
                }
                if (completed && displacement >= threshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    latestReply()
                }
                reset = scope.launch { animate(displacement, 0f, animationSpec = tween(190)) { value, _ -> displacement = value } }
            }
        }
    }) {
        Icon(painterResource(R.drawable.reply_24dp), null,
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(22.dp).graphicsLayer {
                alpha = (displacement / 100f).coerceIn(0f, 1f)
                scaleX = .6f + alpha * .4f; scaleY = scaleX
            }, tint = MaterialTheme.colorScheme.primary)
        Box(Modifier.fillMaxWidth().graphicsLayer { translationX = displacement }) { content() }
    }
}
