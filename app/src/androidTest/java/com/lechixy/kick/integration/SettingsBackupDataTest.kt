package com.lechixy.kick.integration

import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.data.favorites.local.FavoriteUsersStore
import com.lechixy.kick.data.settings.local.SettingsBackupManager
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupDataTest {
    @Test fun startupFilePickerRestoresStableTestAndLegacyBackups() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(target.cacheDir, "startup-backup-test-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) {
            override fun getNoBackupFilesDir(): File = directory
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "test.preferences_pb")
        })
        val manager = SettingsBackupManager(context, repository)
        val originalPath = manager.defaultPath
        val files = listOf(
            """{"version":1,"edition":"stable","settings":{"chat_font_size":18}}""",
            """{"version":1,"edition":"test","settings":{"chat_font_size":19}}""",
            """{"chat_font_size":20}""",
        )
        try {
            files.forEachIndexed { index, json ->
                val file = File(directory, "backup-$index.json").apply { writeText(json) }
                manager.restorePickedBackup(Uri.fromFile(file))
                assertEquals(18 + index, repository.get(SettingsKeys.CHAT_FONT_SIZE).first())
            }
            assertEquals(originalPath, manager.defaultPath)
            assertEquals(null, manager.customLocation.value)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test fun backupRestoresFavoritesAndKeepsUnsetChatDefaultsAdaptive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repository = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "test.preferences_pb")
        })
        val manager = SettingsBackupManager(context, repository)
        val favorite = "backup-test-${System.nanoTime()}"
        val fixture = File(directory, "fixture.json").apply {
            writeText("""
                {"version":1,"settings":{
                  "chat_font_size":19,
                  "fullscreen_chat_opacity":0.0,
                  "player_ambient_mode":"live",
                  "player_ambient_live_fps":60,
                  "player_ambient_intensity":125,
                  "player_ambient_spread":80,
                  "player_ambient_fullscreen":false
                },"playback":{
                  "favorites":[{"username":"$favorite"}]
                }}
            """.trimIndent())
        }
        try {
            manager.importFrom(Uri.fromFile(fixture))
            assertEquals(19, repository.get(SettingsKeys.chatFontSize(1024)).first())
            assertEquals("live", repository.get(SettingsKeys.AMBIENT_MODE).first())
            assertEquals(60, repository.get(SettingsKeys.AMBIENT_LIVE_FPS).first())
            assertEquals(125, repository.get(SettingsKeys.AMBIENT_INTENSITY).first())
            assertEquals(80, repository.get(SettingsKeys.AMBIENT_SPREAD).first())
            assertFalse(repository.get(SettingsKeys.AMBIENT_FULLSCREEN).first())
            assertEquals(0f, repository.get(SettingsKeys.FULLSCREEN_CHAT_OPACITY).first())
            assertTrue(FavoriteUsersStore.favorites.value.contains(null, favorite))
            val export = File(directory, manager.fileName).apply { createNewFile() }
            manager.exportTo(Uri.fromFile(export))
            val backup = Json.parseToJsonElement(export.readText()).jsonObject
            assertFalse("chat_width_fraction" in backup.getValue("settings").jsonObject)
            assertEquals("\"live\"", backup.getValue("settings").jsonObject.getValue("player_ambient_mode").toString())
            assertEquals("60", backup.getValue("settings").jsonObject.getValue("player_ambient_live_fps").toString())
            assertEquals("125", backup.getValue("settings").jsonObject.getValue("player_ambient_intensity").toString())
            assertEquals("80", backup.getValue("settings").jsonObject.getValue("player_ambient_spread").toString())
            assertEquals("false", backup.getValue("settings").jsonObject.getValue("player_ambient_fullscreen").toString())
            assertEquals("0.0", backup.getValue("settings").jsonObject.getValue("fullscreen_chat_opacity").toString())
            assertTrue(backup.getValue("playback").jsonObject.getValue("favorites").toString().contains(favorite))
        } finally {
            if (FavoriteUsersStore.favorites.value.contains(null, favorite)) FavoriteUsersStore.toggle(null, favorite)
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
