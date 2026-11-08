package com.lechixy.kick.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.AnimatedCounterText
import com.lechixy.kick.service.player.SleepTimerState
import com.lechixy.kick.util.player.sleepTimerRemainingText
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlayerSleepTimerSettings(
    timer: SleepTimerState,
    lastMinutes: Int,
    enabled: Boolean,
    onApply: (Int) -> Unit,
) {
    val slider = remember {
        val initialMinutes = timer.selectedMinutes.takeIf { it > 0 } ?: lastMinutes
        SliderState(value = initialMinutes.coerceIn(5, 60).toFloat(), steps = 10, trackRange = 5f..60f)
    }
    val minutes = slider.value.roundToInt()
    val duration = stringResource(R.string.player_minutes, minutes)
    val running = timer.remainingSeconds > 0
    val canUpdate = running && minutes != timer.selectedMinutes

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painterResource(R.drawable.bedtime_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(stringResource(R.string.player_sleep_timer_running), style = MaterialTheme.typography.titleSmall)
                    Text(sleepTimerRemainingText(LocalResources.current, timer.remainingSeconds),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            Modifier.clearAndSetSemantics { contentDescription = duration },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedCounterText(
                count = minutes,
                style = MaterialTheme.typography.headlineMedium,
                formatViewers = false,
                animateEntrance = false,
            )
            Text(stringResource(R.string.player_sleep_timer_minutes_unit), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            state = slider,
            onValueChange = { slider.value = it },
            enabled = enabled,
            modifier = Modifier.semantics { stateDescription = duration },
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (running) {
                OutlinedButton(onClick = { onApply(0) }, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.timer_off_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(18.dp),
                    )
                    Text(stringResource(R.string.player_sleep_timer_stop))
                }
            }
            AnimatedVisibility(visible = !running || canUpdate) {
                Button(onClick = { onApply(minutes) }, enabled = enabled) {
                    Text(stringResource(if (running) R.string.player_sleep_timer_update else R.string.player_sleep_timer_start))
                }
            }
        }
    }
}

@Composable
internal fun PlayerSleepTimerRemainingLabel(seconds: Int) {
    val label = sleepTimerRemainingText(LocalResources.current, seconds)
    val numbers = remember(label) { Regex("\\p{Nd}+").findAll(label).toList() }
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
    val numberStyle = style.copy(fontSize = (style.fontSize.value + 2).sp)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var start = 0
        for (number in numbers) {
            if (start < number.range.first) {
                Text(label.substring(start, number.range.first), color = Color.White, style = style)
            }
            AnimatedCounterText(
                count = number.value.toInt(),
                style = numberStyle,
                color = MaterialTheme.colorScheme.primary,
                formatViewers = false,
                animateEntrance = false,
            )
            start = number.range.last + 1
        }
        if (start < label.length) {
            Text(label.substring(start), color = Color.White, style = style)
        }
    }
}

@Composable
internal fun ActiveSleepTimerIcon(
    modifier: Modifier = Modifier.size(18.dp),
    contentDescription: String? = stringResource(R.string.player_sleep_timer),
) {
    val transition = rememberInfiniteTransition(label = "sleep timer")
    val motion by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(780, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sleep timer pulse",
    )
    Icon(
        painterResource(R.drawable.bedtime_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            rotationZ = -7f + motion * 14f
            scaleX = .9f + motion * .12f
            scaleY = scaleX
        },
        tint = MaterialTheme.colorScheme.primary,
    )
}
