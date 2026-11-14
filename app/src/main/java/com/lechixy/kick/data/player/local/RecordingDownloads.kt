package com.lechixy.kick.data.player.local

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.provider.OpenableColumns
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.data.player.model.RecordedPlayback
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val RECORDING_DOWNLOAD_DIRECTORY =
    "${Environment.DIRECTORY_DOWNLOADS}/${if (BuildConfig.DEBUG) "Kicker Test" else "Kicker"}/"
internal const val RECORDING_DOWNLOAD_MIME_TYPE = "video/mp4"

internal data class RecordingDownload(
    val uri: Uri,
    val name: String,
    val size: Long,
    val downloadedAt: Long,
    val metadata: RecordingDownloadMetadata?,
    val thumbnail: File?,
    val linked: Boolean = false,
    val fileName: String = name,
)

@Serializable
internal data class RecordingDownloadMetadata(
    val taskId: String,
    val title: String,
    val channelName: String?,
    val durationMs: Long,
    val publishedAt: Long,
    val channelSlug: String? = null,
    val sizeBytes: Long = 0,
    val channelAvatar: String? = null,
)

@Serializable
internal data class DownloadBackupEntry(
    val fileName: String,
    val size: Long,
    val metadata: RecordingDownloadMetadata,
)

private val metadataJson = Json { ignoreUnknownKeys = true }
private val linkedDownloadsChanges = MutableStateFlow(0)
internal val downloadBackupChanges = MutableStateFlow(0)

private fun Context.restoredDownloadIndex() =
    getSharedPreferences("restored_download_index", Context.MODE_PRIVATE)

private fun Context.readDownloadIndex(): List<DownloadBackupEntry> = runCatching {
    metadataJson.decodeFromString<List<DownloadBackupEntry>>(restoredDownloadIndex().getString("entries", "[]") ?: "[]")
}.getOrDefault(emptyList())

internal fun Context.restoreDownloadIndex(entries: List<DownloadBackupEntry>) = synchronized(downloadBackupChanges) {
    val index = (entries + readDownloadIndex()).distinctBy { it.fileName to it.size }
    restoredDownloadIndex().edit().putString("entries", metadataJson.encodeToString(index)).apply()
    downloadBackupChanges.update { it + 1 }
}

internal fun Context.forgetDownloadIndexEntry(recording: RecordingDownload) = synchronized(downloadBackupChanges) {
    val index = readDownloadIndex().filterNot { it.fileName == recording.fileName && it.size == recording.size }
    restoredDownloadIndex().edit().putString("entries", metadataJson.encodeToString(index)).apply()
    downloadBackupChanges.update { it + 1 }
}

internal fun Context.downloadBackupIndex(): List<DownloadBackupEntry> {
    val owned = queryRecordingDownloads(this).mapNotNull { item ->
        item.metadata?.let { DownloadBackupEntry(item.fileName, item.size, it) }
    }
    return (owned + readDownloadIndex()).distinctBy { it.fileName to it.size }
}

internal fun Context.linkRecordingDownloads(uris: List<Uri>): Int {
    val granted = uris.distinct().mapNotNull { uri -> runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uri.toString()
    }.getOrNull() }
    if (granted.isNotEmpty()) synchronized(linkedDownloadsChanges) {
        val prefs = getSharedPreferences("linked_recording_downloads", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("uris", prefs.getStringSet("uris", emptySet()).orEmpty() + granted).apply()
        linkedDownloadsChanges.update { it + 1 }
    }
    return granted.size
}

internal fun Context.unlinkRecordingDownload(uri: Uri) = synchronized(linkedDownloadsChanges) {
    val prefs = getSharedPreferences("linked_recording_downloads", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("uris", prefs.getStringSet("uris", emptySet()).orEmpty() - uri.toString()).apply()
    runCatching { contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    linkedDownloadsChanges.update { it + 1 }
}

private fun Context.linkedRecordingDownloads(): List<RecordingDownload> {
    val restored = readDownloadIndex().associateBy { it.fileName to it.size }
    return getSharedPreferences("linked_recording_downloads", Context.MODE_PRIVATE)
        .getStringSet("uris", emptySet()).orEmpty().mapNotNull { value ->
            val uri = Uri.parse(value)
            runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { row ->
                        if (!row.moveToFirst()) return@use null
                        val name = row.getString(0) ?: return@use null
                        val size = row.getLong(1)
                        val metadata = restored[name to size]?.metadata
                        RecordingDownload(uri, metadata?.title?.takeIf(String::isNotBlank) ?: name.removeSuffix(".mp4"),
                            size, metadata?.publishedAt ?: 0L, metadata, null, linked = true, fileName = name)
                    }
            }.getOrNull()
        }
}

private fun Context.downloadAsset(uri: Uri, extension: String): File =
    File(File(noBackupFilesDir, "recording-downloads"), "${ContentUris.parseId(uri)}.$extension")

internal fun Context.recordingThumbnail(uri: Uri): File = downloadAsset(uri, "jpg")

internal fun Context.saveRecordingMetadata(uri: Uri, taskId: String, recording: RecordedPlayback, sizeBytes: Long) {
    val metadata = RecordingDownloadMetadata(taskId, recording.title, recording.channelName,
        recording.durationMs, System.currentTimeMillis(), recording.channelSlug, sizeBytes, recording.channelAvatar)
    val file = downloadAsset(uri, "json")
    file.parentFile?.mkdirs()
    file.writeText(metadataJson.encodeToString(metadata))
}

internal fun Context.removeRecordingMetadata(uri: Uri) {
    downloadAsset(uri, "json").delete()
    recordingThumbnail(uri).delete()
}

/** The single download worker calls this before starting any new publication. */
internal fun Context.cleanInterruptedRecordingDownloads() {
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val filters = Bundle().apply {
        putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
        putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
            "${MediaColumns.RELATIVE_PATH} = ? AND ${MediaColumns.MIME_TYPE} = ? AND ${MediaColumns.OWNER_PACKAGE_NAME} = ?")
        putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
            arrayOf(RECORDING_DOWNLOAD_DIRECTORY, RECORDING_DOWNLOAD_MIME_TYPE, packageName))
    }
    val pending = contentResolver.query(collection, arrayOf(MediaColumns._ID), filters, null)?.use { rows ->
        buildList {
            while (rows.moveToNext()) add(ContentUris.withAppendedId(collection, rows.getLong(0)))
        }
    }.orEmpty()
    pending.forEach { uri ->
        contentResolver.delete(uri, null, null)
        removeRecordingMetadata(uri)
    }
}

internal fun observeRecordingDownloads(context: Context) = combine(callbackFlow {
    val resolver = context.contentResolver
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    resolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, observer)
    trySend(Unit)
    awaitClose { resolver.unregisterContentObserver(observer) }
}.conflate(), linkedDownloadsChanges, downloadBackupChanges) { _, _, _ ->
    runCatching {
        val owned = queryRecordingDownloads(context)
        owned + context.linkedRecordingDownloads()
    }
}.catch { error ->
    if (error !is SecurityException) throw error
    emit(Result.failure(error))
}.flowOn(Dispatchers.IO)

internal fun queryRecordingDownloads(context: Context): List<RecordingDownload> {
    val restored = context.readDownloadIndex().associateBy { it.fileName to it.size }
    val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
    val rows = checkNotNull(context.contentResolver.query(
        collection,
        arrayOf(MediaColumns._ID, MediaColumns.DISPLAY_NAME, MediaColumns.SIZE, MediaColumns.DATE_ADDED),
        "${MediaColumns.RELATIVE_PATH} = ? AND ${MediaColumns.MIME_TYPE} = ? " +
            "AND ${MediaColumns.IS_PENDING} = 0 AND ${MediaColumns.OWNER_PACKAGE_NAME} = ?",
        arrayOf(RECORDING_DOWNLOAD_DIRECTORY, RECORDING_DOWNLOAD_MIME_TYPE, context.packageName),
        "${MediaColumns.DATE_ADDED} DESC, ${MediaColumns._ID} DESC"
    ))
    return rows.use {
        buildList {
            while (rows.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, rows.getLong(0))
                val fileName = rows.getString(1)
                val size = rows.getLong(2)
                val metadata = context.downloadAsset(uri, "json").takeIf(File::isFile)?.let { file ->
                    runCatching { metadataJson.decodeFromString<RecordingDownloadMetadata>(file.readText()) }.getOrNull()
                } ?: restored[fileName to size]?.metadata
                add(RecordingDownload(
                    uri = uri,
                    name = metadata?.title?.takeIf(String::isNotBlank) ?: fileName.removeSuffix(".mp4"),
                    size = size.takeIf { it > 0 } ?: metadata?.sizeBytes ?: 0,
                    downloadedAt = metadata?.publishedAt ?: rows.getLong(3) * 1000,
                    metadata = metadata,
                    thumbnail = context.recordingThumbnail(uri).takeIf(File::isFile),
                    fileName = fileName,
                ))
            }
        }
    }
}
