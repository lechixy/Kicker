package com.lechixy.kick.data.settings.model

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

sealed class SettingType {
    data object BooleanType : SettingType()
    data object StringType : SettingType()
    data object IntType : SettingType()
    data object FloatType : SettingType()
}

data class SettingKey<T>(
    val key: Preferences.Key<T>,
    val defaultValue: T,
    val type: SettingType,
    val isExportable: Boolean = true,
    val validator: ((T) -> Boolean)? = null,
    val defaultProvider: (() -> T)? = null
)

object SettingsKeys {
    val IS_FIRST_RUN = SettingKey(booleanPreferencesKey("is_first_run"), true, SettingType.BooleanType, isExportable = false)
    val LAST_USED_VERSION = SettingKey(stringPreferencesKey("last_used_version"), "", SettingType.StringType, isExportable = false)
    val NAVIGATION_RAIL_EXPANDED = SettingKey(booleanPreferencesKey("navigation_rail_expanded"), false, SettingType.BooleanType)
    val DEVELOPER_ENABLED = SettingKey(booleanPreferencesKey("developer_enabled"), false, SettingType.BooleanType, isExportable = false)
    val DEVELOPER_FLOATING_MENU = SettingKey(booleanPreferencesKey("developer_floating_menu"), true, SettingType.BooleanType, isExportable = false)
    val DEVELOPER_MENU_X = SettingKey(floatPreferencesKey("developer_menu_x"), 0.5f, SettingType.FloatType, isExportable = false)
    val DEVELOPER_MENU_Y = SettingKey(floatPreferencesKey("developer_menu_y"), 0.5f, SettingType.FloatType, isExportable = false)
    /*
     * General
     */
    val APP_LANGUAGE = SettingKey(
        key = stringPreferencesKey("app_language"),
        defaultValue = "system",
        type = SettingType.StringType
    )
    val THEME = SettingKey(
        key = stringPreferencesKey("theme"),
        defaultValue = "system",
        type = SettingType.StringType
    )
    val DYNAMIC_COLOR = SettingKey(
        key = booleanPreferencesKey("dynamic_color"),
        defaultValue = false,
        type = SettingType.BooleanType
    )
    val NAVIGATION_ORDER = SettingKey(stringPreferencesKey("navigation_order"), "", SettingType.StringType)
    val WINTER_SNOW = SettingKey(booleanPreferencesKey("winter_snow"), false, SettingType.BooleanType)
    val WINTER_SNOW_STYLE = SettingKey(stringPreferencesKey("winter_snow_style"), SnowParticleStyle.Dots.name,
        SettingType.StringType, validator = { value -> SnowParticleStyle.entries.any { it.name == value } })
    val WINTER_SNOW_INTENSITY = SettingKey(intPreferencesKey("winter_snow_intensity"), 100,
        SettingType.IntType, validator = { it in 50..150 })
    val WINTER_SNOW_SPEED = SettingKey(intPreferencesKey("winter_snow_speed"), 100,
        SettingType.IntType, validator = { it in 50..200 })
    val WINTER_SNOW_SIZE = SettingKey(intPreferencesKey("winter_snow_size"), 100,
        SettingType.IntType, validator = { it in 75..150 })
    val WINTER_SNOW_SCROLL = SettingKey(booleanPreferencesKey("winter_snow_scroll"), true, SettingType.BooleanType)
    val HOME_SECTION_ORDER = SettingKey(stringPreferencesKey("home_section_order"), "", SettingType.StringType)
    val TOP_BAR_LOGO_ANIMATION = SettingKey(booleanPreferencesKey("top_bar_logo_animation"), true, SettingType.BooleanType)

    /*
     * Player & Playback
     */
    val AUTOPLAY = SettingKey(
        key = booleanPreferencesKey("autoplay"),
        defaultValue = true,
        type = SettingType.BooleanType
    )
    val LAST_SLEEP_TIMER_MINUTES = SettingKey(
        key = intPreferencesKey("last_sleep_timer_minutes"),
        defaultValue = 30,
        type = SettingType.IntType,
        validator = { it in 5..60 && it % 5 == 0 }
    )
    val PIP_ENABLED = SettingKey(
        key = booleanPreferencesKey("pip_enabled"),
        defaultValue = true,
        type = SettingType.BooleanType
    )
    val LOW_LATENCY = SettingKey(
        key = booleanPreferencesKey("low_latency_v2_enabled"),
        defaultValue = false,
        type = SettingType.BooleanType
    )
    val SYNC_PLAYER_VOLUME_WITH_DEVICE = SettingKey(
        key = booleanPreferencesKey("sync_player_volume_with_device"),
        defaultValue = false,
        type = SettingType.BooleanType
    )
    val AUTO_FULLSCREEN_LIVE = SettingKey(
        key = booleanPreferencesKey("auto_fullscreen_live"),
        defaultValue = false,
        type = SettingType.BooleanType
    )
    val VIDEO_QUALITY = SettingKey(
        key = intPreferencesKey("video_quality"),
        defaultValue = 720,
        type = SettingType.IntType
    )
    val CHAT_TIMESTAMPS = SettingKey(
        key = stringPreferencesKey("chat_timestamps"),
        defaultValue = "only_video",
        type = SettingType.StringType,
        validator = { value -> com.lechixy.kick.data.chat.model.ChatTimestampMode.entries.any { it.value == value } }
    )
    val CHAT_FONT_SIZE = SettingKey(
        key = intPreferencesKey("chat_font_size"),
        defaultValue = 15,
        type = SettingType.IntType,
        validator = { it in 10..24 }
    )
    val AUTO_CONNECT_CHAT = SettingKey(
        key = booleanPreferencesKey("auto_connect_chat"),
        defaultValue = true,
        type = SettingType.BooleanType
    )
    val CHAT_MESSAGE_SPACING = SettingKey(
        key = intPreferencesKey("chat_message_spacing"),
        defaultValue = 3,
        type = SettingType.IntType,
        validator = { it in 0..25 }
    )
    val CHAT_WIDTH_FRACTION = SettingKey(
        key = floatPreferencesKey("chat_width_fraction"),
        defaultValue = 0.32f,
        type = SettingType.FloatType,
        validator = { it in 0.18f..0.60f }
    )
    fun chatFontSize(smallestWidthDp: Int) =
        CHAT_FONT_SIZE.copy(defaultValue = if (smallestWidthDp >= 600) 16 else CHAT_FONT_SIZE.defaultValue)

    fun chatMessageSpacing(smallestWidthDp: Int) =
        CHAT_MESSAGE_SPACING.copy(defaultValue = if (smallestWidthDp >= 600) 4 else CHAT_MESSAGE_SPACING.defaultValue)

    fun chatWidth(screenWidthDp: Int): SettingKey<Float> = CHAT_WIDTH_FRACTION.copy(
        defaultValue = if (screenWidthDp < 700) CHAT_WIDTH_FRACTION.defaultValue else
            ((screenWidthDp * .28f).coerceIn(280f, 360f) / screenWidthDp).coerceIn(.18f, .60f),
    )
    val FULLSCREEN_CHAT_ENABLED = SettingKey(
        booleanPreferencesKey("fullscreen_chat_enabled"), false, SettingType.BooleanType
    )
    val FULLSCREEN_CHAT_HORIZONTAL = SettingKey(
        booleanPreferencesKey("fullscreen_chat_horizontal"), false, SettingType.BooleanType
    )
    val FULLSCREEN_CHAT_LANES = SettingKey(
        intPreferencesKey("fullscreen_chat_lanes"), 1, SettingType.IntType,
        validator = { it in 1..4 }
    )
    val FULLSCREEN_CHAT_SCALE = SettingKey(
        intPreferencesKey("fullscreen_chat_scale"), 100, SettingType.IntType,
        validator = { it in 70..150 }
    )
    val FULLSCREEN_CHAT_FONT_SIZE = SettingKey(
        intPreferencesKey("fullscreen_chat_font_size"), CHAT_FONT_SIZE.defaultValue, SettingType.IntType,
        validator = { it in 10..24 }
    )
    val FULLSCREEN_CHAT_SHAPE = SettingKey(
        stringPreferencesKey("fullscreen_chat_shape"), FullscreenChatShape.Rounded.value, SettingType.StringType,
        validator = { value -> FullscreenChatShape.entries.any { it.value == value } }
    )
    val FULLSCREEN_CHAT_OPACITY = SettingKey(
        floatPreferencesKey("fullscreen_chat_opacity"), 0.60f, SettingType.FloatType,
        validator = { it in 0.35f..0.85f }
    )
    val FULLSCREEN_CHAT_BLUR = SettingKey(
        booleanPreferencesKey("fullscreen_chat_blur"), false, SettingType.BooleanType
    )
    val FULLSCREEN_CHAT_BADGES = SettingKey(
        booleanPreferencesKey("fullscreen_chat_badges"), true, SettingType.BooleanType
    )
    val FULLSCREEN_CHAT_POSITION_LOCKED = SettingKey(
        booleanPreferencesKey("fullscreen_chat_position_locked"), false, SettingType.BooleanType
    )
    val FULLSCREEN_CHAT_VERTICAL_POSITION = SettingKey(
        stringPreferencesKey("fullscreen_chat_vertical_position"),
        FullscreenChatPosition.VerticalDefault.encode(),
        SettingType.StringType,
        validator = { FullscreenChatPosition.decode(it) != null }
    )
    val FULLSCREEN_CHAT_HORIZONTAL_POSITION = SettingKey(
        stringPreferencesKey("fullscreen_chat_horizontal_position"),
        FullscreenChatPosition.HorizontalDefault.encode(),
        SettingType.StringType,
        validator = { FullscreenChatPosition.decode(it) != null }
    )
    val MINI_PLAYER_WIDTH_DP = SettingKey(
        key = floatPreferencesKey("mini_player_width_dp"),
        defaultValue = MiniPlayerSize.TABLET_DEFAULT_WIDTH_DP,
        type = SettingType.FloatType,
        validator = { it in MiniPlayerSize.MIN_WIDTH_DP..MiniPlayerSize.MAX_WIDTH_DP }
    )

    fun miniPlayerWidth(smallestScreenWidthDp: Int): SettingKey<Float> =
        MINI_PLAYER_WIDTH_DP.copy(defaultValue = MiniPlayerSize.defaultWidthDp(smallestScreenWidthDp))

    val RECOMMENDED_LIVESTREAM_LANGUAGE = SettingKey(
        key = stringPreferencesKey("recommended_livestream_language"),
        defaultValue = com.lechixy.kick.util.recommendedStreamLanguage(java.util.Locale.getDefault()),
        type = SettingType.StringType,
        defaultProvider = { com.lechixy.kick.util.systemRecommendedStreamLanguage() }
    )
    /*
     * Exportable Keys
     */
    val ALL_KEYS: List<SettingKey<*>> by lazy {
        listOf(
            NAVIGATION_RAIL_EXPANDED,
            APP_LANGUAGE,
            THEME,
            DYNAMIC_COLOR,
            WINTER_SNOW,
            WINTER_SNOW_STYLE,
            WINTER_SNOW_INTENSITY,
            WINTER_SNOW_SPEED,
            WINTER_SNOW_SIZE,
            WINTER_SNOW_SCROLL,
            NAVIGATION_ORDER,
            HOME_SECTION_ORDER,
            TOP_BAR_LOGO_ANIMATION,
            AUTOPLAY,
            LAST_SLEEP_TIMER_MINUTES,
            PIP_ENABLED,
            SYNC_PLAYER_VOLUME_WITH_DEVICE,
            LOW_LATENCY,
            AUTO_FULLSCREEN_LIVE,
            VIDEO_QUALITY,
            RECOMMENDED_LIVESTREAM_LANGUAGE,
            CHAT_TIMESTAMPS,
            CHAT_FONT_SIZE,
            AUTO_CONNECT_CHAT,
            CHAT_MESSAGE_SPACING,
            CHAT_WIDTH_FRACTION,
            FULLSCREEN_CHAT_ENABLED,
            FULLSCREEN_CHAT_HORIZONTAL,
            FULLSCREEN_CHAT_LANES,
            FULLSCREEN_CHAT_SCALE,
            FULLSCREEN_CHAT_FONT_SIZE,
            FULLSCREEN_CHAT_SHAPE,
            FULLSCREEN_CHAT_OPACITY,
            FULLSCREEN_CHAT_BLUR,
            FULLSCREEN_CHAT_BADGES,
            FULLSCREEN_CHAT_POSITION_LOCKED,
            FULLSCREEN_CHAT_VERTICAL_POSITION,
            FULLSCREEN_CHAT_HORIZONTAL_POSITION,
            MINI_PLAYER_WIDTH_DP
        )
    }
}
