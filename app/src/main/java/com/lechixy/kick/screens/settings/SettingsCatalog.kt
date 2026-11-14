package com.lechixy.kick.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.FullscreenChatPositionSetting
import com.lechixy.kick.components.channel.chat.FullscreenChatSettingsExpander
import com.lechixy.kick.components.channel.chat.fullscreenChatOptions
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.components.settings.*
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.MiniPlayerSize
import com.lechixy.kick.util.LanguageUtils.updateAppLocale
import com.lechixy.kick.screens.settings.customization.WinterModeSettings
import com.lechixy.kick.screens.settings.customization.winterModeSearchTerms
import java.text.Normalizer
import java.util.Locale

internal data class SettingsEntry(val id: String, val title: Int, val description: Int?,
    val destination: SettingsDestinations, val group: Int, val searchTerms: List<Int> = emptyList(),
    val content: @Composable () -> Unit)

internal fun matchesSettingsQuery(query: String, title: String, description: String, category: String, locale: Locale): Boolean {
    fun normalize(text: String) = Normalizer.normalize(text.lowercase(locale), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
    val haystack = normalize("$title $description $category")
    return normalize(query).split(Regex("\\s+")).all { it in haystack }
}

@Composable
internal fun settingsCatalog(expandGroups: Boolean = false): List<SettingsEntry> {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val general = SettingsDestinations.GENERAL
    val custom = SettingsDestinations.CUSTOMIZATION
    val playback = SettingsDestinations.PLAYBACK
    val chat = SettingsDestinations.CHAT
    val horizontal by LocalSettingsRepository.current.get(SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL)
        .collectAsStateWithLifecycle(SettingsKeys.FULLSCREEN_CHAT_HORIZONTAL.defaultValue)
    val backup = LocalSettingsBackupActions.current
    return listOf(
        SettingsEntry("developer-floating-menu", R.string.developer_floating_menu, null, SettingsDestinations.DEVELOPER, R.string.settings_group_interface) {
            SettingSwitch(SettingsKeys.DEVELOPER_FLOATING_MENU, stringResource(R.string.developer_floating_menu),
                icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("language", R.string.settings_app_language, null, general, R.string.settings_group_language) {
            SettingDropdown(SettingsKeys.APP_LANGUAGE, stringResource(R.string.settings_app_language),
                icon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                options = listOf("system" to stringResource(R.string.settings_system), "en" to "English", "tr" to "Türkçe"),
                onValueChanged = { updateAppLocale(context, it) })
        },
        SettingsEntry("recommendations", R.string.settings_recommended_language, R.string.settings_recommended_language_description, general, R.string.settings_group_language) {
            SettingTextField(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE, stringResource(R.string.settings_recommended_language),
                description = stringResource(R.string.settings_recommended_language_description), placeholder = "tr",
                icon = R.drawable.public_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
            SettingsEntry("backup-location", R.string.settings_backup_location, null, general, R.string.settings_group_backup) {
            SettingCard(
                title = stringResource(R.string.settings_backup_location),
                icon = R.drawable.settings_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = if (backup?.customLocationSelected == true)
                    stringResource(R.string.settings_backup_location_custom_summary) else backup?.defaultPath,
                enabled = backup?.enabled == true,
                onClick = backup?.chooseAutomaticLocation
            )
        },
        SettingsEntry("import-settings", R.string.settings_backup_import, R.string.settings_backup_import_description, general, R.string.settings_group_backup) {
            SettingCard(
                title = stringResource(R.string.settings_backup_import),
                icon = R.drawable.keyboard_arrow_down_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = stringResource(R.string.settings_backup_import_description),
                enabled = backup?.enabled == true,
                onClick = backup?.importSettings
            )
        },
        SettingsEntry("export-settings", R.string.settings_backup_export, R.string.settings_backup_export_description, general, R.string.settings_group_backup) {
            SettingCard(
                title = stringResource(R.string.settings_backup_export),
                icon = R.drawable.keyboard_arrow_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                description = stringResource(R.string.settings_backup_export_description),
                enabled = backup?.enabled == true,
                onClick = backup?.exportSettings
            )
        },
        SettingsEntry("theme", R.string.settings_theme, null, custom, R.string.settings_group_appearance) {
            SettingDropdown(SettingsKeys.THEME, stringResource(R.string.settings_theme), options = listOf(
                "system" to stringResource(R.string.settings_system), "light" to stringResource(R.string.settings_light),
                "dark" to stringResource(R.string.settings_dark), "amoled" to "AMOLED"),
                icon = R.drawable.bedtime_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("dynamic-color", R.string.settings_dynamic_color, null, custom, R.string.settings_group_appearance) {
            SettingSwitch(SettingsKeys.DYNAMIC_COLOR, stringResource(R.string.settings_dynamic_color),
                icon = R.drawable.palette_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("winter-snow", R.string.settings_winter_snow, R.string.settings_winter_snow_description,
            custom, R.string.settings_group_seasonal_effects, searchTerms = winterModeSearchTerms) {
            WinterModeSettings(initiallyExpanded = expandGroups)
        },
        SettingsEntry("navigation-order", R.string.settings_navigation_order, R.string.settings_navigation_order_description, custom, R.string.settings_group_navigation) {
            NavigationOrderSetting()
        },
        SettingsEntry("top-bar-logo-animation", R.string.settings_top_bar_logo_animation, null, custom, R.string.settings_group_navigation) {
            SettingSwitch(SettingsKeys.TOP_BAR_LOGO_ANIMATION, stringResource(R.string.settings_top_bar_logo_animation),
                icon = R.drawable.motion_play_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("home-order", R.string.settings_home_order, R.string.settings_home_order_description, custom, R.string.settings_group_home) {
            HomeSectionOrderSetting()
        },
        SettingsEntry("autoplay", R.string.player_autoplay, null, playback, R.string.settings_group_streaming) {
            SettingSwitch(SettingsKeys.AUTOPLAY, stringResource(R.string.player_autoplay),
                icon = R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("volume", R.string.settings_volume_sync, R.string.settings_volume_sync_description, playback, R.string.settings_group_audio) {
            SettingSwitch(SettingsKeys.SYNC_PLAYER_VOLUME_WITH_DEVICE, stringResource(R.string.settings_volume_sync),
                description = stringResource(R.string.settings_volume_sync_description),
                icon = R.drawable.volume_up_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("mini-player", R.string.settings_mini_width, null, playback, R.string.settings_group_player_layout) {
            val widthSetting = SettingsKeys.miniPlayerWidth(LocalConfiguration.current.smallestScreenWidthDp)
            SettingFloatSlider(widthSetting, stringResource(R.string.settings_mini_width),
                valueRange = MiniPlayerSize.MIN_WIDTH_DP..MiniPlayerSize.MAX_WIDTH_DP, steps = 24,
                icon = R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${it.toInt()} dp" })
        },
        SettingsEntry("pip", R.string.player_pip, R.string.player_pip_description, playback, R.string.settings_group_player_layout) {
            SettingSwitch(SettingsKeys.PIP_ENABLED, stringResource(R.string.player_pip),
                description = stringResource(R.string.player_pip_description),
                icon = R.drawable.theaters_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("latency", R.string.player_low_latency, R.string.player_low_latency_description, playback, R.string.settings_group_streaming) {
            SettingSwitch(SettingsKeys.LOW_LATENCY, stringResource(R.string.player_low_latency),
                description = stringResource(R.string.player_low_latency_description),
                icon = R.drawable.bolt_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("fullscreen", R.string.player_auto_fullscreen, R.string.player_auto_fullscreen_description, playback, R.string.settings_group_player_layout) {
            SettingSwitch(SettingsKeys.AUTO_FULLSCREEN_LIVE, stringResource(R.string.player_auto_fullscreen),
                description = stringResource(R.string.player_auto_fullscreen_description),
                icon = R.drawable.fullscreen_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("quality", R.string.player_default_quality, R.string.player_default_quality_description, playback, R.string.settings_group_streaming) {
            SettingDropdown(SettingsKeys.VIDEO_QUALITY, stringResource(R.string.player_default_quality), description = stringResource(R.string.player_default_quality_description),
                icon = R.drawable.hd_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                options = listOf(-1 to stringResource(R.string.player_auto), 160 to "160p", 360 to "360p", 480 to "480p", 720 to "720p (HD)", 1080 to "1080p (FHD)"))
        },
        SettingsEntry("chat-connection", R.string.settings_chat_connection, null, chat, R.string.fullscreen_chat_connection) {
            SettingSwitch(SettingsKeys.AUTO_CONNECT_CHAT, stringResource(R.string.settings_chat_connection),
                icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24)
        },
        SettingsEntry("timestamps", R.string.chat_show_timestamps, null, chat, R.string.settings_group_appearance) { ChatTimestampSetting() },
        SettingsEntry("chat-spacing", R.string.settings_chat_spacing, null, chat, R.string.settings_group_appearance) {
            SettingSlider(SettingsKeys.chatMessageSpacing(configuration.smallestScreenWidthDp), stringResource(R.string.settings_chat_spacing), valueRange = 0f..25f, steps = 24,
                icon = R.drawable.tune_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "$it dp" })
        },
        SettingsEntry("chat-width", R.string.settings_chat_width, null, chat, R.string.settings_group_appearance) {
            SettingFloatSlider(SettingsKeys.chatWidth(configuration.screenWidthDp), stringResource(R.string.settings_chat_width), valueRange = .18f.. .60f, steps = 41,
                icon = R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${kotlin.math.round(it * 100).toInt()}%" })
        },
        SettingsEntry("chat-font", R.string.player_chat_font, R.string.player_chat_font_description, chat, R.string.settings_group_appearance) {
            SettingSlider(SettingsKeys.chatFontSize(configuration.smallestScreenWidthDp), stringResource(R.string.player_chat_font), description = stringResource(R.string.player_chat_font_description), valueRange = 10f..24f, steps = 13,
                icon = R.drawable.edit_24dp_e3e3e3_fill0_wght400_grad0_opsz24, valueFormatter = { "${it}sp" })
        },
        SettingsEntry("fullscreen-chat", R.string.fullscreen_chat_title, R.string.settings_fullscreen_chat_enabled,
            chat, R.string.settings_group_player_layout,
            searchTerms = fullscreenChatOptions(horizontal) { }.flatMap { listOfNotNull(it.title, it.description) }) {
            FullscreenChatSettingsExpander(horizontal, initiallyExpanded = expandGroups) { enabled ->
                FullscreenChatPositionSetting(enabled)
            }
        },
    )
}

internal fun LazyListScope.groupedSettings(entries: List<SettingsEntry>, includeCategory: Boolean = false) {
    entries.groupBy { it.destination to it.group }.forEach { (section, groupEntries) ->
        val (category, group) = section
        item("heading:${category.name}:$group") {
            val groupName = stringResource(group)
            SettingsGroupTitle(if (includeCategory) "${stringResource(category.titleRes)} · $groupName" else groupName)
        }
        items(groupEntries, key = { it.id }) { it.content() }
    }
}

@Composable
internal fun SettingsGroupTitle(title: String) {
    Text(title, Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
internal fun SettingsPage(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    Scaffold(modifier = modifier, topBar = { SettingsTopBar(title, onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = SettingsContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun SettingsCategoryScreen(destination: SettingsDestinations, onBackClick: () -> Unit, modifier: Modifier = Modifier) {
    val entries = settingsCatalog().filter { it.destination == destination }
    SettingsPage(stringResource(destination.titleRes), onBackClick, modifier) {
        groupedSettings(entries)
    }
}
