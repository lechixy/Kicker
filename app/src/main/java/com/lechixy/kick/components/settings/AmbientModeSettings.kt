package com.lechixy.kick.components.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.data.settings.model.PlayerAmbientMode
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.resolvedDefault
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import kotlinx.coroutines.launch

internal val ambientModeSearchTerms = listOf(
    R.string.player_ambient_dynamic, R.string.player_ambient_live, R.string.player_ambient_live_fps,
    R.string.player_ambient_intensity, R.string.player_ambient_spread, R.string.player_ambient_fullscreen,
    R.string.player_ambient_through_ui, R.string.player_ambient_preserve_black_bars,
    R.string.player_ambient_battery_saver, R.string.player_ambient_chat_opacity,
    R.string.player_ambient_navigation_opacity, R.string.player_ambient_reset_levels,
)

internal val PlayerAmbientMode.titleResource: Int
    get() = when (this) {
        PlayerAmbientMode.Off -> R.string.player_ambient_off
        PlayerAmbientMode.Dynamic -> R.string.player_ambient_dynamic
        PlayerAmbientMode.Live -> R.string.player_ambient_live
    }

@Composable
private fun selectedAmbientMode(): PlayerAmbientMode {
    val repository = LocalSettingsRepository.current
    val key = SettingsKeys.AMBIENT_MODE
    val storedMode by remember(repository) { repository.get(key) }.collectAsStateWithLifecycle(key.defaultValue)
    return PlayerAmbientMode.fromValue(storedMode)
}

private val PlayerAmbientMode.descriptionResource: Int
    get() = when (this) {
        PlayerAmbientMode.Off -> R.string.player_ambient_description
        PlayerAmbientMode.Dynamic -> R.string.player_ambient_dynamic_description
        PlayerAmbientMode.Live -> R.string.player_ambient_live_description
    }

@Composable
private fun ambientModeOptions() = PlayerAmbientMode.entries.map { it.value to stringResource(it.titleResource) }

@Composable
internal fun AmbientModeSettingsExpander(initiallyExpanded: Boolean) {
    val repository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()
    val key = SettingsKeys.AMBIENT_MODE
    val default = key.resolvedDefault
    val value by remember(repository) { repository.get(key) }.collectAsStateWithLifecycle(default)
    val mode = PlayerAmbientMode.fromValue(value)
    SettingsExpander(
        title = stringResource(R.string.player_ambient_mode),
        description = stringResource(mode.descriptionResource),
        icon = R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        initiallyExpanded = initiallyExpanded,
        headerAction = {
            SettingDropdownAction(value, ambientModeOptions()) { selected ->
                scope.launch { repository.set(key, selected) }
            }
        },
        onReset = if (value != default) ({ scope.launch { repository.reset(key) } }) else null,
    ) {
        AmbientModeControls(mode)
    }
}

@Composable
internal fun AmbientModeSettings() {
    val mode = selectedAmbientMode()
    SettingDropdown(
        key = SettingsKeys.AMBIENT_MODE,
        title = stringResource(R.string.player_ambient_selection),
        description = stringResource(mode.descriptionResource),
        icon = R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        options = ambientModeOptions(),
    )
    AmbientModeControls(mode)
}

@Composable
private fun AmbientModeControls(mode: PlayerAmbientMode) {
    val repository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()
    val throughUiKey = SettingsKeys.AMBIENT_THROUGH_UI
    val throughUi by remember(repository) { repository.get(throughUiKey) }
        .collectAsStateWithLifecycle(throughUiKey.defaultValue)
    if (mode == PlayerAmbientMode.Live) {
        SettingDropdown(
            key = SettingsKeys.AMBIENT_LIVE_FPS,
            title = stringResource(R.string.player_ambient_live_fps),
            icon = R.drawable.speed_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            options = listOf(30, 60).map { it to stringResource(R.string.player_ambient_fps_format, it) },
        )
    }
    val resources = LocalResources.current
    SettingSlider(
        key = SettingsKeys.AMBIENT_INTENSITY,
        title = stringResource(R.string.player_ambient_intensity),
        description = stringResource(R.string.player_ambient_intensity_description),
        icon = R.drawable.brightness_6_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        valueRange = SettingsKeys.AMBIENT_INTENSITY_RANGE.let { it.first.toFloat()..it.last.toFloat() },
        steps = 37,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingSlider(
        key = SettingsKeys.AMBIENT_SPREAD,
        title = stringResource(R.string.player_ambient_spread),
        description = stringResource(R.string.player_ambient_spread_description),
        icon = R.drawable.blur_on_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        valueRange = SettingsKeys.AMBIENT_SPREAD_RANGE.let { it.first.toFloat()..it.last.toFloat() },
        steps = 34,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingCard(
        title = stringResource(R.string.player_ambient_reset_levels),
        icon = R.drawable.restart_alt_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        onClick = {
            scope.launch {
                repository.editSettings {
                    remove(SettingsKeys.AMBIENT_INTENSITY.key)
                    remove(SettingsKeys.AMBIENT_SPREAD.key)
                }
            }
        },
    )
    SettingSwitch(
        key = SettingsKeys.AMBIENT_BATTERY_SAVER,
        title = stringResource(R.string.player_ambient_battery_saver),
        description = stringResource(R.string.player_ambient_battery_saver_description),
        icon = R.drawable.battery_saver_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    )
    SettingSwitch(
        key = SettingsKeys.AMBIENT_FULLSCREEN,
        title = stringResource(R.string.player_ambient_fullscreen),
        description = stringResource(R.string.player_ambient_fullscreen_description),
        icon = R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    )
    SettingSwitch(
        key = throughUiKey,
        title = stringResource(R.string.player_ambient_through_ui),
        description = stringResource(R.string.player_ambient_through_ui_description),
        icon = R.drawable.layers_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    )
    SettingSlider(
        key = SettingsKeys.AMBIENT_CHAT_OPACITY,
        title = stringResource(R.string.player_ambient_chat_opacity),
        icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        valueRange = 0f..100f,
        steps = 19,
        enabled = throughUi,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingSlider(
        key = SettingsKeys.AMBIENT_NAVIGATION_OPACITY,
        title = stringResource(R.string.player_ambient_navigation_opacity),
        icon = R.drawable.space_dashboard_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
        valueRange = 0f..100f,
        steps = 19,
        enabled = throughUi,
        valueFormatter = { resources.getString(R.string.player_ambient_percent, it) },
    )
    SettingSwitch(
        key = SettingsKeys.AMBIENT_PRESERVE_BLACK_BARS,
        title = stringResource(R.string.player_ambient_preserve_black_bars),
        description = stringResource(R.string.player_ambient_preserve_black_bars_description),
        icon = R.drawable.aspect_ratio_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
    )
}
