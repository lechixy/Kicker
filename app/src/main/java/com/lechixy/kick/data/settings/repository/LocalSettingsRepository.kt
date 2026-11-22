package com.lechixy.kick.data.settings.repository

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import com.lechixy.kick.data.settings.model.SettingsKeys

internal val LocalBlurEffectsEnabled = compositionLocalOf { SettingsKeys.BLUR_EFFECTS.defaultValue }

val LocalSettingsRepository = staticCompositionLocalOf<SettingsRepository> {
    error("SettingsRepository bulunamadı! CompositionLocalProvider ile sağlandığından emin olun.")
}
