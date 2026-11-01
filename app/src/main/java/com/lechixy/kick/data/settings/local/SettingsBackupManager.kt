package com.lechixy.kick.data.settings.local

import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.SettingType
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository

@Singleton
class SettingsBackupManager @Inject constructor(
    private val repository: SettingsRepository
) {
    suspend fun exportToJson(): String {
        val root = JSONObject()
        val currentPrefs = repository.allPreferencesFlow.first()

        for (setting in SettingsKeys.ALL_KEYS) {
            if (!setting.isExportable) continue
            val keyName = setting.key.name
            if (currentPrefs.contains(setting.key)) {
                root.put(keyName, currentPrefs[setting.key])
            }
        }
        return root.toString(2)
    }

    suspend fun importFromJson(jsonString: String): Result<Unit> = runCatching {
        val root = JSONObject(jsonString)

        for (setting in SettingsKeys.ALL_KEYS) {
            val keyName = setting.key.name
            if (!root.has(keyName)) continue

            when (setting.type) {
                is SettingType.BooleanType -> {
                    @Suppress("UNCHECKED_CAST")
                    val key = setting as SettingKey<Boolean>
                    val value = root.getBoolean(keyName)
                    repository.set(key, value)
                }
                is SettingType.StringType -> {
                    @Suppress("UNCHECKED_CAST")
                    val key = setting as SettingKey<String>
                    val value = root.getString(keyName)
                    repository.set(key, value)
                }
                is SettingType.IntType -> {
                    @Suppress("UNCHECKED_CAST")
                    val key = setting as SettingKey<Int>
                    val value = root.getInt(keyName)
                    repository.set(key, value)
                }
                is SettingType.FloatType -> {
                    @Suppress("UNCHECKED_CAST")
                    val key = setting as SettingKey<Float>
                    val value = root.getDouble(keyName).toFloat()
                    repository.set(key, value)
                }
            }
        }
    }
}