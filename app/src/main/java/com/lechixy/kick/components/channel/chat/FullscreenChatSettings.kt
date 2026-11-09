package com.lechixy.kick.components.channel.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveDialog
import com.lechixy.kick.components.settings.SettingAction
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingFloatSlider
import com.lechixy.kick.components.settings.SettingSection
import com.lechixy.kick.components.settings.SettingSlider
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.data.settings.model.FullscreenChatShape
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import java.text.NumberFormat
import kotlinx.coroutines.launch

internal data class FullscreenChatOption(
    @StringRes val title: Int,
    @StringRes val section: Int,
    @StringRes val description: Int? = null,
    val content: @Composable () -> Unit,
)

internal fun fullscreenChatOptions(
    horizontal: Boolean,
    onEditPosition: @Composable () -> Unit,
): List<FullscreenChatOption> = buildList {
    val layout = R.string.fullscreen_chat_layout
    val appearance = R.string.fullscreen_chat_appearance
    add(FullscreenChatOption(R.string.fullscreen_chat_mode, layout) {
        SettingDropdown(
            key = SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL,
            title = stringResource(R.string.fullscreen_chat_mode),
            options = listOf(
                false to stringResource(R.string.fullscreen_chat_vertical),
                true to stringResource(R.string.fullscreen_chat_horizontal),
            ),
        )
    })
    if (horizontal) add(FullscreenChatOption(R.string.fullscreen_chat_lane_count, layout) {
        SettingSlider(
            key = SettingsKeys.FULLSCREEN_CHAT_LANES,
            title = stringResource(R.string.fullscreen_chat_lane_count),
            valueRange = 1f..4f,
            steps = 2,
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_scale, layout, R.string.fullscreen_chat_scale_description) {
        val percent = rememberPercentFormat()
        SettingSlider(
            key = SettingsKeys.FULLSCREEN_CHAT_SCALE,
            title = stringResource(R.string.fullscreen_chat_scale),
            description = stringResource(R.string.fullscreen_chat_scale_description),
            valueRange = 70f..150f,
            steps = 15,
            valueFormatter = { percent.format(it / 100.0) },
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_edit_position, layout, content = onEditPosition))
    add(FullscreenChatOption(R.string.fullscreen_chat_lock_position, layout, R.string.fullscreen_chat_lock_position_description) {
        SettingSwitch(
            key = SettingsKeys.FULLSCREEN_CHAT_POSITION_LOCKED,
            title = stringResource(R.string.fullscreen_chat_lock_position),
            description = stringResource(R.string.fullscreen_chat_lock_position_description),
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_font_size, appearance) {
        SettingSlider(
            key = rememberFullscreenChatFontSizeSetting(),
            title = stringResource(R.string.fullscreen_chat_font_size),
            valueRange = 10f..24f,
            steps = 13,
            valueFormatter = { "$it sp" },
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_shape, appearance) {
        SettingDropdown(
            key = SettingsKeys.FULLSCREEN_CHAT_SHAPE,
            title = stringResource(R.string.fullscreen_chat_shape),
            options = listOf(
                FullscreenChatShape.Square.value to stringResource(R.string.fullscreen_chat_shape_square),
                FullscreenChatShape.Rounded.value to stringResource(R.string.fullscreen_chat_shape_rounded),
                FullscreenChatShape.Pill.value to stringResource(R.string.fullscreen_chat_shape_pill),
            ),
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_opacity, appearance) {
        val percent = rememberPercentFormat()
        SettingFloatSlider(
            key = SettingsKeys.FULLSCREEN_CHAT_OPACITY,
            title = stringResource(R.string.fullscreen_chat_opacity),
            valueRange = 0.35f..0.85f,
            valueFormatter = { percent.format(it.toDouble()) },
        )
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_blur, appearance) {
        SettingSwitch(SettingsKeys.FULLSCREEN_CHAT_BLUR, stringResource(R.string.fullscreen_chat_blur))
    })
    add(FullscreenChatOption(R.string.fullscreen_chat_badges, appearance, R.string.fullscreen_chat_badges_description) {
        SettingSwitch(
            key = SettingsKeys.FULLSCREEN_CHAT_BADGES,
            title = stringResource(R.string.fullscreen_chat_badges),
            description = stringResource(R.string.fullscreen_chat_badges_description),
        )
    })
}

@Composable
private fun rememberPercentFormat(): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { NumberFormat.getPercentInstance(locale) }
}

@Composable
internal fun FullscreenChatSettings(horizontal: Boolean, onEditPosition: () -> Unit, onConnect: (() -> Unit)?) {
    val groups = fullscreenChatOptions(horizontal) {
        SettingAction(stringResource(R.string.fullscreen_chat_edit_position), onClick = onEditPosition)
    }.groupBy { it.section }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        groups.forEach { (title, options) ->
            SettingSection(stringResource(title), itemSpacing = 10.dp) {
                options.forEach { it.content() }
            }
        }
        if (onConnect != null) {
            SettingSection(stringResource(R.string.fullscreen_chat_connection)) {
                SettingAction(stringResource(R.string.chat_connect_action), onClick = onConnect)
            }
        }
    }
}

@Composable
internal fun FullscreenChatPositionSetting() {
    var editing by remember { mutableStateOf(false) }
    val repository = LocalSettingsRepository.current
    val scope = rememberCoroutineScope()
    SettingAction(stringResource(R.string.fullscreen_chat_edit_position), onClick = { editing = true })
    if (editing) {
        val preferences = rememberFullscreenChatPreferences()
        PredictiveDialog(onDismissRequest = { editing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(.94f), shape = MaterialTheme.shapes.large) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainer)) {
                    FullscreenChatOverlay(
                        host = null,
                        channel = null,
                        liveChatViewModel = null,
                        preferences = preferences,
                        playing = false,
                        editing = true,
                        onSavePosition = { horizontal, position ->
                            val key = if (horizontal) SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL_POSITION else SettingsKeys.FULLSCREEN_CHAT_VERTICAL_POSITION
                            scope.launch { repository.set(key, position.encode()) }
                            editing = false
                        },
                        onCancelEditing = { editing = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
