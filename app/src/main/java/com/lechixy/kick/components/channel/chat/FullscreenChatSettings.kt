package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingAction
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingFloatSlider
import com.lechixy.kick.components.settings.SettingSection
import com.lechixy.kick.components.settings.SettingSlider
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import com.lechixy.kick.data.settings.model.SettingsKeys
import java.text.NumberFormat

@Composable
internal fun FullscreenChatSettings(
    horizontal: Boolean,
    onEditPosition: () -> Unit,
    onConnect: (() -> Unit)?,
) {
    val fontSize = rememberFullscreenChatFontSizeSetting()
    val locale = LocalConfiguration.current.locales[0]
    val percent = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SettingSection(stringResource(R.string.fullscreen_chat_layout), itemSpacing = 10.dp) {
            SettingDropdown(
                key = SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL,
                title = stringResource(R.string.fullscreen_chat_mode),
                options = listOf(
                    false to stringResource(R.string.fullscreen_chat_vertical),
                    true to stringResource(R.string.fullscreen_chat_horizontal),
                ),
            )
            if (horizontal) {
                SettingSlider(
                    key = SettingsKeys.FULLSCREEN_CHAT_LANES,
                    title = stringResource(R.string.fullscreen_chat_lane_count),
                    valueRange = 1f..4f,
                    steps = 2,
                )
            }
            SettingSlider(
                key = SettingsKeys.FULLSCREEN_CHAT_SCALE,
                title = stringResource(R.string.fullscreen_chat_scale),
                description = stringResource(R.string.fullscreen_chat_scale_description),
                valueRange = 70f..150f,
                steps = 15,
                valueFormatter = { percent.format(it / 100.0) },
            )
            SettingAction(stringResource(R.string.fullscreen_chat_edit_position), onClick = onEditPosition)
            SettingSwitch(
                key = SettingsKeys.FULLSCREEN_CHAT_POSITION_LOCKED,
                title = stringResource(R.string.fullscreen_chat_lock_position),
                description = stringResource(R.string.fullscreen_chat_lock_position_description),
            )
        }
        SettingSection(stringResource(R.string.fullscreen_chat_appearance), itemSpacing = 10.dp) {
            SettingSlider(
                key = fontSize,
                title = stringResource(R.string.fullscreen_chat_font_size),
                valueRange = 10f..24f,
                steps = 13,
                valueFormatter = { "$it sp" },
            )
            SettingDropdown(
                key = SettingsKeys.FULLSCREEN_CHAT_SHAPE,
                title = stringResource(R.string.fullscreen_chat_shape),
                options = listOf(
                    FullscreenChatShape.Square.value to stringResource(R.string.fullscreen_chat_shape_square),
                    FullscreenChatShape.Rounded.value to stringResource(R.string.fullscreen_chat_shape_rounded),
                    FullscreenChatShape.Pill.value to stringResource(R.string.fullscreen_chat_shape_pill),
                ),
            )
            SettingFloatSlider(
                key = SettingsKeys.FULLSCREEN_CHAT_OPACITY,
                title = stringResource(R.string.fullscreen_chat_opacity),
                valueRange = 0.35f..0.85f,
                valueFormatter = { percent.format(it.toDouble()) },
            )
            SettingSwitch(
                key = SettingsKeys.FULLSCREEN_CHAT_BLUR,
                title = stringResource(R.string.fullscreen_chat_blur),
            )
            SettingSwitch(
                key = SettingsKeys.FULLSCREEN_CHAT_BADGES,
                title = stringResource(R.string.fullscreen_chat_badges),
                description = stringResource(R.string.fullscreen_chat_badges_description),
            )
        }
        if (onConnect != null) {
            SettingSection(stringResource(R.string.fullscreen_chat_connection)) {
                SettingAction(stringResource(R.string.chat_connect_action), onClick = onConnect)
            }
        }
    }
}
