package com.lechixy.kick.screens.settings.customization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingSlider
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.SettingsExpander
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.SnowParticleStyle
import java.text.NumberFormat

internal val winterModeSearchTerms = listOf(
    R.string.settings_snow_style, R.string.settings_snow_dots, R.string.settings_snow_flakes,
    R.string.settings_snow_intensity, R.string.settings_snow_speed, R.string.settings_snow_size,
    R.string.settings_snow_scroll,
)

@Composable
internal fun WinterModeSettings(initiallyExpanded: Boolean) {
    SettingsExpander(
        title = stringResource(R.string.settings_winter_snow),
        description = stringResource(R.string.settings_winter_snow_description),
        icon = R.drawable.ac_unit_24dp,
        toggleKey = SettingsKeys.WINTER_SNOW,
        initiallyExpanded = initiallyExpanded,
    ) { enabled ->
        val locale = LocalConfiguration.current.locales[0]
        val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
        val format: (Int) -> String = { percent.format(it / 100.0) }
        SettingDropdown(
            key = SettingsKeys.WINTER_SNOW_STYLE,
            title = stringResource(R.string.settings_snow_style),
            icon = R.drawable.category_24dp,
            enabled = enabled,
            options = listOf(
                SnowParticleStyle.Dots.name to stringResource(R.string.settings_snow_dots),
                SnowParticleStyle.Flakes.name to stringResource(R.string.settings_snow_flakes),
            ),
        )
        SettingSlider(SettingsKeys.WINTER_SNOW_INTENSITY, stringResource(R.string.settings_snow_intensity),
            valueRange = 50f..150f, steps = 9, icon = R.drawable.bubble_chart_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            enabled = enabled, valueFormatter = format)
        SettingSlider(SettingsKeys.WINTER_SNOW_SPEED, stringResource(R.string.settings_snow_speed),
            valueRange = 50f..200f, steps = 14, icon = R.drawable.speed_24dp,
            enabled = enabled, valueFormatter = format)
        SettingSlider(SettingsKeys.WINTER_SNOW_SIZE, stringResource(R.string.settings_snow_size),
            valueRange = 75f..150f, steps = 14, icon = R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
            enabled = enabled, valueFormatter = format)
        SettingSwitch(SettingsKeys.WINTER_SNOW_SCROLL, stringResource(R.string.settings_snow_scroll),
            description = stringResource(R.string.settings_snow_scroll_description), icon = R.drawable.swap_vert_24dp,
            enabled = enabled)
    }
}
