package com.lechixy.kick.data.settings.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

import com.lechixy.kick.data.settings.model.SettingKey

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
                preferences[settingKey.key] ?: settingKey.defaultProvider?.invoke() ?: settingKey.defaultValue
            }
            .distinctUntilChanged()
    }

    suspend fun <T> set(settingKey: SettingKey<T>, value: T) {
        if (settingKey.key == com.lechixy.kick.data.settings.model.SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE.key && value is String) {
            val code = com.lechixy.kick.util.kickLanguageCode(value) ?: return
            dataStore.edit { it[com.lechixy.kick.data.settings.model.SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE.key] = code }
            return
        }
        if (settingKey.validator?.invoke(value) == false) return
        dataStore.edit { preferences ->
            preferences[settingKey.key] = value
        }
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

    val allPreferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
}