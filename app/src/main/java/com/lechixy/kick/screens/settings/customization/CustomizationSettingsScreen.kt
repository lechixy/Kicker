package com.lechixy.kick.screens.settings.customization

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingSection
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.settings.model.SettingsKeys

@Composable
fun CustomizationSettingsScreen(onBackClick: () -> Unit) {
    Scaffold(topBar = { SettingsTopBar(stringResource(R.string.settings_customization), onBackClick) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                SettingSection(title = stringResource(R.string.settings_customization),
                    description = stringResource(R.string.settings_customization_description)) {
                    SettingDropdown(key = SettingsKeys.THEME, title = stringResource(R.string.settings_theme),
                        options = listOf("system" to stringResource(R.string.settings_system),
                            "light" to stringResource(R.string.settings_light), "dark" to stringResource(R.string.settings_dark),
                            "amoled" to "AMOLED"))
                    SettingSwitch(key = SettingsKeys.DYNAMIC_COLOR, title = stringResource(R.string.settings_dynamic_color))
                }
            }
        }
    }
}
