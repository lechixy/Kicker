package com.lechixy.kick.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.orderedAppDestinations
import kotlinx.coroutines.launch

@Composable
internal fun NavigationOrderSetting() {
    val repository = LocalSettingsRepository.current
    val setting = SettingsKeys.NAVIGATION_ORDER
    val savedOrder by repository.get(setting).collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.settings_navigation_order)
    SettingCard(
        title = title,
        description = stringResource(R.string.settings_navigation_order_description),
        onClick = { draft = savedOrder }
    )

    val currentDraft = draft ?: return
    val destinations = orderedAppDestinations(currentDraft)
    fun move(from: Int, to: Int) {
        val order = destinations.toMutableList()
        val destination = order.removeAt(from)
        order.add(to, destination)
        draft = order.joinToString(",") { it.name }
    }
    PredictiveAlertDialog(
        onDismissRequest = { draft = null },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                destinations.forEachIndexed { index, destination ->
                    androidx.compose.runtime.key(destination) {
                        val label = stringResource(destination.label)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(destination.activeIcon), contentDescription = null)
                            Text(label, Modifier.weight(1f).padding(horizontal = 12.dp))
                            IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                                Icon(
                                    painterResource(R.drawable.keyboard_arrow_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    stringResource(R.string.settings_navigation_move_up, label)
                                )
                            }
                            IconButton(onClick = { move(index, index + 1) }, enabled = index < destinations.lastIndex) {
                                Icon(
                                    painterResource(R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    stringResource(R.string.settings_navigation_move_down, label)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                draft = null
                scope.launch { repository.set(setting, currentDraft) }
            }) { Text(stringResource(R.string.settings_navigation_order_save)) }
        },
        dismissButton = {
            TextButton(onClick = { draft = null }) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}
