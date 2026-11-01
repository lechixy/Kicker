package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val repository = LocalSettingsRepository.current
    val coroutineScope = rememberCoroutineScope()
    val currentValue by repository.get(key).collectAsStateWithLifecycle(initialValue = key.defaultValue)

    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == currentValue }?.second
        ?: currentValue.toString()

    SettingCard(
        title = title,
        modifier = modifier,
        description = description,
        icon = icon,
        enabled = enabled,
        action = {
            Box {
                FilledTonalButton(
                    onClick = { expanded = true },
                    enabled = enabled
                ) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Icon(
                        painter = painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (optionValue, label) ->
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = {
                                expanded = false
                                coroutineScope.launch {
                                    repository.set(key, optionValue)
                                }
                                onValueChanged?.invoke(optionValue)
                            }
                        )
                    }
                }
            }
        }
    )
}