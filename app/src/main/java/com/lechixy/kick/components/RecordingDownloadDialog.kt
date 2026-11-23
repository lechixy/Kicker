package com.lechixy.kick.components

import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.player.local.PlaybackProgressStore
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.data.player.local.WatchHistoryStore
import com.lechixy.kick.data.player.model.LiveDownloadSnapshot
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.RecordingDownloadRange
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import com.lechixy.kick.service.ClipAudioHandoff
import com.lechixy.kick.service.LiveRecordingDownload
import com.lechixy.kick.service.PreparedRecordingDownload
import com.lechixy.kick.service.kickPlaybackAudioAttributes
import com.lechixy.kick.service.prepareRecordingDownload
import com.lechixy.kick.service.recordingDownloadDataSource
import com.lechixy.kick.service.recordingDownloadMediaItem
import com.lechixy.kick.util.FormatUtils
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

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
    var startInput by remember(source) { mutableStateOf<String?>(null) }
    var endInput by remember(source) { mutableStateOf<String?>(null) }
    var showEnd by remember(source) { mutableStateOf(false) }
    val selectedStart = startInput?.let { parseDownloadTime(it)?.minus(offset) } ?: start.takeIf { startInput == null }
    val selectedEnd = endInput?.let { parseDownloadTime(it)?.minus(offset) } ?: end.takeIf { endInput == null }
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

    PredictiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recording_download)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        confirmButton = {
            TextButton(enabled = source != null && range != null, onClick = {
                if (source != null && range != null) {
                    onConfirm(duration, source.selection.copy(
                        range = range.takeIf { partial },
                        estimatedBytes = estimatedBytes,
                    ), source.snapshot)
                }
            }) { Text(stringResource(R.string.recording_download)) }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(recording.title.ifBlank { recording.channelName ?: recording.id },
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (source != null) {
                    RecordingDownloadPreview(source, recording.thumbnail, range, partial, showEnd,
                        onChooseEnd = { showEnd = it },
                        onDuration = { measuredDuration = it },
                        onBitrate = { measuredBitrate = it })
                } else {
                    DownloadPreviewFrame(recording.thumbnail) {
                        if (loadFailed) {
                            TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.clip_preview_retry)) }
                        } else CircularProgressIndicator()
                    }
                    if (loadFailed) Text(stringResource(R.string.recording_download_source_failed),
                        color = MaterialTheme.colorScheme.error)
                }
                if (live != null) {
                    Text(stringResource(R.string.recording_download_live_window), style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !partial, onClick = {
                        partial = false
                        showEnd = false
                    },
                        label = { Text(stringResource(R.string.recording_download_entire)) })
                    FilterChip(selected = partial, onClick = { partial = true }, enabled = whole != null,
                        label = { Text(stringResource(R.string.recording_download_partial)) })
                }
                if (partial && whole != null) {
                    val sliderRange = trimmedRange ?: whole
                    val sliderState = remember(duration) {
                        RangeSliderState(endValue = duration.toFloat(), trackRange = 0f..duration.toFloat())
                    }
                    SideEffect {
                        val lower = sliderRange.startMs.toFloat()
                        val upper = sliderRange.endMs.toFloat()
                        if (lower > sliderState.endValue) sliderState.endValue = upper
                        sliderState.startValue = lower
                        sliderState.endValue = upper
                    }
                    RangeSlider(
                        state = sliderState,
                        onValueChange = { values ->
                            val step = minOf(1_000L, duration)
                            val sliderStart = ((values.start / step).roundToLong() * step)
                                .coerceIn(0, duration - step)
                            val sliderEnd = if (values.endInclusive == duration.toFloat()) duration else
                                ((values.endInclusive / step).roundToLong() * step).coerceIn(sliderStart + step, duration)
                            pickRange(sliderStart, sliderEnd)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadTimeField(R.string.recording_download_range_start,
                            startInput ?: FormatUtils.formatDuration(start + offset),
                            trimmedRange == null, Modifier.weight(1f)) {
                            startInput = it
                            showEnd = false
                        }
                        DownloadTimeField(R.string.recording_download_range_end,
                            endInput ?: FormatUtils.formatDuration(end + offset),
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
                        Text(stringResource(R.string.recording_download_from_resume,
                            FormatUtils.formatDuration(savedPosition + offset)))
                    }
                }
                if (range != null) {
                    Text(stringResource(R.string.recording_download_range_summary,
                        FormatUtils.formatDuration(range.startMs + offset),
                        FormatUtils.formatDuration(range.endMs + offset),
                        FormatUtils.formatDuration(range.durationMs)))
                    Text(if (estimatedBytes != null) stringResource(R.string.recording_download_estimated_size,
                        Formatter.formatFileSize(context, estimatedBytes))
                    else stringResource(R.string.recording_download_size_unknown),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@Composable
private fun DownloadTimeField(
    label: Int,
    value: String,
    invalid: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
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

@Composable
private fun DownloadPreviewFrame(thumbnail: String?, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().aspectRatio(16f / 9), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun RecordingDownloadPreview(
    source: PreparedRecordingDownload,
    thumbnail: String?,
    range: RecordingDownloadRange?,
    partial: Boolean,
    showEnd: Boolean,
    onChooseEnd: (Boolean) -> Unit,
    onDuration: (Long) -> Unit,
    onBitrate: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reportDuration by rememberUpdatedState(onDuration)
    val reportBitrate by rememberUpdatedState(onBitrate)
    val player = remember(source) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(recordingDownloadDataSource(source.snapshot)))
            .setSeekParameters(SeekParameters.EXACT)
            .build().apply {
                setMediaItem(recordingDownloadMediaItem(source.url, source.selection, source.snapshot, clip = false))
            }
    }
    var ready by remember(player) { mutableStateOf(false) }
    var playing by remember(player) { mutableStateOf(false) }
    var failed by remember(player) { mutableStateOf(false) }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    ready = true
                    if (player.duration > 0) reportDuration(player.duration)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                var bitrate = 0L
                for (group in tracks.groups) {
                    for (index in 0 until group.length) {
                        if (group.isTrackSelected(index)) bitrate += group.getTrackFormat(index).bitrate.coerceAtLeast(0)
                    }
                }
                reportBitrate(bitrate)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                playing = playWhenReady
                ClipAudioHandoff.update(playWhenReady)
            }

            override fun onPlayerError(error: PlaybackException) {
                ready = false
                failed = true
                player.pause()
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycle.addObserver(observer)
        player.addListener(listener)
        player.prepare()
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            if (player.playWhenReady) ClipAudioHandoff.update(false)
            player.release()
        }
    }
    LaunchedEffect(player, ready, range, showEnd) {
        if (ready && range != null) {
            player.pause()
            player.seekTo(if (showEnd) (range.endMs - 200).coerceAtLeast(range.startMs) else range.startMs)
        } else if (range == null) {
            player.pause()
        }
    }
    LaunchedEffect(player, playing, range) {
        if (!playing || range == null) return@LaunchedEffect
        while (player.currentPosition < range.endMs && player.playbackState != Player.STATE_ENDED) delay(40)
        player.pause()
    }

    DownloadPreviewFrame(thumbnail) {
        if (!failed) PlayerVideoSurface(player, playing, Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium))
        if (failed) {
            TextButton(onClick = { failed = false; player.prepare() }) { Text(stringResource(R.string.clip_preview_retry)) }
        } else if (!ready) CircularProgressIndicator()
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = ready && range != null, onClick = {
            if (playing) player.pause()
            else if (range != null) {
                if (player.currentPosition !in range.startMs until (range.endMs - 250).coerceAtLeast(range.startMs)) {
                    player.seekTo(range.startMs)
                }
                ClipAudioHandoff.update(true)
                player.setAudioAttributes(kickPlaybackAudioAttributes, true)
                player.play()
            }
        }) { Text(stringResource(if (playing) R.string.recording_download_pause_preview else R.string.recording_download_preview)) }
        if (partial) {
            TextButton(enabled = ready && range != null, onClick = {
                player.pause()
                onChooseEnd(false)
                range?.let { player.seekTo(it.startMs) }
            }) { Text(stringResource(R.string.recording_download_range_start)) }
            TextButton(enabled = ready && range != null, onClick = {
                player.pause()
                onChooseEnd(true)
                range?.let { player.seekTo((it.endMs - 200).coerceAtLeast(it.startMs)) }
            }) { Text(stringResource(R.string.recording_download_range_end)) }
        }
    }
}
