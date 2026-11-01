package com.lechixy.kick.data.settings.repository

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository bulunamadı! CompositionLocalProvider ile sağlandığından emin olun.")
}