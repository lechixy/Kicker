package com.lechixy.kick.data.settings.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.lechixy.kick.data.settings.model.SettingKey
import com.lechixy.kick.data.settings.model.SettingType
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.data.settings.repository.setSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: SettingsRepository
) {
    private val exportableSettings = SettingsKeys.ALL_KEYS.filter { it.isExportable }
    private val storage by lazy { AutomaticSettingsBackupStorage(context) }
    private val startupBackup = CompletableDeferred<Uri?>()
    private val restoreHandled = CompletableDeferred<Unit>()
    private val writeLock = Mutex()
    private val selectedLocation = MutableStateFlow<Uri?>(null)
    val customLocation = selectedLocation.asStateFlow()

    internal suspend fun runAutomaticBackup() {
        selectedLocation.value = runCatching { storage.readCustomLocation() }
            .onFailure { Log.w(TAG, "Cannot read automatic backup location", it) }.getOrNull()
        val oldBackup = runCatching {
            when {
                storage.hasRestoreDecision -> null
                storage.restoredCopy.baseFile.isFile -> Uri.fromFile(storage.restoredCopy.baseFile)
                else -> storage.findSharedBackup()
            }
        }.onFailure { Log.w(TAG, "Cannot locate previous settings backup", it) }.getOrNull()
        if (oldBackup == null && !storage.hasRestoreDecision) {
            runCatching { finishStartupRestore() }
                .onFailure { Log.w(TAG, "Cannot mark settings restore as handled", it) }
        }
        startupBackup.complete(oldBackup)
        // Keep the previous file intact until the user restores it or skips the offer.
        if (oldBackup != null) restoreHandled.await()

        // Skipping an import keeps the old backup until an exportable preference actually changes.
        var skippedSettings = runCatching { storage.skippedSettings() }
            .onFailure { Log.w(TAG, "Cannot read the settings restore decision", it) }.getOrNull()
        var lastSaved = skippedSettings
        repository.snapshots.retryWhen { failure, _ ->
            if (failure is IOException) delay(1_000)
            failure is IOException
        }.collect {
            writeLock.withLock {
                val json = exportToJson(repository.snapshot())
                if (json == lastSaved) return@withLock
                runCatching {
                    if (skippedSettings != null) {
                        storage.saveRestoreDecision()
                        skippedSettings = null
                    }
                    storage.saveRestoreCopy(json)
                    saveToLocation(selectedLocation.value, json)
                    lastSaved = json
                }.onFailure { Log.w(TAG, "Cannot save automatic settings backup", it) }
            }
        }
    }

    suspend fun shouldOfferRestore(): Boolean = startupBackup.await() != null && !restoreHandled.isCompleted

    suspend fun restoreDefaultBackup(): String? {
        val uri = checkNotNull(startupBackup.await())
        val language = importFrom(uri)
        finishStartupRestore()
        return language
    }

    suspend fun finishStartupRestore(preserveBackup: Boolean = false) = withContext(Dispatchers.IO) {
        val skippedSettings = if (preserveBackup) exportToJson(repository.snapshot()) else null
        storage.saveRestoreDecision(skippedSettings)
        restoreHandled.complete(Unit)
        Unit
    }

    suspend fun setAutomaticLocation(uri: Uri?) = withContext(Dispatchers.IO) {
        writeLock.withLock {
            val previous = selectedLocation.value
            if (previous == uri) return@withLock
            val resolver = context.contentResolver
            val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            uri?.let { resolver.takePersistableUriPermission(it, write) }
            try {
                saveToLocation(uri, exportToJson(repository.snapshot()))
                storage.saveCustomLocation(uri)
                selectedLocation.value = uri
            } catch (failure: Exception) {
                uri?.let { runCatching { resolver.releasePersistableUriPermission(it, write) } }
                throw failure
            }
            previous?.let { runCatching { resolver.releasePersistableUriPermission(it, write) } }
            Unit
        }
    }

    private fun saveToLocation(uri: Uri?, json: String) {
        if (uri == null) storage.saveSharedBackup(json) else context.contentResolver.writeBackup(uri, json)
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.writeBackup(uri, exportToJson(repository.snapshot()))
    }

    suspend fun importFrom(uri: Uri): String? = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open backup for reading")
        val json = input.bufferedReader().use { reader ->
            val buffer = CharArray(MAX_BACKUP_CHARS + 1)
            var length = 0
            while (length < buffer.size) {
                val read = reader.read(buffer, length, buffer.size - length)
                if (read == -1) break
                length += read
            }
            require(length <= MAX_BACKUP_CHARS) { "Settings backup is too large" }
            buffer.concatToString(0, length)
        }
        importFromJson(json)
    }

    private fun exportToJson(current: Preferences): String {
        val values = exportableSettings
            // An unset mini player width should use the destination device's default.
            .filter { it.key != SettingsKeys.MINI_PLAYER_WIDTH_DP.key || SettingsKeys.MINI_PLAYER_WIDTH_DP.key in current }
            .associate { it.key.name to it.encode(current) }
        return backupJson.encodeToString(
            JsonObject(mapOf("version" to JsonPrimitive(FORMAT_VERSION), "settings" to JsonObject(values)))
        )
    }

    private suspend fun importFromJson(json: String): String? {
        val root = Json.parseToJsonElement(json.removePrefix("\uFEFF")) as? JsonObject
            ?: throw IllegalArgumentException("Expected a settings object")
        val values = if ("version" in root || "settings" in root) {
            val version = root["version"] as? JsonPrimitive
            if (version == null || version.isString || version.intOrNull != FORMAT_VERSION) {
                throw UnsupportedVersion()
            }
            root["settings"] as? JsonObject ?: throw IllegalArgumentException("Missing settings")
        } else {
            // Older exports used a flat map of settings.
            root
        }
        val included = exportableSettings.filter { it.key.name in values }
        require(included.isNotEmpty()) { "No recognized settings" }
        val updated = repository.editSettings {
            for (setting in included) {
                val value = values[setting.key.name] as? JsonPrimitive
                    ?: throw IllegalArgumentException("Invalid value for ${setting.key.name}")
                writeImportedSetting(setting, value)
            }
        }
        return updated[SettingsKeys.APP_LANGUAGE.key].takeIf { SettingsKeys.APP_LANGUAGE in included }
    }

    private fun <T> SettingKey<T>.encode(current: Preferences): JsonPrimitive =
        when (val value = current[key] ?: defaultProvider?.invoke() ?: defaultValue) {
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> error("Unsupported setting ${key.name}")
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> MutablePreferences.writeImportedSetting(setting: SettingKey<T>, value: JsonPrimitive) {
        val decoded = when (setting.type) {
            SettingType.BooleanType -> value.takeUnless { it.isString }?.booleanOrNull
            SettingType.StringType -> value.takeIf { it.isString }?.content
            SettingType.IntType -> value.takeUnless { it.isString }?.intOrNull
            SettingType.FloatType -> value.takeUnless { it.isString }?.floatOrNull?.takeIf { it.isFinite() }
        }
        require(decoded != null && setSetting(setting, decoded as T)) { "Invalid value for ${setting.key.name}" }
    }

    class UnsupportedVersion : IllegalArgumentException("Unsupported settings backup version")

    companion object {
        const val FILE_NAME = "kicker_settings.json"
        const val MIME_TYPE = "application/json"
        private const val TAG = "SettingsBackup"
        private const val FORMAT_VERSION = 1
        private const val MAX_BACKUP_CHARS = 64 * 1024
        private val backupJson = Json { prettyPrint = true }
    }
}
