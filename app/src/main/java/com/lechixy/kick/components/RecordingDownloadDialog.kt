package com.lechixy.kick.components

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lechixy.kick.R
import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.data.player.local.WatchHistoryStore
import com.lechixy.kick.data.player.model.LiveDownloadSnapshot
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.RecordingDownloadRange
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import com.lechixy.kick.service.LiveRecordingDownload
import com.lechixy.kick.service.PreparedRecordingDownload
import com.lechixy.kick.service.prepareRecordingDownload
import com.lechixy.kick.util.FormatUtils
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException

@Composable
internal fun RecordingDownloadDialog(
    recording: RecordedPlayback,
    live: LiveRecordingDownload?,
    onDismiss: () -> Unit,
    onConfirm: (Long, RecordingDownloadSelection, LiveDownloadSnapshot?) -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var prepared by remember(recording, live, attempt) { mutableStateOf<PreparedRecordingDownload?>(null) }
    var loadFailed by remember(recording, live, attempt) { mutableStateOf(false) }
    LaunchedEffect(recording, live, attempt) {
        try {
            prepared = prepareRecordingDownload(recording, live)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            loadFailed = true
        }
    }
    val source = prepared
    var measuredDuration by remember(source) { mutableLongStateOf(0L) }
    var measuredBitrate by remember(source) { mutableLongStateOf(0L) }
    val duration = source?.durationMs?.takeIf { it > 0 } ?: measuredDuration
    val offset = source?.selection?.timelineOffsetMs ?: 0L
    val savedPosition = remember(recording, source, duration) {
        if (source?.hasBroadcastTimeline == true && duration > 0) {
            savedDownloadPosition(recording, offset + duration)?.minus(offset)?.takeIf { it in 0 until duration }
        } else null
    }
    var partial by remember(recording) { mutableStateOf(false) }
    var start by remember(source) { mutableLongStateOf(0L) }
    var end by remember(source, duration) { mutableLongStateOf(duration) }
    var startInput by remember(source) { mutableStateOf<TextFieldValue?>(null) }
    var endInput by remember(source) { mutableStateOf<TextFieldValue?>(null) }
    var showEnd by remember(source) { mutableStateOf(false) }
    val selectedStart = startInput?.let { parseDownloadTime(it.text)?.minus(offset) } ?: start.takeIf { startInput == null }
    val selectedEnd = endInput?.let { parseDownloadTime(it.text)?.minus(offset) } ?: end.takeIf { endInput == null }
    val trimmedRange = if (selectedStart != null && selectedEnd != null &&
        selectedStart >= 0 && selectedStart < selectedEnd && selectedEnd <= duration
    ) RecordingDownloadRange(selectedStart, selectedEnd) else null
    val whole = duration.takeIf { it > 0 }?.let { RecordingDownloadRange(0L, it) }
    val range = if (partial) trimmedRange else whole
    val estimatedBytes = range?.let {
        val bitrate = source?.bitrate?.toLong() ?: measuredBitrate
        source?.sourceBytes?.let { bytes -> (bytes.toDouble() * it.durationMs / duration).roundToLong() }
            ?: if (bitrate > 0) (bitrate.toDouble() * it.durationMs / 8_000).roundToLong() else null
    }

    fun pickRange(newStart: Long, newEnd: Long) {
        showEnd = newStart == selectedStart && newEnd != selectedEnd
        start = newStart
        end = newEnd
        startInput = null
        endInput = null
    }

    val preview: @Composable (Dp) -> Unit = { frameWidth ->
        if (source != null) {
            RecordingDownloadPreview(source, recording.thumbnail, range, partial, showEnd,
                onDuration = { measuredDuration = it }, onBitrate = { measuredBitrate = it },
                frameWidth = frameWidth)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DownloadPreviewFrame(recording.thumbnail, frameWidth) {
                    if (loadFailed) {
                        Surface(shape = MaterialTheme.shapes.large) {
                            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.clip_preview_retry)) }
                        }
                    } else CircularProgressIndicator()
                }
                if (loadFailed) {
                    Text(stringResource(R.string.recording_download_source_failed),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    val options: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !partial,
                    onClick = { partial = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.recording_download_entire)) }
                SegmentedButton(
                    selected = partial,
                    onClick = { partial = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = whole != null,
                ) { Text(stringResource(R.string.recording_download_partial)) }
            }
            if (live != null) {
                Text(stringResource(R.string.recording_download_live_window),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (partial && whole != null) {
                Text(stringResource(R.string.recording_download_trim_help),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DownloadRangeSlider(trimmedRange ?: whole, duration, ::pickRange)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(FormatUtils.formatDuration(offset), style = MaterialTheme.typography.labelSmall)
                    Text(FormatUtils.formatDuration(offset + duration), style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DownloadTimeField(R.string.recording_download_range_start,
                        startInput ?: TextFieldValue(FormatUtils.formatDuration(start + offset)),
                        trimmedRange == null, Modifier.weight(1f)) {
                        startInput = it
                        showEnd = false
                    }
                    DownloadTimeField(R.string.recording_download_range_end,
                        endInput ?: TextFieldValue(FormatUtils.formatDuration(end + offset)),
                        trimmedRange == null, Modifier.weight(1f)) {
                        endInput = it
                        showEnd = true
                    }
                }
                if (trimmedRange == null) {
                    Text(stringResource(R.string.recording_download_invalid_range),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (savedPosition != null) {
                TextButton(onClick = {
                    val upper = selectedEnd?.takeIf { it > savedPosition && it <= duration } ?: duration
                    partial = true
                    pickRange(savedPosition, upper)
                }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.history_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null)
                        Text(stringResource(R.string.recording_download_from_resume,
                            FormatUtils.formatDuration(savedPosition + offset)))
                    }
                }
            }
        }
    }

    RecordingDownloadPanel(
        title = recording.title.ifBlank { recording.channelName ?: recording.id },
        range = range,
        offset = offset,
        estimatedSize = estimatedBytes?.let { Formatter.formatFileSize(context, it) },
        canDownload = source != null && range != null,
        onDismiss = onDismiss,
        onDownload = {
            if (source != null && range != null) {
                onConfirm(duration, source.selection.copy(
                    range = range.takeIf { partial },
                    estimatedBytes = estimatedBytes,
                ), source.snapshot)
            }
        },
        preview = preview,
        options = options,
    )
}

@Composable
private fun RecordingDownloadPanel(
    title: String,
    range: RecordingDownloadRange?,
    offset: Long,
    estimatedSize: String?,
    canDownload: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    preview: @Composable (Dp) -> Unit,
    options: @Composable () -> Unit,
) {
    val currentPreview by rememberUpdatedState(preview)
    val movablePreview = remember { movableContentOf<Dp> { currentPreview(it) } }
    PredictiveDialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullScreen = maxWidth < 480.dp || maxHeight < 440.dp
            val color = MaterialTheme.colorScheme.surfaceContainerLow
            val backdrop = if (fullScreen) Modifier.background(color) else Modifier
            val margin = if (fullScreen) Modifier else Modifier.padding(16.dp)
            BoxWithConstraints(
                Modifier.fillMaxSize().then(backdrop).windowInsetsPadding(WindowInsets.safeDrawing).imePadding().then(margin),
                contentAlignment = Alignment.Center,
            ) {
                val columns = maxWidth >= 620.dp
                val shortWindow = maxHeight < 280.dp
                if (!fullScreen) {
                    Box(Modifier.matchParentSize().pointerInput(onDismiss) { detectTapGestures { onDismiss() } })
                }
                Surface(
                    if (fullScreen) Modifier.fillMaxSize()
                    else Modifier.widthIn(max = if (columns) 860.dp else 520.dp).fillMaxWidth(),
                    shape = if (fullScreen) RectangleShape else MaterialTheme.shapes.extraLarge,
                    color = color,
                ) {
                    Box(contentAlignment = Alignment.TopCenter) {
                        val contentScroll = if (shortWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier
                        Column(Modifier.widthIn(max = 940.dp).fillMaxWidth().then(contentScroll)) {
                            DownloadPanelHeader(title, onDismiss)
                            val body = if (shortWindow) Modifier else Modifier.weight(1f, fill = fullScreen)
                            if (columns) {
                                Row(body.fillMaxWidth().padding(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    BoxWithConstraints(Modifier.weight(1.1f).padding(bottom = 14.dp)) {
                                        val frameHeight = if (shortWindow) 144.dp else (maxHeight - 36.dp).coerceAtLeast(104.dp)
                                        val scroll = if (shortWindow) Modifier else Modifier.verticalScroll(rememberScrollState())
                                        Column(Modifier.fillMaxWidth().then(scroll)) { movablePreview(frameHeight * (16f / 9f)) }
                                    }
                                    val scroll = if (shortWindow) Modifier else Modifier.verticalScroll(rememberScrollState())
                                    Column(Modifier.weight(1f).then(scroll).padding(bottom = 14.dp)) { options() }
                                }
                            } else {
                                val scroll = if (shortWindow) Modifier else Modifier.verticalScroll(rememberScrollState())
                                Column(body.fillMaxWidth().then(scroll).padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    movablePreview(Dp.Infinity)
                                    options()
                                }
                            }
                            DownloadPanelFooter(range, offset, estimatedSize, canDownload, columns, onDismiss, onDownload)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadPanelHeader(title: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.recording_download_title),
                modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                stringResource(R.string.welcome_close))
        }
    }
}

@Composable
private fun DownloadPanelFooter(
    range: RecordingDownloadRange?,
    offset: Long,
    estimatedSize: String?,
    canDownload: Boolean,
    inlineActions: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    HorizontalDivider()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        val padding = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
        val actions: @Composable (Modifier) -> Unit = { modifier ->
            FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                Button(onClick = onDownload, enabled = canDownload) { Text(stringResource(R.string.recording_download)) }
            }
        }
        if (range != null && inlineActions) {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DownloadRangeSummary(range, offset, estimatedSize, Modifier.weight(1f))
                actions(Modifier.widthIn(max = 300.dp))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (range != null) DownloadRangeSummary(range, offset, estimatedSize, Modifier)
                actions(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DownloadRangeSummary(range: RecordingDownloadRange, offset: Long, estimatedSize: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.recording_download_range_summary,
            FormatUtils.formatDuration(range.startMs + offset),
            FormatUtils.formatDuration(range.endMs + offset),
            FormatUtils.formatDuration(range.durationMs)),
            style = MaterialTheme.typography.titleSmall)
        Text(
            estimatedSize?.let { stringResource(R.string.recording_download_estimated_size, it) }
                ?: stringResource(R.string.recording_download_size_unknown),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadRangeSlider(range: RecordingDownloadRange, duration: Long, onRangeChange: (Long, Long) -> Unit) {
    val slider = remember(duration) {
        RangeSliderState(endValue = duration.toFloat(), trackRange = 0f..duration.toFloat())
    }
    SideEffect {
        val lower = range.startMs.toFloat()
        val upper = range.endMs.toFloat()
        if (lower > slider.endValue) slider.endValue = upper
        slider.startValue = lower
        slider.endValue = upper
    }
    RangeSlider(
        state = slider,
        onValueChange = { values ->
            val step = minOf(1_000L, duration)
            val start = ((values.start / step).roundToLong() * step).coerceIn(0, duration - step)
            val end = if (values.endInclusive == duration.toFloat()) duration else
                ((values.endInclusive / step).roundToLong() * step).coerceIn(start + step, duration)
            onRangeChange(start, end)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DownloadTimeField(
    label: Int,
    value: TextFieldValue,
    invalid: Boolean,
    modifier: Modifier,
    onChange: (TextFieldValue) -> Unit,
) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value = value, onValueChange = onChange, modifier = modifier,
        label = { Text(stringResource(label)) }, singleLine = true, isError = invalid,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

private fun savedDownloadPosition(recording: RecordedPlayback, duration: Long): Long? {
    val history = recording.channelSlug?.let { slug ->
        WatchHistoryStore.progressFor(WatchHistoryEntry(
            key = if (recording.id.startsWith("live:")) "live:$slug:${recording.livestreamId}"
                else "recording:${recording.progressId}",
            channelSlug = slug,
            channelName = recording.channelName.orEmpty(),
            title = recording.title,
            recording = recording,
            livestreamId = recording.livestreamId,
            vodId = recording.vodId,
            startTime = recording.startTime,
        ))
    }
    val saved = PlaybackProgressStore.get(recording.progressId) ?: history
    return saved?.resumePosition(duration)?.takeIf { it > 0 }
}

internal fun parseDownloadTime(value: String): Long? {
    val parts = value.trim().split(':')
    if (parts.size !in 1..3) return null
    var seconds = 0L
    for ((index, part) in parts.withIndex()) {
        if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
        val number = part.toLongOrNull() ?: return null
        if (number > Long.MAX_VALUE / 1000 || index > 0 && number >= 60 ||
            seconds > (Long.MAX_VALUE / 1000 - number) / 60
        ) return null
        seconds = seconds * 60 + number
    }
    return seconds * 1000
}
