package com.lechixy.kick.data.settings.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.lechixy.kick.data.settings.model.SettingsKeys
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinterWelcomeTest {
    @Test
    fun winterStartsInDecemberAndRespectsOptOutThroughFebruary() = runBlocking {
        val settings = SettingsRepository(object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        })
        assertNull(settings.prepareWinterWelcome(LocalDate.of(2026, 11, 30)))
        assertEquals(WinterWelcome.Seasonal, settings.prepareWinterWelcome(LocalDate.of(2026, 12, 1)))
        assertTrue(settings.get(SettingsKeys.WINTER_SNOW).first())

        settings.set(SettingsKeys.WINTER_SNOW, false)
        assertNull(settings.prepareWinterWelcome(LocalDate.of(2027, 1, 1)))
        assertNull(settings.prepareWinterWelcome(LocalDate.of(2027, 2, 28)))
        assertFalse(settings.get(SettingsKeys.WINTER_SNOW).first())
        assertNull(settings.prepareWinterWelcome(LocalDate.of(2027, 3, 1)))

        settings.set(SettingsKeys.WINTER_SNOW, true)
        assertEquals(WinterWelcome.OffSeason, settings.prepareWinterWelcome(LocalDate.of(2027, 3, 1)))
        assertFalse(settings.get(SettingsKeys.WINTER_SNOW).first())
        settings.set(SettingsKeys.WINTER_SNOW, true)
        assertNull(settings.prepareWinterWelcome(LocalDate.of(2027, 11, 30)))
        assertFalse(settings.get(SettingsKeys.WINTER_SNOW).first())
        assertEquals(WinterWelcome.Seasonal, settings.prepareWinterWelcome(LocalDate.of(2027, 12, 1)))
    }
}
