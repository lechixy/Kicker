package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.resolvedDefault
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun <T> SettingDropdown(
    key: SettingKey<T>,
    title: String,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true,
    onValueChanged: ((T) -> Unit)? = null
) {
    val expanded = remember(enabled) { mutableStateOf(false) }
    val repository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()
    val default = key.resolvedDefault
    val currentValue by remember(repository, key) { repository.get(key) }.collectAsStateWithLifecycle(default)
    SettingCard(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = { expanded.value = true },
        onReset = if (currentValue != default) ({
            expanded.value = false
            scope.launch {
                repository.reset(key)
                onValueChanged?.invoke(default)
            }
        }) else null,
        action = {
            SettingDropdownAction(currentValue, options, enabled, expanded) { selected ->
                scope.launch { repository.set(key, selected) }
                onValueChanged?.invoke(selected)
            }
        }
    )
}

@Composable
internal fun <T> SettingDropdownAction(
    currentValue: T,
    options: List<Pair<T, String>>,
    enabled: Boolean = true,
    expandedState: MutableState<Boolean> = remember(enabled) { mutableStateOf(false) },
    onValueChanged: (T) -> Unit,
) {
    var expanded by expandedState
    val currentLabel = options.firstOrNull { it.first == currentValue }?.second ?: currentValue.toString()

    Box {
        FilledTonalButton(onClick = { expanded = true }, enabled = enabled) {
            Text(currentLabel, style = MaterialTheme.typography.bodyMedium)
            Icon(
                painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
            )
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { (optionValue, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onValueChanged(optionValue)
                    },
                )
            }
        }
    }
}
