package com.lechixy.kick.screens.clip

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class TrimDrag { Start, End, Window }

@Composable
internal fun ClipTrimTimeline(
    trim: ClipTrim,
    sourceDuration: Int,
    rangeLabel: String,
    enabled: Boolean,
    onTrimChange: (ClipTrim) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrim by rememberUpdatedState(trim)
    val currentOnTrimChange by rememberUpdatedState(onTrimChange)
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.clip_trim_timeline)
    val actions = if (enabled) {
        listOf(
            stringResource(R.string.clip_trim_move_earlier) to trim.moveBy(-1, sourceDuration),
            stringResource(R.string.clip_trim_move_later) to trim.moveBy(1, sourceDuration),
            stringResource(R.string.clip_trim_start_earlier) to trim.resizeStart(-1, sourceDuration),
            stringResource(R.string.clip_trim_start_later) to trim.resizeStart(1, sourceDuration),
            stringResource(R.string.clip_trim_end_earlier) to trim.resizeEnd(-1, sourceDuration),
            stringResource(R.string.clip_trim_end_later) to trim.resizeEnd(1, sourceDuration),
        ).filter { (_, next) -> next != trim }.map { (description, next) ->
            CustomAccessibilityAction(description) {
                onTrimChange(next)
                true
            }
        }
    } else emptyList()

    Canvas(
        modifier.fillMaxWidth().height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHigh)
            .semantics {
                contentDescription = label
                stateDescription = rangeLabel
                customActions = actions
            }
            .pointerInput(sourceDuration, enabled) {
                if (!enabled) return@pointerInput
                val inset = 6.dp.toPx()
                val hitRadius = 16.dp.toPx()
                var initial = currentTrim
                var drag: TrimDrag? = null
                var downX = 0f
                detectDragGestures(
                    orientationLock = Orientation.Horizontal,
                    onDragStart = { down, _, _ ->
                        initial = currentTrim
                        downX = down.position.x
                        val trackWidth = (size.width - inset * 2).coerceAtLeast(1f)
                        val start = inset + initial.start.toFloat() / sourceDuration * trackWidth
                        val end = inset + initial.end.toFloat() / sourceDuration * trackWidth
                        val fromStart = abs(downX - start)
                        val fromEnd = abs(downX - end)
                        val handleRadius = minOf(hitRadius, (end - start) / 3f)
                        drag = when {
                            downX <= start && fromStart <= hitRadius -> TrimDrag.Start
                            downX >= end && fromEnd <= hitRadius -> TrimDrag.End
                            fromStart <= handleRadius -> TrimDrag.Start
                            fromEnd <= handleRadius -> TrimDrag.End
                            downX in start..end -> TrimDrag.Window
                            else -> null
                        }
                    },
                    onDrag = { change, _ ->
                        val dx = change.position.x - downX
                        val trackWidth = (size.width - inset * 2).coerceAtLeast(1f)
                        val delta = (dx * sourceDuration / trackWidth).roundToInt()
                        val next = when (drag) {
                            TrimDrag.Start -> initial.resizeStart(delta, sourceDuration)
                            TrimDrag.End -> initial.resizeEnd(delta, sourceDuration)
                            TrimDrag.Window -> initial.moveBy(delta, sourceDuration)
                            null -> return@detectDragGestures
                        }
                        if (next != currentTrim) currentOnTrimChange(next)
                    },
                )
            },
    ) {
        val inset = 6.dp.toPx()
        val trackWidth = (size.width - inset * 2).coerceAtLeast(1f)
        val start = inset + trim.start.toFloat() / sourceDuration * trackWidth
        val end = inset + trim.end.toFloat() / sourceDuration * trackWidth
        val centerY = size.height / 2
        val barCount = (trackWidth / 5.dp.toPx()).toInt().coerceAtLeast(1)
        repeat(barCount) { index ->
            val x = inset + (index + .5f) / barCount * trackWidth
            val halfHeight = (4 + (index * 37 % 17)).dp.toPx()
            drawLine(
                color = if (x in start..end) colors.primary else colors.onSurfaceVariant.copy(alpha = .4f),
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawRoundRect(
            color = colors.onSurface,
            topLeft = Offset(start, 3.dp.toPx()),
            size = Size(end - start, size.height - 6.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
        for (x in listOf(start, end)) {
            drawLine(
                colors.onSurface, Offset(x, centerY - 9.dp.toPx()), Offset(x, centerY + 9.dp.toPx()),
                strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}
