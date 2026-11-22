package com.lechixy.kick.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.components.settings.AmbientModeSettings
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.LocalSettingsRepository
import com.lechixy.kick.data.settings.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AmbientModeSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun resetRestoresBothLightingLevelsAndKeepsPanelAndBatteryPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "ambient-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        try {
            runBlocking {
                settings.set(SettingsKeys.AMBIENT_INTENSITY, 150)
                settings.set(SettingsKeys.AMBIENT_SPREAD, 170)
                settings.set(SettingsKeys.AMBIENT_CHAT_OPACITY, 60)
                settings.set(SettingsKeys.AMBIENT_NAVIGATION_OPACITY, 20)
                settings.set(SettingsKeys.AMBIENT_BATTERY_SAVER, false)
            }
            compose.setContent {
                CompositionLocalProvider(LocalSettingsRepository provides settings) {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) { AmbientModeSettings() }
                    }
                }
            }
            compose.waitUntil(5_000) {
                runBlocking {
                    settings.get(SettingsKeys.AMBIENT_INTENSITY).first() == 100 &&
                        settings.get(SettingsKeys.AMBIENT_SPREAD).first() == 100
                }
            }
            runBlocking {
                assertEquals(60, settings.get(SettingsKeys.AMBIENT_CHAT_OPACITY).first())
                assertEquals(20, settings.get(SettingsKeys.AMBIENT_NAVIGATION_OPACITY).first())
                assertEquals(false, settings.get(SettingsKeys.AMBIENT_BATTERY_SAVER).first())
            }
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
