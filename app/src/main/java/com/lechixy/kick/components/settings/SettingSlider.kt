package com.lechixy.kick.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun SettingSlider(key: SettingKey<Int>, title: String, valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier, steps: Int = 0, description: String? = null,
    valueFormatter: (Int) -> String = { it.toString() }) {
    NumberSettingSlider(key, title, { it.toFloat() }, { it.roundToInt() }, valueRange,
        modifier, steps, description, valueFormatter)
}

@Composable
fun SettingFloatSlider(key: SettingKey<Float>, title: String, valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier, steps: Int = 0, description: String? = null,
    valueFormatter: (Float) -> String = { it.toString() }) {
    NumberSettingSlider(key, title, { it }, { it }, valueRange, modifier, steps, description, valueFormatter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> NumberSettingSlider(
    key: SettingKey<T>,
    title: String,
    toFloat: (T) -> Float,
    fromFloat: (Float) -> T,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    description: String? = null,
    valueFormatter: (T) -> String = { it.toString() }
) {
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val persistentValue by repository.get(key).collectAsStateWithLifecycle(initialValue = key.defaultValue)

    var sliderPosition by remember {
        mutableFloatStateOf(toFloat(persistentValue).coerceIn(valueRange.start, valueRange.endInclusive))
    }

    LaunchedEffect(persistentValue, valueRange.start, valueRange.endInclusive) {
        sliderPosition = toFloat(persistentValue).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = valueFormatter(fromFloat(sliderPosition)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val coercedPosition = sliderPosition.coerceIn(valueRange.start, valueRange.endInclusive)
            val sliderState = remember(steps, valueRange) {
                SliderState(
                    value = coercedPosition,
                    steps = steps,
                    trackRange = valueRange
                )
            }
            sliderState.value = coercedPosition
            Slider(
                state = sliderState,
                onValueChange = { sliderPosition = it.coerceIn(valueRange.start, valueRange.endInclusive) },
                onValueChangeFinished = {
                    val finalValue = fromFloat(sliderPosition)
                    if (finalValue != persistentValue) {
                        coroutineScope.launch {
                            repository.set(key, finalValue)
                        }
                    }
                }
            )
        }
    }
}