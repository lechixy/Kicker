package com.lechixy.kick.data.player.local

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import com.lechixy.kick.data.player.model.RecordedPlayback
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val RECORDING_DOWNLOAD_DIRECTORY = "${Environment.DIRECTORY_DOWNLOADS}/Kicker/"
internal const val RECORDING_DOWNLOAD_MIME_TYPE = "video/mp4"

internal data class RecordingDownload(
    val uri: Uri,
    val name: String,
    val size: Long,
    val downloadedAt: Long,
    val metadata: RecordingDownloadMetadata?,
    val thumbnail: File?,
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
)

private val metadataJson = Json { ignoreUnknownKeys = true }

private fun Context.downloadAsset(uri: Uri, extension: String): File =
    File(File(noBackupFilesDir, "recording-downloads"), "${ContentUris.parseId(uri)}.$extension")

internal fun Context.recordingThumbnail(uri: Uri): File = downloadAsset(uri, "jpg")

internal fun Context.saveRecordingMetadata(uri: Uri, taskId: String, recording: RecordedPlayback, sizeBytes: Long) {
    val metadata = RecordingDownloadMetadata(taskId, recording.title, recording.channelName,
        recording.durationMs, System.currentTimeMillis(), recording.channelSlug, sizeBytes)
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

internal fun observeRecordingDownloads(context: Context) = callbackFlow {
    val resolver = context.contentResolver
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    resolver.registerContentObserver(MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, observer)
    trySend(Unit)
    awaitClose { resolver.unregisterContentObserver(observer) }
}.conflate().map {
    runCatching { queryRecordingDownloads(context) }
}.catch { error ->
    if (error !is SecurityException) throw error
    emit(Result.failure(error))
}.flowOn(Dispatchers.IO)

internal fun queryRecordingDownloads(context: Context): List<RecordingDownload> {
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
                val metadata = context.downloadAsset(uri, "json").takeIf(File::isFile)?.let { file ->
                    runCatching { metadataJson.decodeFromString<RecordingDownloadMetadata>(file.readText()) }.getOrNull()
                }
                add(RecordingDownload(
                    uri = uri,
                    name = metadata?.title?.takeIf(String::isNotBlank) ?: rows.getString(1).removeSuffix(".mp4"),
                    size = rows.getLong(2).takeIf { it > 0 } ?: metadata?.sizeBytes ?: 0,
                    downloadedAt = metadata?.publishedAt ?: rows.getLong(3) * 1000,
                    metadata = metadata,
                    thumbnail = context.recordingThumbnail(uri).takeIf(File::isFile),
                ))
            }
        }
    }
}
