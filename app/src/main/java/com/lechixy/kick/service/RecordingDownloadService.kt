package com.lechixy.kick.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import com.lechixy.kick.R
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.model.RecordedPlayback
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@androidx.annotation.OptIn(UnstableApi::class)
class RecordingDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private var download: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            download?.cancel()
            if (download == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        if (download != null) {
            toast(R.string.recording_download_busy)
            return START_NOT_STICKY
        }
        val recording = intent?.getStringExtra(EXTRA_RECORDING)?.let {
            runCatching { Json.decodeFromString<RecordedPlayback>(it) }.getOrNull()
        }
        if (recording == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.recording_download_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val started = runCatching { startForeground(PROGRESS_NOTIFICATION, progressNotification(recording.title)) }
        if (started.isFailure) {
            toast(R.string.recording_download_failed)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        toast(R.string.recording_download_started)
        download = scope.launch(start = CoroutineStart.LAZY) {
            var tempFile: File? = null
            try {
                val resolved = KickRepository(KickNetwork.api).resolveRecording(recording)
                val source = Uri.parse(checkNotNull(resolved.directUrl))
                require(source.scheme == "https")
                val file = withContext(Dispatchers.IO) {
                    val directory = File(cacheDir, "recording-download")
                    directory.mkdirs()
                    directory.listFiles()?.forEach { it.delete() }
                    File.createTempFile("recording-", ".mp4", directory)
                }
                tempFile = file
                export(source, file, resolved.title)
                notifications.notify(
                    PROGRESS_NOTIFICATION,
                    progressNotification(resolved.title, message = R.string.recording_download_saving)
                )
                val result = saveToDownloads(file, resolved.title.ifBlank { resolved.id })
                showResult(R.string.recording_download_complete, resolved.title, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Download failed (${error.javaClass.simpleName})")
                showResult(R.string.recording_download_failed, recording.title)
            } finally {
                tempFile?.delete()
                download = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        download?.start()
        return START_NOT_STICKY
    }

    private suspend fun export(source: Uri, file: File, title: String) = coroutineScope {
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
            var displayed = -1
            while (true) {
                delay(1_000)
                if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE && holder.progress != displayed) {
                    displayed = holder.progress
                    notifications.notify(PROGRESS_NOTIFICATION, progressNotification(title, displayed))
                }
            }
        }
        try {
            completed.await()
        } finally {
            progress.cancel()
            if (!completed.isCompleted) transformer.cancel()
        }
    }

    private suspend fun saveToDownloads(file: File, title: String): Uri = withContext(Dispatchers.IO) {
        check(file.length() > 0)
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).trim(' ', '.')
            .ifBlank { getString(R.string.recording_download_fallback_name) }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$safeTitle.mp4")
            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kicker")
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
            ensureActive()
            val ready = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            check(contentResolver.update(uri, ready, null, null) == 1)
            published = true
            uri
        } finally {
            if (!published) contentResolver.delete(uri, null, null)
        }
    }

    private fun progressNotification(
        title: String,
        percent: Int? = null,
        message: Int = R.string.recording_download_in_progress
    ): Notification {
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, RecordingDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(message))
            .setContentText(title)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.recording_download_cancel), cancel).build())
            .build()
    }

    private fun showResult(message: Int, title: String, uri: Uri? = null) {
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (uri != null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(getString(message))
            .setContentText(title)
            .setAutoCancel(true)
        if (uri != null) {
            val open = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            notification.setContentIntent(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
        }
        notifications.notify(RESULT_NOTIFICATION, notification.build())
        if (!notifications.areNotificationsEnabled()) toast(message)
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onTimeout(startId: Int, fgsType: Int) {
        download?.cancel()
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
        private const val EXTRA_RECORDING = "recording"
        private const val ACTION_CANCEL = "com.lechixy.kick.CANCEL_RECORDING_DOWNLOAD"

        fun enqueue(context: Context, recording: RecordedPlayback) {
            val request = Intent(context, RecordingDownloadService::class.java)
                .putExtra(EXTRA_RECORDING, Json.encodeToString(recording))
            runCatching { ContextCompat.startForegroundService(context, request) }
                .onFailure { Toast.makeText(context, R.string.recording_download_failed, Toast.LENGTH_SHORT).show() }
        }
    }
}
