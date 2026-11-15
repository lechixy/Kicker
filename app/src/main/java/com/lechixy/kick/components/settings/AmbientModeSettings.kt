package com.lechixy.kick.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository

internal val ambientModeSearchTerms = listOf(
    R.string.player_ambient_dynamic, R.string.player_ambient_live, R.string.player_ambient_live_fps,
    R.string.player_ambient_intensity, R.string.player_ambient_spread, R.string.player_ambient_fullscreen,
)

internal val PlayerAmbientMode.titleResource: Int
    get() = when (this) {
        PlayerAmbientMode.Off -> R.string.player_ambient_off
        PlayerAmbientMode.Dynamic -> R.string.player_ambient_dynamic
        PlayerAmbientMode.Live -> R.string.player_ambient_live
    }

@Composable
internal fun AmbientModeSettings() {
    val repository = LocalSettingsRepository.current
    val key = SettingsKeys.AMBIENT_MODE
    val storedMode by remember(repository) { repository.get(key) }.collectAsStateWithLifecycle(key.defaultValue)
    val mode = PlayerAmbientMode.fromValue(storedMode)
    val description = when (mode) {
        PlayerAmbientMode.Off -> R.string.player_ambient_description
        PlayerAmbientMode.Dynamic -> R.string.player_ambient_dynamic_description
        PlayerAmbientMode.Live -> R.string.player_ambient_live_description
    }

    SettingDropdown(
        key = key,
        title = stringResource(R.string.player_ambient_selection),
        description = stringResource(description),
        options = PlayerAmbientMode.entries.map { it.value to stringResource(it.titleResource) },
    )
    if (mode == PlayerAmbientMode.Live) {
        SettingDropdown(
            key = SettingsKeys.AMBIENT_LIVE_FPS,
            title = stringResource(R.string.player_ambient_live_fps),
            options = listOf(30, 60).map { it to stringResource(R.string.player_ambient_fps_format, it) },
        )
    }
    val resources = LocalResources.current
    SettingSlider(
        key = SettingsKeys.AMBIENT_INTENSITY,
        title = stringResource(R.string.player_ambient_intensity),
        description = stringResource(R.string.player_ambient_intensity_description),
        valueRange = 25f..150f,
        steps = 24,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingSlider(
        key = SettingsKeys.AMBIENT_SPREAD,
        title = stringResource(R.string.player_ambient_spread),
        description = stringResource(R.string.player_ambient_spread_description),
        valueRange = 50f..150f,
        steps = 19,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingSwitch(
        key = SettingsKeys.AMBIENT_FULLSCREEN,
        title = stringResource(R.string.player_ambient_fullscreen),
        description = stringResource(R.string.player_ambient_fullscreen_description),
    )
}
