package com.lechixy.kick.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import com.lechixy.kick.data.player.local.PlaybackProgressStore

@Composable
internal fun PlaybackProgressBar(recordingId: String?) {
    if (recordingId == null) return
    val flow = remember(recordingId) { PlaybackProgressStore.progress.map { it[recordingId]?.fraction ?: 0f }.distinctUntilChanged() }
    val progress by flow.collectAsStateWithLifecycle(initialValue = PlaybackProgressStore.get(recordingId)?.fraction ?: 0f)
    if (progress > 0f) {
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
            gapSize = 0.dp, drawStopIndicator = {}
        )
    }
}
