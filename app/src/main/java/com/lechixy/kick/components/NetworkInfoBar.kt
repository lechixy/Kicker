package com.lechixy.kick.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.lechixy.kick.R
import com.lechixy.kick.data.common.remote.ConnectionNotice
import com.lechixy.kick.data.common.remote.NetworkMonitor

@Composable
fun NetworkInfoBar(modifier: Modifier = Modifier) {
    val notice by NetworkMonitor.notice.collectAsStateWithLifecycle()
    ConnectionNoticeBar(notice, modifier)
}

@Composable
internal fun ConnectionNoticeBar(notice: ConnectionNotice?, modifier: Modifier = Modifier) {
    // Keep the last message/color through the exit animation.
    var displayed by remember { mutableStateOf(ConnectionNotice.Offline) }
    notice?.let { displayed = it }
    val restored = displayed == ConnectionNotice.Restored
    val background = if (restored) Color(0xFF16733D) else MaterialTheme.colorScheme.inverseSurface
    val foreground = if (restored) Color.White else MaterialTheme.colorScheme.inverseOnSurface
    AnimatedVisibility(
        visible = notice != null, modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Box(Modifier.fillMaxWidth().background(background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            Text(stringResource(when (displayed) {
                ConnectionNotice.Offline -> R.string.connection_lost
                ConnectionNotice.Weak -> R.string.connection_weak
                ConnectionNotice.Restored -> R.string.connection_restored
            }), color = foreground, style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center)
        }
    }
}
