package com.lechixy.kick.screens.settings.playback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lechixy.kick.screens.settings.SettingsCategoryScreen
import com.lechixy.kick.screens.settings.SettingsDestinations

@Composable
fun PlayerPlaybackSettingsScreen(modifier: Modifier = Modifier, onBackClick: () -> Unit) {
    SettingsCategoryScreen(SettingsDestinations.PLAYBACK, onBackClick, modifier)
}
