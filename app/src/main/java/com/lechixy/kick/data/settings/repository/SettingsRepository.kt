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
                preferences[settingKey.key] ?: settingKey.defaultValue
            }
            .distinctUntilChanged()
    }

    suspend fun <T> set(settingKey: SettingKey<T>, value: T) {
        if (settingKey.validator?.invoke(value) == false) return
        dataStore.edit { preferences ->
            preferences[settingKey.key] = value
        }
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