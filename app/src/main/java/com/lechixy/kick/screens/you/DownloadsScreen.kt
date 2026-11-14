package com.lechixy.kick.screens.you

import android.os.StatFs
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.lechixy.kick.components.settings.SettingsCardSurface
import com.lechixy.kick.components.settings.SettingsTopBar
import com.lechixy.kick.components.settings.settingsOutlineColor
import com.lechixy.kick.data.player.local.RecordingDownload
import com.lechixy.kick.data.player.local.RecordingDownloadProgress
import com.lechixy.kick.data.player.local.RecordingDownloadQueue
import com.lechixy.kick.data.player.local.RecordingDownloadTask
import com.lechixy.kick.data.player.local.observeRecordingDownloads
import com.lechixy.kick.data.player.local.forgetDownloadIndexEntry
import com.lechixy.kick.data.player.local.linkRecordingDownloads
import com.lechixy.kick.data.player.local.unlinkRecordingDownload
import com.lechixy.kick.data.player.local.removeRecordingMetadata
import com.lechixy.kick.service.RecordingDownloadService
import com.lechixy.kick.util.FormatUtils
import java.text.NumberFormat
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
    val totalBytes = recordings.filterNot { it.linked }.sumOf { it.size }
    val isDownloading = progress != null
    val storage by produceState<Pair<Long, Long>?>(null, totalBytes, isDownloading) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val volume = StatFs((context.getExternalFilesDir(null) ?: context.filesDir).path)
                volume.totalBytes to volume.availableBytes
            }.getOrNull()
        }
    }
    val scope = rememberCoroutineScope()
    val linkFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val linked = withContext(Dispatchers.IO) { context.linkRecordingDownloads(uris) }
            if (linked != uris.size) Toast.makeText(context, R.string.you_download_link_failed, Toast.LENGTH_SHORT).show()
        }
    }
    var playing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var cancelling by rememberSaveable { mutableStateOf<String?>(null) }

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
                        Column {
                            SectionHeader(
                                stringResource(R.string.you_download_queue),
                                leadingIcon = R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                            )
                            if (progress == null && tasks.any { !it.failed }) {
                                DownloadActionButton(
                                    R.string.you_download_resume,
                                    R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24,
                                ) { RecordingDownloadService.resume(context) }
                            }
                        }
                    }
                    items(tasks, key = { it.id }) { task ->
                        val active = progress?.takeIf { it.id == task.id }
                        DownloadTaskCard(
                            task, active,
                            onRetry = { RecordingDownloadService.resume(context, task.id) },
                            onCancel = { cancelling = task.id },
                        )
                    }
                }
                item("completed") {
                    Column {
                        SectionHeader(
                            stringResource(R.string.you_download_completed),
                            leadingIcon = R.drawable.check_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        )
                        DownloadActionButton(
                            R.string.you_download_link,
                            R.drawable.open_in_new_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        ) { linkFiles.launch(arrayOf("video/mp4")) }
                    }
                }
                if (recordings.isEmpty()) item("status") { DownloadListMessage(downloads) }
                items(recordings, key = { it.uri.toString() }) { recording ->
                    CompletedDownloadCard(
                        recording,
                        onPlay = { playing = recording.uri.toString() },
                        onRemove = { deleting = recording.uri.toString() },
                    )
                }
            }
        }
    }
    recordings.firstOrNull { it.uri.toString() == playing }?.let { recording ->
        DownloadPlaybackDialog(recording) { playing = null }
    }
    tasks.firstOrNull { it.id == cancelling }?.let { task ->
        val action = if (task.failed) R.string.you_download_remove_task else R.string.you_download_cancel_task
        PredictiveAlertDialog(
            onDismissRequest = { cancelling = null },
            title = { Text(stringResource(action), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(
                if (task.failed) R.string.you_download_remove_task_message else R.string.you_download_cancel_task_message,
                task.recording.title.ifBlank { task.recording.id },
            ), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    cancelling = null
                    RecordingDownloadService.cancel(context, task.id)
                }) { Text(stringResource(action), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    recordings.firstOrNull { it.uri.toString() == deleting }?.let { recording ->
        val action = if (recording.linked) R.string.you_download_unlink else R.string.you_download_delete
        PredictiveAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(action), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(if (recording.linked) R.string.you_download_unlink_message
                else R.string.you_download_delete_message, recording.name), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            runCatching {
                                if (recording.linked) context.unlinkRecordingDownload(recording.uri)
                                else {
                                    check(context.contentResolver.delete(recording.uri, null, null) > 0)
                                    context.removeRecordingMetadata(recording.uri)
                                    context.forgetDownloadIndexEntry(recording)
                                }
                            }.isSuccess
                        }
                        if (!deleted) Toast.makeText(context, R.string.you_download_delete_failed, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(action), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: RecordingDownloadTask,
    progress: RecordingDownloadProgress?,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val recording = task.recording
    val status = when {
        task.failed -> R.string.recording_download_failed
        progress?.saving == true -> R.string.recording_download_saving
        progress != null -> R.string.recording_download_in_progress
        else -> R.string.you_download_waiting
    }
    SettingsCardSurface {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(recording.title.ifBlank { recording.id }, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    DownloadChannel(recording.channelSlug, recording.channelName, recording.channelAvatar)
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        painterResource(if (task.failed) R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                            else R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(if (task.failed) R.string.you_download_remove_task else R.string.you_download_cancel_task),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            HorizontalDivider(color = settingsOutlineColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(status), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = if (task.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                progress?.percent?.let {
                    val locale = LocalConfiguration.current.locales[0]
                    val formatter = remember(locale) { NumberFormat.getPercentInstance(locale) }
                    Text(formatter.format(it / 100.0),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (progress != null) {
                val percent = progress.percent
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = trackColor)
                else LinearProgressIndicator({ percent / 100f }, Modifier.fillMaxWidth(), trackColor = trackColor)
                Text(
                    stringResource(R.string.you_download_written,
                        Formatter.formatFileSize(context, progress.writtenBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (task.failed) DownloadActionButton(R.string.chat_retry, onClick = onRetry)
        }
    }
}

@Composable
private fun CompletedDownloadCard(recording: RecordingDownload, onPlay: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val size = Formatter.formatFileSize(context, recording.size)
    val details = if (recording.downloadedAt > 0) {
        val date = DateUtils.formatDateTime(context, recording.downloadedAt,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_TIME)
        stringResource(R.string.you_download_details, size, date)
    } else size
    SettingsCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.you_download_play), onClick = onPlay)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DownloadThumbnail(recording, Modifier.weight(.3f), showPlay = true)
                Column(Modifier.weight(.7f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(recording.name, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    recording.metadata?.let { DownloadChannel(it.channelSlug, it.channelName, it.channelAvatar) }
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (recording.linked) {
                        Text(stringResource(R.string.you_download_linked), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.padding(end = 4.dp)) {
                Icon(
                    painterResource(if (recording.linked) R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24
                        else R.drawable.delete_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    stringResource(if (recording.linked) R.string.you_download_unlink else R.string.you_download_delete),
                    tint = if (recording.linked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DownloadActionButton(
    @StringRes label: Int,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, settingsOutlineColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceContainerLow,
            contentColor = colors.onSurface,
        ),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.padding(end = 8.dp), tint = colors.primary)
        }
        Text(stringResource(label))
    }
}

@Composable
private fun DownloadChannel(slug: String?, name: String?, avatar: String?) {
    val label = name?.takeIf(String::isNotBlank) ?: slug?.takeIf(String::isNotBlank) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        UsageChannelAvatar(slug, label, avatar, Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DownloadsStorage(downloadBytes: Long, storage: Pair<Long, Long>?) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    SettingsCardSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null, tint = colors.primary)
                Text(stringResource(R.string.you_download_storage), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.you_download_storage_used, Formatter.formatFileSize(context, downloadBytes)),
                style = MaterialTheme.typography.titleSmall)
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
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
private fun DownloadThumbnail(recording: RecordingDownload, modifier: Modifier, showPlay: Boolean = false) {
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
                if (showPlay) {
                    Surface(shape = CircleShape, color = Color.Black.copy(alpha = .55f)) {
                        Icon(
                            painterResource(R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp).size(18.dp),
                            tint = Color.White,
                        )
                    }
                }
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
