package com.lechixy.kick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.lechixy.kick.MainActivity
import com.lechixy.kick.R
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.local.RECORDING_DOWNLOAD_DIRECTORY
import com.lechixy.kick.data.player.local.RECORDING_DOWNLOAD_MIME_TYPE
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.local.RecordingDownloadProgress
import com.lechixy.kick.data.player.local.RecordingDownloadQueue
import com.lechixy.kick.data.player.local.RecordingDownloadTask
import com.lechixy.kick.data.player.local.cleanInterruptedRecordingDownloads
import com.lechixy.kick.data.player.local.queryRecordingDownloads
import com.lechixy.kick.data.player.local.downloadBackupChanges
import kotlinx.coroutines.flow.update
import com.lechixy.kick.data.player.local.recordingThumbnail
import com.lechixy.kick.data.player.local.removeRecordingMetadata
import com.lechixy.kick.data.player.local.saveRecordingMetadata
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@androidx.annotation.OptIn(UnstableApi::class)
class RecordingDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var worker: Job? = null
    private var download: Job? = null
    private val queue = RecordingDownloadQueue

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_TASK_ID)
        when (intent?.action) {
            ACTION_CANCEL -> {
                if (id != null) {
                    queue.remove(id)
                    if (queue.progress.value?.id == id) download?.cancel() else publishProgress()
                }
                if (worker == null) stopSelf(startId)
                return START_STICKY
            }
            ACTION_RETRY -> if (id != null) queue.retry(id)
            ACTION_ENQUEUE -> {
                val recording = intent.getStringExtra(EXTRA_RECORDING)?.let {
                    runCatching { Json.decodeFromString<RecordedPlayback>(it) }.getOrNull()
                }
                if (recording != null) {
                    val added = queue.enqueue(recording)
                    toast(when {
                        !added -> R.string.recording_download_already_queued
                        worker != null -> R.string.recording_download_queued
                        else -> R.string.recording_download_started
                    })
                }
            }
        }
        val next = queue.tasks.value.firstOrNull { !it.failed }
        if (next == null) {
            if (worker == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (worker != null) {
            publishProgress()
            return START_STICKY
        }
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_download_channel), NotificationManager.IMPORTANCE_LOW)
        )
        if (runCatching { startForeground(PROGRESS_NOTIFICATION, progressNotification(next, null)) }.isFailure) {
            toast(R.string.recording_download_failed)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        worker = scope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(Dispatchers.IO) {
                    runCatching { cleanInterruptedRecordingDownloads() }.onFailure {
                        Log.w(TAG, "Could not clean interrupted downloads (${it.javaClass.simpleName})")
                    }
                }
                while (isActive) {
                    val task = queue.tasks.value.firstOrNull { !it.failed } ?: break
                    setProgress(RecordingDownloadProgress(task.id))
                    coroutineScope {
                        val item = launch(start = CoroutineStart.LAZY) { download(task) }
                        download = item
                        item.start()
                        item.join()
                        download = null
                    }
                }
            } finally {
                download = null
                worker = null
                queue.updateProgress(null)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        worker?.start()
        return START_STICKY
    }

    private suspend fun download(task: RecordingDownloadTask) {
        var tempFile: File? = null
        try {
            // A process can stop after publication but before the queue update is persisted.
            val published = withContext(Dispatchers.IO) {
                queryRecordingDownloads(this@RecordingDownloadService).any { it.metadata?.taskId == task.id }
            }
            if (!published) {
                val resolved = resolveRecording(task.recording)
                val source = Uri.parse(checkNotNull(resolved.directUrl))
                require(source.scheme == "https")
                val file = withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "recording-download")
                    directory.mkdirs()
                    directory.listFiles()?.forEach { it.delete() }
                    File.createTempFile("recording-", ".mp4", directory)
                }
                tempFile = file
                export(source, file, task.id)
                val fileSize = withContext(Dispatchers.IO) { file.length() }
                setProgress(RecordingDownloadProgress(task.id, percent = 100, saving = true, writtenBytes = fileSize))
                saveToDownloads(file, task.id, resolved.copy(title = resolved.title.ifBlank { resolved.id }))
            }
            queue.remove(task.id)
            showResult(task, R.string.recording_download_complete, success = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Download failed (${error.javaClass.simpleName})")
            if (queue.tasks.value.any { it.id == task.id }) {
                queue.fail(task.id)
                showResult(task, R.string.recording_download_failed, success = false)
            }
        } finally {
            tempFile?.delete()
        }
    }

    private suspend fun resolveRecording(recording: RecordedPlayback): RecordedPlayback {
        if (recording.id.startsWith("clip_")) {
            try {
                val clip = KickNetwork.api.getClipDetail(recording.id).data.toRecording()
                if (!clip.directUrl.isNullOrBlank()) return clip.copy(channelAvatar = clip.channelAvatar ?: recording.channelAvatar)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Older clips may only be available through the original playback endpoint.
            }
        }
        return KickRepository(KickNetwork.api).resolveRecording(recording)
    }

    private fun setProgress(progress: RecordingDownloadProgress) {
        val previous = queue.progress.value
        queue.updateProgress(progress)
        if (previous?.id != progress.id || previous.percent != progress.percent || previous.saving != progress.saving) {
            publishProgress()
        }
    }

    private fun publishProgress() {
        val progress = queue.progress.value ?: return
        val task = queue.tasks.value.firstOrNull { it.id == progress.id } ?: return
        notifications.notify(PROGRESS_NOTIFICATION, progressNotification(task, progress))
    }

    private suspend fun export(source: Uri, file: File, id: String) = coroutineScope {
        val completed = CompletableDeferred<Unit>()
        val transformer = Transformer.Builder(this@RecordingDownloadService)
            .setAssetLoaderFactory(
                ExoPlayerAssetLoader.Factory(
                    this@RecordingDownloadService,
                    DefaultDecoderFactory.Builder(this@RecordingDownloadService).build(),
                    Clock.DEFAULT,
                    DefaultMediaSourceFactory(playbackHttpDataSourceFactory())
                )
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    completed.complete(Unit)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    completed.completeExceptionally(exportException)
                }
            })
            .build()
        transformer.start(MediaItem.fromUri(source), file.absolutePath)
        val progress = launch {
            val holder = ProgressHolder()
            while (true) {
                delay(1_000)
                val percent = if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    holder.progress.coerceIn(0, 100)
                } else null
                val writtenBytes = withContext(Dispatchers.IO) { file.length() }
                setProgress(RecordingDownloadProgress(id, percent, writtenBytes = writtenBytes))
            }
        }
        try {
            completed.await()
        } finally {
            progress.cancel()
            if (!completed.isCompleted) transformer.cancel()
        }
    }

    private suspend fun saveToDownloads(file: File, id: String, recording: RecordedPlayback) = withContext(Dispatchers.IO) {
        check(file.length() > 0)
        val safeTitle = recording.title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).trim(' ', '.')
            .ifBlank { getString(R.string.recording_download_fallback_name) }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$safeTitle.mp4")
            put(MediaStore.Downloads.MIME_TYPE, RECORDING_DOWNLOAD_MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, RECORDING_DOWNLOAD_DIRECTORY)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = checkNotNull(contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        var published = false
        try {
            val output = checkNotNull(contentResolver.openOutputStream(uri, "w"))
            output.use { target ->
                file.inputStream().use { source ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        ensureActive()
                        val size = source.read(buffer)
                        if (size == -1) break
                        target.write(buffer, 0, size)
                    }
                }
            }
            saveRecordingMetadata(uri, id, recording, file.length())
            saveThumbnail(uri, file, recording.thumbnail)
            ensureActive()
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            check(contentResolver.update(uri, ready, null, null) == 1)
            published = true
            downloadBackupChanges.update { it + 1 }
        } finally {
            if (!published) {
                runCatching { contentResolver.delete(uri, null, null) }
                removeRecordingMetadata(uri)
            }
        }
    }

    private suspend fun saveThumbnail(uri: Uri, file: File, thumbnail: String?) {
        try {
            val artwork = thumbnail?.takeIf { it.startsWith("https://") }?.let { url ->
                val request = ImageRequest.Builder(this).data(url).size(480, 270).allowHardware(false).build()
                SingletonImageLoader.get(this).execute(request).image?.toBitmap()
            } ?: ThumbnailUtils.createVideoThumbnail(file, Size(480, 270), null)
            recordingThumbnail(uri).outputStream().use { artwork.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            recordingThumbnail(uri).delete()
        }
    }

    private fun progressNotification(task: RecordingDownloadTask, progress: RecordingDownloadProgress?): Notification {
        val cancel = PendingIntent.getService(
            this, task.id.hashCode(), Intent(this, RecordingDownloadService::class.java).setAction(ACTION_CANCEL)
                .putExtra(EXTRA_TASK_ID, task.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val queued = queue.tasks.value.filter { it.id != task.id && !it.failed }
        val title = when {
            progress?.saving == true -> getString(R.string.recording_download_saving)
            progress?.percent != null -> getString(R.string.you_download_progress, progress.percent)
            else -> getString(R.string.recording_download_in_progress)
        }
        val detail = buildString {
            append(task.recording.title)
            if (queued.isNotEmpty()) {
                appendLine()
                append(getString(R.string.you_download_next, queued.size))
                queued.take(4).forEach { appendLine(); append(it.recording.title) }
            }
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(task.recording.title)
            .setSubText(queued.takeIf { it.isNotEmpty() }?.let { getString(R.string.you_download_next, it.size) })
            .setStyle(Notification.BigTextStyle().bigText(detail))
            .setContentIntent(openDownloads())
            .setProgress(100, progress?.percent ?: 0, progress?.percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.recording_download_cancel), cancel).build())
            .build()
    }

    private fun showResult(task: RecordingDownloadTask, message: Int, success: Boolean) {
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(getString(message))
            .setContentText(task.recording.title)
            .setAutoCancel(true)
            .setContentIntent(openDownloads())
            .build()
        notifications.notify(task.id, RESULT_NOTIFICATION, notification)
        if (!notifications.areNotificationsEnabled()) toast(message)
    }

    private fun openDownloads(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).setAction(ACTION_OPEN_DOWNLOADS)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE
    )

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onTimeout(startId: Int, fgsType: Int) {
        worker?.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingDownload"
        private const val CHANNEL = "recording_downloads"
        private const val PROGRESS_NOTIFICATION = 241
        private const val RESULT_NOTIFICATION = 242
        internal const val ACTION_OPEN_DOWNLOADS = "com.lechixy.kick.OPEN_DOWNLOADS"
        private const val ACTION_ENQUEUE = "com.lechixy.kick.ENQUEUE_RECORDING_DOWNLOAD"
        private const val ACTION_RETRY = "com.lechixy.kick.RETRY_RECORDING_DOWNLOAD"
        private const val EXTRA_TASK_ID = "task"
        private const val EXTRA_RECORDING = "recording"
        private const val ACTION_CANCEL = "com.lechixy.kick.CANCEL_RECORDING_DOWNLOAD"

        fun enqueue(context: Context, recording: RecordedPlayback) {
            val request = Intent(context, RecordingDownloadService::class.java).setAction(ACTION_ENQUEUE)
                .putExtra(EXTRA_RECORDING, Json.encodeToString(recording))
            start(context, request)
        }

        internal fun resume(context: Context, retryId: String? = null) {
            val intent = Intent(context, RecordingDownloadService::class.java)
            if (retryId != null) intent.setAction(ACTION_RETRY).putExtra(EXTRA_TASK_ID, retryId)
            start(context, intent)
        }

        internal fun cancel(context: Context, id: String) {
            if (RecordingDownloadQueue.progress.value != null) {
                val intent = Intent(context, RecordingDownloadService::class.java).setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_TASK_ID, id)
                context.startService(intent)
            } else RecordingDownloadQueue.remove(id)
        }

        private fun start(context: Context, request: Intent) {
            runCatching { ContextCompat.startForegroundService(context, request) }
                .onFailure { Toast.makeText(context, R.string.recording_download_failed, Toast.LENGTH_SHORT).show() }
        }
    }
}
