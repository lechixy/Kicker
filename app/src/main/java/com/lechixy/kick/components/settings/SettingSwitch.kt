package com.lechixy.kick.components.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.resolvedDefault
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun SettingSwitch(
    key: SettingKey<Boolean>,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
    badge: String? = null,
) {
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val default = key.resolvedDefault
    val value by remember(repository, key) { repository.get(key) }.collectAsStateWithLifecycle(default)

    SettingSwitch(
        title = title,
        checked = value,
        onCheckedChange = { newValue ->
            coroutineScope.launch {
                repository.set(key, newValue)
            }
        },
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        badge = badge,
        onReset = if (value != default) ({ coroutineScope.launch { repository.reset(key) } }) else null
    )
}

@Composable
fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
    onReset: (() -> Unit)? = null,
    badge: String? = null,
) {
    SettingCard(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        onReset = onReset,
        badge = badge,
        rowModifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        action = {
            DraggableSwitchTrack(checked, enabled, onCheckedChange)
        }
    )
}

@Composable
private fun DraggableSwitchTrack(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val latestChecked by rememberUpdatedState(checked)
    val latestChange by rememberUpdatedState(onCheckedChange)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var dragPosition by remember(enabled, rtl) { mutableStateOf<Float?>(null) }
    val position by animateFloatAsState(
        targetValue = dragPosition ?: if (checked) 1f else 0f,
        animationSpec = if (dragPosition == null) spring() else snap(),
        label = "Switch thumb position",
    )
    val colors = SwitchDefaults.colors()
    val trackColor = when {
        enabled && checked -> colors.checkedTrackColor
        enabled -> colors.uncheckedTrackColor
        checked -> colors.disabledCheckedTrackColor
        else -> colors.disabledUncheckedTrackColor
    }
    val borderColor = when {
        enabled && checked -> colors.checkedBorderColor
        enabled -> colors.uncheckedBorderColor
        checked -> colors.disabledCheckedBorderColor
        else -> colors.disabledUncheckedBorderColor
    }
    val thumbColor = when {
        enabled && checked -> colors.checkedThumbColor
        enabled -> colors.uncheckedThumbColor
        checked -> colors.disabledCheckedThumbColor
        else -> colors.disabledUncheckedThumbColor
    }
    val dragModifier = if (enabled) {
        Modifier.pointerInput(rtl) {
            val travel = 20.dp.toPx()
            val direction = if (rtl) -1 else 1
            detectHorizontalDragGestures(
                onDragStart = { dragPosition = if (latestChecked) 1f else 0f },
                onHorizontalDrag = { _, delta ->
                    dragPosition = ((dragPosition ?: 0f) + direction * delta / travel).coerceIn(0f, 1f)
                },
                onDragEnd = {
                    val newValue = (dragPosition ?: 0f) >= 0.5f
                    dragPosition = null
                    if (latestChecked != newValue) latestChange(newValue)
                },
                onDragCancel = { dragPosition = null },
            )
        }
    } else Modifier

    Canvas(Modifier.size(width = 52.dp, height = 48.dp).then(dragModifier)) {
        val trackHeight = 32.dp.toPx()
        val trackTop = (size.height - trackHeight) / 2
        val border = 2.dp.toPx()
        val trackSize = Size(size.width, trackHeight)
        val trackRadius = trackHeight / 2
        drawRoundRect(trackColor, Offset(0f, trackTop), trackSize, CornerRadius(trackRadius))
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(border / 2, trackTop + border / 2),
            size = Size(size.width - border, trackHeight - border),
            cornerRadius = CornerRadius(trackRadius - border / 2),
            style = Stroke(border),
        )
        val progress = position.coerceIn(0f, 1f)
        val trackProgress = if (rtl) 1f - progress else progress
        val thumbX = trackRadius + (size.width - trackHeight) * trackProgress
        drawCircle(thumbColor, radius = (8.dp + 4.dp * progress).toPx(), center = Offset(thumbX, center.y))
    }
}
