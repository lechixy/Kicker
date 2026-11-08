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
import com.lechixy.kick.data.player.model.RecordedPlayback
import com.lechixy.kick.service.RecordingDownloadService

@Composable
internal fun rememberRecordingDownloadAction(): (RecordedPlayback) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<RecordedPlayback?>(null) }
    val permission = Manifest.permission.POST_NOTIFICATIONS
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { RecordingDownloadService.enqueue(context, it) }
        pending = null
    }
    return { recording ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        ) {
            if (pending == null) {
                pending = recording
                launcher.launch(permission)
            }
        } else {
            RecordingDownloadService.enqueue(context, recording)
        }
    }
}
