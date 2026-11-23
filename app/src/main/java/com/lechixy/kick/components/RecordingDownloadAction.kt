package com.lechixy.kick.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lechixy.kick.data.player.model.LiveDownloadSnapshot
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.data.player.model.RecordingDownloadSelection
import com.lechixy.kick.service.LiveRecordingDownload
import com.lechixy.kick.service.RecordingDownloadService

private data class PendingRecordingDownload(
    val recording: RecordedPlayback,
    val selection: RecordingDownloadSelection?,
    val snapshot: LiveDownloadSnapshot?,
)

@Composable
internal fun rememberRecordingDownloadAction(live: LiveRecordingDownload? = null): (RecordedPlayback) -> Unit {
    val context = LocalContext.current
    var target by remember { mutableStateOf<Pair<RecordedPlayback, LiveRecordingDownload?>?>(null) }
    var pending by remember { mutableStateOf<PendingRecordingDownload?>(null) }
    val permission = Manifest.permission.POST_NOTIFICATIONS
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { RecordingDownloadService.enqueue(context, it.recording, it.selection, it.snapshot) }
        pending = null
    }
    fun enqueue(recording: RecordedPlayback, selection: RecordingDownloadSelection?, snapshot: LiveDownloadSnapshot?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            if (pending == null) {
                pending = PendingRecordingDownload(recording, selection, snapshot)
                launcher.launch(permission)
            }
        } else {
            RecordingDownloadService.enqueue(context, recording, selection, snapshot)
        }
    }

    target?.let { (recording, liveSource) ->
        RecordingDownloadDialog(
            recording = recording,
            live = liveSource,
            onDismiss = { target = null },
            onConfirm = { duration, selection, snapshot ->
                target = null
                val source = recording.copy(durationMs = duration)
                enqueue(source, selection, snapshot)
            },
        )
    }
    return { recording ->
        if (recording.id.startsWith("clip_")) {
            enqueue(recording, null, null)
        } else {
            target = recording to live
        }
    }
}
