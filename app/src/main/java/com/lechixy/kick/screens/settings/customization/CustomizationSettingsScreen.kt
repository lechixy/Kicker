package com.lechixy.kick.screens.settings.customization

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lechixy.kick.screens.settings.SettingsCategoryScreen
import com.lechixy.kick.screens.settings.SettingsDestinations

@Composable
fun CustomizationSettingsScreen(modifier: Modifier = Modifier, onBackClick: () -> Unit) {
    SettingsCategoryScreen(SettingsDestinations.CUSTOMIZATION, onBackClick, modifier)
}
