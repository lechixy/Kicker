package com.lechixy.kick.screens.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lechixy.kick.screens.settings.SettingsCategoryScreen
import com.lechixy.kick.screens.settings.SettingsDestinations

@Composable
fun GeneralSettingsScreen(modifier: Modifier = Modifier, onBackClick: () -> Unit) {
    SettingsCategoryScreen(SettingsDestinations.GENERAL, onBackClick, modifier)
}
