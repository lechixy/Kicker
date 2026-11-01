package com.lechixy.kick.components.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

@Composable
fun SettingSwitch(
    key: SettingKey<Boolean>,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    enabled: Boolean = true
) {
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val value by repository.get(key).collectAsStateWithLifecycle(initialValue = key.defaultValue)

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
        enabled = enabled
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
    enabled: Boolean = true
) {
    SettingCard(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
        action = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    )
}