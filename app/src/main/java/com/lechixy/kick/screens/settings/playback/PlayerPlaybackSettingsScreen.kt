package com.lechixy.kick.screens.settings.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingFloatSlider
import com.lechixy.kick.components.settings.SettingSection
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.settings.model.SettingsKeys

@Composable
fun PlayerPlaybackSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SettingsTopBar(title = stringResource(R.string.player_playback), onBackClick = onBackClick) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                item {
                    SettingSection(
                        title = stringResource(R.string.player_section),
                        description = stringResource(R.string.player_section_description)
                    ) {
                        SettingSwitch(key = SettingsKeys.AUTOPLAY, title = stringResource(R.string.player_autoplay))
                        SettingSwitch(key = SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE,
                            title = stringResource(R.string.settings_volume_sync),
                            description = stringResource(R.string.settings_volume_sync_description), enabled = false)
                        SettingFloatSlider(key = SettingsKeys.MINI_PLAYER_WIDTH_DP, title = stringResource(R.string.settings_mini_width),
                            valueRange = com.lechixy.kick.data.settings.model.MiniPlayerSize.MIN_WIDTH_DP..com.lechixy.kick.data.settings.model.MiniPlayerSize.MAX_WIDTH_DP, steps = 24, valueFormatter = { "${it.toInt()} dp" })
                        SettingSwitch(
                            key = SettingsKeys.PIP_ENABLED,
                            title = stringResource(R.string.player_pip),
                            description = stringResource(R.string.player_pip_description)
                        )
                        SettingSwitch(
                            key = SettingsKeys.LOW_LATENCY,
                            title = stringResource(R.string.player_low_latency),
                            description = stringResource(R.string.player_low_latency_description)
                        )
                        SettingSwitch(
                            key = SettingsKeys.AUTO_FULLSCREEN_LIVE,
                            title = stringResource(R.string.player_auto_fullscreen),
                            description = stringResource(R.string.player_auto_fullscreen_description)
                        )
                        SettingDropdown(
                            key = SettingsKeys.VIDEO_QUALITY,
                            title = stringResource(R.string.player_default_quality),
                            description = stringResource(R.string.player_default_quality_description),
                            options = listOf(
                                -1 to stringResource(R.string.player_auto),
                                160 to "160p",
                                360 to "360p",
                                480 to "480p",
                                720 to "720p (HD)",
                                1080 to "1080p (FHD)"
                            )
                        )

                    }
                }
            }
        }
    }
}