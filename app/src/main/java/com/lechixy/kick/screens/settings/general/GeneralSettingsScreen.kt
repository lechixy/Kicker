package com.lechixy.kick.screens.settings.general

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

import com.lechixy.kick.R
import com.lechixy.kick.components.settings.SettingDropdown
import com.lechixy.kick.components.settings.SettingFloatSlider
import com.lechixy.kick.components.settings.SettingSection
import com.lechixy.kick.components.settings.SettingSlider
import com.lechixy.kick.components.settings.SettingSwitch
import com.lechixy.kick.components.settings.SettingTextField
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.util.LanguageUtils.updateAppLocale

@Composable
fun GeneralSettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { SettingsTopBar(title = stringResource(R.string.settings_general), onBackClick = onBackClick) }
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
                        title = stringResource(R.string.settings_chat_interface),
                        description = stringResource(R.string.settings_chat_description)
                    ) {
                        val context = LocalContext.current
                        SettingDropdown(
                            key = SettingsKeys.APP_LANGUAGE,
                            title = stringResource(R.string.settings_app_language),
                            options = listOf(
                                "system" to stringResource(R.string.settings_system),
                                "en" to "English",
                                "tr" to "Türkçe"
                            ),
                            onValueChanged = { selectedLanguageTag ->
                                updateAppLocale(context, selectedLanguageTag)
                            }
                        )

                        SettingSwitch(key = SettingsKeys.AUTO_CONNECT_CHAT, title = stringResource(R.string.settings_chat_connection))
                        SettingSlider(key = SettingsKeys.CHAT_MESSAGE_SPACING, title = stringResource(R.string.settings_chat_spacing),
                            valueRange = 0f..25f, steps = 24, valueFormatter = { "${it.toInt()} dp" })
                        SettingFloatSlider(key = SettingsKeys.CHAT_WIDTH_FRACTION, title = stringResource(R.string.settings_chat_width),
                            valueRange = .18f.. .60f, steps = 41, valueFormatter = { "${(it * 100).toInt()}%" })
                        SettingSlider(
                            key = SettingsKeys.CHAT_FONT_SIZE,
                            title = stringResource(R.string.player_chat_font),
                            description = stringResource(R.string.player_chat_font_description),
                            valueRange = 10f..24f,
                            steps = 13,
                            valueFormatter = { "${it}sp" }
                        )
                    }
                }

                item {
                    SettingSection(
                        title = stringResource(R.string.settings_recommendations),
                        description = stringResource(R.string.settings_recommendations_description)
                    ) {
                        SettingTextField(
                            key = SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE,
                            title = stringResource(R.string.settings_recommended_language),
                            description = stringResource(R.string.settings_recommended_language_description),
                            placeholder = "tr"
                        )
                    }
                }
            }
        }
    }
}
