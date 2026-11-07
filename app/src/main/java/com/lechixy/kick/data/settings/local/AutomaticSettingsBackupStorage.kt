package com.lechixy.kick.data.settings.local

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.util.AtomicFile
import com.lechixy.kick.data.settings.local.SettingsBackupManager.Companion.FILE_NAME
import com.lechixy.kick.data.settings.local.SettingsBackupManager.Companion.MIME_TYPE
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class AutomaticSettingsBackupStorage(private val context: Context) {
    // Android Auto Backup restores this copy before launch when a system backup is available.
    val restoredCopy = AtomicFile(File(context.filesDir, FILE_NAME))
    private val location = AtomicFile(File(context.noBackupFilesDir, "settings_backup_location"))
    private val restoreDecision = AtomicFile(File(context.noBackupFilesDir, "settings_restore_offered"))
    private val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val resolver = context.contentResolver

    fun readCustomLocation(): Uri? = location.readTextOrNull()?.let(Uri::parse)

    val hasRestoreDecision: Boolean get() = restoreDecision.baseFile.exists()

    fun skippedSettings(): String? = restoreDecision.readTextOrNull()?.takeIf { it.isNotEmpty() }

    fun saveRestoreDecision(skippedSettings: String? = null) = restoreDecision.writeText(skippedSettings.orEmpty())

    fun saveCustomLocation(uri: Uri?) {
        if (uri == null) location.delete() else location.writeText(uri.toString())
    }

    fun saveRestoreCopy(json: String) = restoredCopy.writeText(json)

    fun findSharedBackup(): Uri? = resolver.query(
        downloads,
        arrayOf(MediaColumns._ID),
        "${MediaColumns.RELATIVE_PATH} = ? AND ${MediaColumns.OWNER_PACKAGE_NAME} = ? " +
            "AND (${MediaColumns.DISPLAY_NAME} = ? OR ${MediaColumns.DISPLAY_NAME} LIKE ?)",
        arrayOf(DIRECTORY, context.packageName, FILE_NAME, "${FILE_NAME.substringBeforeLast('.')} (%).json"),
        "${MediaColumns._ID} DESC"
    )?.use { rows ->
        if (rows.moveToFirst()) ContentUris.withAppendedId(downloads, rows.getLong(0)) else null
    }

    fun saveSharedBackup(json: String) {
        val existing = findSharedBackup()
        if (existing != null) {
            resolver.writeBackup(existing, json)
            return
        }

        val uri = resolver.insert(downloads, ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaColumns.RELATIVE_PATH, DIRECTORY)
            put(MediaColumns.IS_PENDING, 1)
        }) ?: throw IOException("Cannot create automatic settings backup")

        try {
            resolver.writeBackup(uri, json)
            val published = resolver.update(uri, ContentValues().apply {
                put(MediaColumns.IS_PENDING, 0)
            }, null, null)
            if (published == 0) throw IOException("Cannot publish automatic settings backup")
        } catch (failure: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw failure
        }
    }

    private fun AtomicFile.writeText(value: String) {
        val output = startWrite()
        try {
            output.write(value.encodeToByteArray())
            finishWrite(output)
        } catch (failure: Exception) {
            failWrite(output)
            throw failure
        }
    }

    private fun AtomicFile.readTextOrNull(): String? = try {
        readFully().decodeToString()
    } catch (_: FileNotFoundException) {
        null
    }

    private companion object {
        val DIRECTORY = "${Environment.DIRECTORY_DOWNLOADS}/Kicker/"
    }
}

internal fun ContentResolver.writeBackup(uri: Uri, json: String) {
    val output = openOutputStream(uri, "wt") ?: throw IOException("Cannot open backup for writing")
    output.bufferedWriter().use { it.write(json) }
}
