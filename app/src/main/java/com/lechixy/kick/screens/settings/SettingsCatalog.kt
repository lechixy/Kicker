package com.lechixy.kick.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.settings.*
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.MiniPlayerSize
import com.lechixy.kick.util.LanguageUtils.updateAppLocale
import java.text.Normalizer
import java.util.Locale

internal data class SettingsEntry(val id: String, val title: Int, val description: Int?,
    val destination: SettingsDestinations, val content: @Composable () -> Unit)

internal fun matchesSettingsQuery(query: String, title: String, description: String, category: String, locale: Locale): Boolean {
    fun normalize(text: String) = Normalizer.normalize(text.lowercase(locale), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
    val haystack = normalize("$title $description $category")
    return normalize(query).split(Regex("\\s+")).all { it in haystack }
}

@Composable
internal fun settingsCatalog(): List<SettingsEntry> {
    val context = LocalContext.current
    val general = SettingsDestinations.GENERAL
    val custom = SettingsDestinations.CUSTOMIZATION
    val playback = SettingsDestinations.PLAYBACK
    val chat = SettingsDestinations.CHAT
    val openChangelog = LocalOpenChangelog.current
    val backup = LocalSettingsBackupActions.current
    return listOf(
        SettingsEntry("changelog", R.string.settings_changelog, R.string.settings_changelog_description, general) {
            SettingCard(title = stringResource(R.string.settings_changelog), description = stringResource(R.string.settings_changelog_description),
                icon = R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24, onClick = openChangelog)
        },
        SettingsEntry("developer-floating-menu", R.string.developer_floating_menu, null, SettingsDestinations.DEVELOPER) {
            SettingSwitch(SettingsKeys.DEVELOPER_FLOATING_MENU, stringResource(R.string.developer_floating_menu),
                icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("language", R.string.settings_app_language, null, general) {
            SettingDropdown(SettingsKeys.APP_LANGUAGE, stringResource(R.string.settings_app_language),
                icon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                options = listOf("system" to stringResource(R.string.settings_system), "en" to "English", "tr" to "Türkçe"),
                onValueChanged = { updateAppLocale(context, it) })
        },
        SettingsEntry("recommendations", R.string.settings_recommended_language, R.string.settings_recommended_language_description, general) {
            SettingTextField(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE, stringResource(R.string.settings_recommended_language),
                description = stringResource(R.string.settings_recommended_language_description), placeholder = "tr",
                icon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("backup-location", R.string.settings_backup_location, R.string.settings_backup_location_message, general) {
            SettingCard(
                title = stringResource(R.string.settings_backup_location),
                icon = R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = stringResource(
                    if (backup?.customLocationSelected == true) R.string.settings_backup_location_custom_summary
                    else R.string.settings_backup_location_default_summary
                ),
                enabled = backup?.enabled == true,
                onClick = backup?.chooseAutomaticLocation
            )
        },
        SettingsEntry("import-settings", R.string.settings_backup_import, R.string.settings_backup_import_description, general) {
            SettingCard(
                title = stringResource(R.string.settings_backup_import),
                icon = R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = stringResource(R.string.settings_backup_import_description),
                enabled = backup?.enabled == true,
                onClick = backup?.importSettings
            )
        },
        SettingsEntry("export-settings", R.string.settings_backup_export, R.string.settings_backup_export_description, general) {
            SettingCard(
                title = stringResource(R.string.settings_backup_export),
                icon = R.drawable.keyboard_arrow_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = stringResource(R.string.settings_backup_export_description),
                enabled = backup?.enabled == true,
                onClick = backup?.exportSettings
            )
        },
        SettingsEntry("theme", R.string.settings_theme, null, custom) {
            SettingDropdown(SettingsKeys.THEME, stringResource(R.string.settings_theme), options = listOf(
                "system" to stringResource(R.string.settings_system), "light" to stringResource(R.string.settings_light),
                "dark" to stringResource(R.string.settings_dark), "amoled" to "AMOLED"),
                icon = R.drawable.bedtime_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("dynamic-color", R.string.settings_dynamic_color, null, custom) {
            SettingSwitch(SettingsKeys.DYNAMIC_COLOR, stringResource(R.string.settings_dynamic_color),
                icon = R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("navigation-order", R.string.settings_navigation_order, R.string.settings_navigation_order_description, custom) {
            NavigationOrderSetting()
        },
        SettingsEntry("top-bar-logo-animation", R.string.settings_top_bar_logo_animation, null, custom) {
            SettingSwitch(SettingsKeys.TOP_BAR_LOGO_ANIMATION, stringResource(R.string.settings_top_bar_logo_animation),
                icon = R.drawable.motion_play_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("autoplay", R.string.player_autoplay, null, playback) {
            SettingSwitch(SettingsKeys.AUTOPLAY, stringResource(R.string.player_autoplay),
                icon = R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("volume", R.string.settings_volume_sync, R.string.settings_volume_sync_description, playback) {
            SettingSwitch(SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE, stringResource(R.string.settings_volume_sync),
                description = stringResource(R.string.settings_volume_sync_description),
                icon = R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("mini-player", R.string.settings_mini_width, null, playback) {
            SettingFloatSlider(SettingsKeys.MINI_PLAYER_WIDTH_DP, stringResource(R.string.settings_mini_width),
                valueRange = MiniPlayerSize.MIN_WIDTH_DP..MiniPlayerSize.MAX_WIDTH_DP, steps = 24,
                icon = R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${it.toInt()} dp" })
        },
        SettingsEntry("pip", R.string.player_pip, R.string.player_pip_description, playback) {
            SettingSwitch(SettingsKeys.PIP_ENABLED, stringResource(R.string.player_pip),
                description = stringResource(R.string.player_pip_description),
                icon = R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("latency", R.string.player_low_latency, R.string.player_low_latency_description, playback) {
            SettingSwitch(SettingsKeys.LOW_LATENCY, stringResource(R.string.player_low_latency),
                description = stringResource(R.string.player_low_latency_description),
                icon = R.drawable.bolt_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("fullscreen", R.string.player_auto_fullscreen, R.string.player_auto_fullscreen_description, playback) {
            SettingSwitch(SettingsKeys.AUTO_FULLSCREEN_LIVE, stringResource(R.string.player_auto_fullscreen),
                description = stringResource(R.string.player_auto_fullscreen_description),
                icon = R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("quality", R.string.player_default_quality, R.string.player_default_quality_description, playback) {
            SettingDropdown(SettingsKeys.VIDEO_QUALITY, stringResource(R.string.player_default_quality), description = stringResource(R.string.player_default_quality_description),
                icon = R.drawable.hd_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                options = listOf(-1 to stringResource(R.string.player_auto), 160 to "160p", 360 to "360p", 480 to "480p", 720 to "720p (HD)", 1080 to "1080p (FHD)"))
        },
        SettingsEntry("timestamps", R.string.chat_show_timestamps, null, chat) { ChatTimestampSetting() },
        SettingsEntry("chat-connection", R.string.settings_chat_connection, null, chat) {
            SettingSwitch(SettingsKeys.AUTO_CONNECT_CHAT, stringResource(R.string.settings_chat_connection),
                icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("chat-spacing", R.string.settings_chat_spacing, null, chat) {
            SettingSlider(SettingsKeys.CHAT_MESSAGE_SPACING, stringResource(R.string.settings_chat_spacing), valueRange = 0f..25f, steps = 24,
                icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "$it dp" })
        },
        SettingsEntry("chat-width", R.string.settings_chat_width, null, chat) {
            SettingFloatSlider(SettingsKeys.CHAT_WIDTH_FRACTION, stringResource(R.string.settings_chat_width), valueRange = .18f.. .60f, steps = 41,
                icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${(it * 100).toInt()}%" })
        },
        SettingsEntry("chat-font", R.string.player_chat_font, R.string.player_chat_font_description, chat) {
            SettingSlider(SettingsKeys.CHAT_FONT_SIZE, stringResource(R.string.player_chat_font), description = stringResource(R.string.player_chat_font_description), valueRange = 10f..24f, steps = 13,
                icon = R.drawable.edit_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${it}sp" })
        }
    )
}

@Composable
fun SettingsCategoryScreen(destination: SettingsDestinations, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val entries = settingsCatalog().filter { it.destination == destination }
    Scaffold(modifier = modifier, topBar = { SettingsTopBar(stringResource(destination.titleRes), onBackClick) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally)
            .widthIn(max = 840.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { it.content() }
        }
    }
}
