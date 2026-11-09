package com.lechixy.kick.screens.you

import android.os.StatFs
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.components.PredictiveAlertDialog
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.data.player.local.RecordingDownload
import com.lechixy.kick.data.player.local.RecordingDownloadQueue
import com.lechixy.kick.data.player.local.observeRecordingDownloads
import com.lechixy.kick.data.player.local.removeRecordingMetadata
import com.lechixy.kick.service.RecordingDownloadService
import com.lechixy.kick.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads by remember(context) { observeRecordingDownloads(context) }.collectAsStateWithLifecycle(null)
    val tasks by RecordingDownloadQueue.tasks.collectAsStateWithLifecycle()
    val progress by RecordingDownloadQueue.progress.collectAsStateWithLifecycle()
    val recordings = downloads?.getOrNull().orEmpty()
    val totalBytes = recordings.sumOf { it.size }
    val storage by produceState<Pair<Long, Long>?>(null, totalBytes) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val volume = StatFs((context.getExternalFilesDir(null) ?: context.filesDir).path)
                volume.totalBytes to volume.availableBytes
            }.getOrNull()
        }
    }
    val scope = rememberCoroutineScope()
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { SettingsTopBar(stringResource(R.string.you_downloads), onBack) }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 840.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item("storage") { DownloadsStorage(totalBytes, storage) }
                if (tasks.isNotEmpty()) {
                    item("queue") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.you_download_queue), modifier = Modifier.weight(1f))
                            if (progress == null && tasks.any { !it.failed }) {
                                TextButton(onClick = { RecordingDownloadService.resume(context) }) {
                                    Text(stringResource(R.string.you_download_resume))
                                }
                            }
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        val active = progress?.takeIf { it.id == task.id }
                        ListItem(
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(when {
                                        task.failed -> R.string.recording_download_failed
                                        active?.saving == true -> R.string.recording_download_saving
                                        active != null -> R.string.recording_download_in_progress
                                        else -> R.string.recording_download_queued
                                    }))
                                    if (active != null) {
                                        val percent = active.percent
                                        if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        else {
                                            LinearProgressIndicator({ percent / 100f }, Modifier.fillMaxWidth())
                                            Text(stringResource(R.string.you_download_progress, percent))
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Row {
                                    if (task.failed) TextButton(onClick = { RecordingDownloadService.resume(context, task.id) }) {
                                        Text(stringResource(R.string.chat_retry))
                                    }
                                    IconButton(onClick = { RecordingDownloadService.cancel(context, task.id) }) {
                                        Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                            stringResource(R.string.recording_download_cancel))
                                    }
                                }
                            },
                        ) { Text(task.recording.title) }
                    }
                }
                item("completed") { SectionHeader(stringResource(R.string.you_download_completed)) }
                item("status") { DownloadListMessage(downloads) }
                items(recordings, key = { it.uri.toString() }) { recording ->
                    Surface(
                        onClick = { playing = recording.uri.toString() },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DownloadThumbnail(recording, Modifier.width(110.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(recording.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                recording.metadata?.channelName?.takeIf(String::isNotBlank)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                val date = DateUtils.formatDateTime(context, recording.downloadedAt,
                                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
                                Text(stringResource(R.string.you_download_details, Formatter.formatFileSize(context, recording.size), date),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { deleting = recording.uri.toString() }) {
                                Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                                    stringResource(R.string.you_download_delete))
                            }
                        }
                    }
                }
            }
        }
    }
    recordings.firstOrNull { it.uri.toString() == playing }?.let { recording ->
        DownloadPlaybackDialog(recording) { playing = null }
    }
    recordings.firstOrNull { it.uri.toString() == deleting }?.let { recording ->
        PredictiveAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.you_download_delete)) },
            text = { Text(stringResource(R.string.you_download_delete_message, recording.name)) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.delete(recording.uri, null, null)
                                context.removeRecordingMetadata(recording.uri)
                            }.isSuccess
                        }
                        if (!deleted) Toast.makeText(context, R.string.you_download_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.you_download_delete)) }
            },
        )
    }
}

@Composable
private fun DownloadsStorage(downloadBytes: Long, storage: Pair<Long, Long>?) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.you_download_storage), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.you_download_storage_used, Formatter.formatFileSize(context, downloadBytes)))
            if (storage != null) {
                val (total, available) = storage
                if (total > 0) {
                    val used = (1 - available.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    val downloaded = (downloadBytes.toDouble() / total).toFloat().coerceIn(0f, used)
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        drawRect(colors.surfaceContainerHighest)
                        drawRect(colors.outlineVariant, size = Size(size.width * used, size.height))
                        drawRect(colors.primary, Offset.Zero, Size(size.width * downloaded, size.height))
                    }
                    Text(stringResource(R.string.you_download_storage_available,
                        Formatter.formatFileSize(context, available), Formatter.formatFileSize(context, total)),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun DownloadListMessage(downloads: Result<List<RecordingDownload>>?) {
    val message = when {
        downloads == null -> R.string.you_downloads_loading
        downloads.isFailure -> R.string.you_downloads_failed
        downloads.getOrNull().isNullOrEmpty() -> R.string.you_downloads_empty
        else -> return
    }
    YouSectionMessage(stringResource(message))
}

@Composable
internal fun DownloadCard(recording: RecordingDownload, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(onClick, Modifier.fillMaxWidth()) {
        DownloadThumbnail(recording, Modifier.fillMaxWidth())
        Text(recording.name, Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(Formatter.formatFileSize(context, recording.size), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DownloadThumbnail(recording: RecordingDownload, modifier: Modifier) {
    val context = LocalContext.current
    val thumbnail by produceState<Any?>(recording.thumbnail, recording.uri, recording.thumbnail) {
        if (recording.thumbnail == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.loadThumbnail(recording.uri, android.util.Size(320, 180), null)
                }.getOrNull()
            }
        } else value = recording.thumbnail
    }
    Surface(modifier.aspectRatio(16f / 9f), color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (thumbnail != null) {
                AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(painterResource(R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null, Modifier.size(24.dp))
            }
            recording.metadata?.durationMs?.takeIf { it > 0 }?.let { duration ->
                Surface(Modifier.align(Alignment.BottomEnd).padding(4.dp), shape = MaterialTheme.shapes.extraSmall) {
                    Text(FormatUtils.formatDuration(duration), Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
