package com.lechixy.kick.data.settings.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.model.resolvedDefault
import com.lechixy.kick.util.preferredStreamLanguageCode

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun <T> get(settingKey: SettingKey<T>): Flow<T> {
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[settingKey.key] ?: settingKey.resolvedDefault
            }
            .distinctUntilChanged()
    }

    suspend fun <T> set(settingKey: SettingKey<T>, value: T) {
        dataStore.edit { preferences ->
            preferences.setSetting(settingKey, value)
        }
    }

    internal suspend fun editSettings(update: MutablePreferences.() -> Unit): Preferences =
        dataStore.edit { it.update() }

    internal suspend fun prepareWinterWelcome(today: LocalDate): WinterWelcome? {
        var welcome: WinterWelcome? = null
        dataStore.edit { preferences ->
            val snow = SettingsKeys.WINTER_SNOW.key
            val season = winterSeasonStartYear(today)
            if (season != null && preferences[winterWelcomeSeason] != season) {
                preferences[winterWelcomeSeason] = season
                preferences[snow] = true
                welcome = WinterWelcome.Seasonal
            } else if (season == null && preferences[snow] == true) {
                preferences[snow] = false
                if (preferences[winterWelcomeOffSeason] != today.year) {
                    preferences[winterWelcomeOffSeason] = today.year
                    welcome = WinterWelcome.OffSeason
                }
            }
        }
        return welcome
    }

    /** Persist the launch marker atomically before any release network request. */
    suspend fun recordStartupVersion(version: String): Boolean {
        val keys = com.lechixy.kick.data.settings.model.SettingsKeys
        fun normalize(value: String) = value.trim().trimStart('v', 'V')
        var show = false
        dataStore.edit { preferences ->
            val previous = preferences[keys.LAST_USED_VERSION.key].orEmpty()
            show = preferences[keys.IS_FIRST_RUN.key] != false || previous.isBlank() ||
                !normalize(previous).equals(normalize(version), ignoreCase = true)
            preferences[keys.IS_FIRST_RUN.key] = false
            preferences[keys.LAST_USED_VERSION.key] = version
        }
        return show
    }

    suspend fun <T> reset(settingKey: SettingKey<T>) {
        dataStore.edit { preferences ->
            preferences.remove(settingKey.key)
        }
    }

    suspend fun resetAll() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    internal val snapshots: Flow<Preferences> get() = dataStore.data

    internal suspend fun snapshot(): Preferences = snapshots.first()
}

internal enum class WinterWelcome { Seasonal, OffSeason }

internal fun winterSeasonStartYear(today: LocalDate): Int? = when (today.monthValue) {
    12 -> today.year
    1, 2 -> today.year - 1
    else -> null
}

private val winterWelcomeSeason = intPreferencesKey("winter_welcome_season")
private val winterWelcomeOffSeason = intPreferencesKey("winter_welcome_off_season")

internal fun <T> MutablePreferences.setSetting(setting: SettingKey<T>, value: T): Boolean {
    if (setting.key == SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE.key && value is String) {
        val code = preferredStreamLanguageCode(value) ?: return false
        this[SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE.key] = code
        return true
    }
    if (setting.validator?.invoke(value) == false) return false
    this[setting.key] = value
    return true
}
