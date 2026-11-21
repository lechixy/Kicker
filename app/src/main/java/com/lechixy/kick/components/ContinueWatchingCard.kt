package com.lechixy.kick.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.util.FormatUtils
import com.lechixy.kick.util.buildOptimizedImageRequest

@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    onClick: (ExpansionSource?) -> Unit,
) {
    var cardCoordinates by remember(entry.key) { mutableStateOf<LayoutCoordinates?>(null) }
    var mediaCoordinates by remember(entry.key) { mutableStateOf<LayoutCoordinates?>(null) }
    var showActions by remember(entry.key) { mutableStateOf(false) }
    var lastTapAt by remember(entry.key, enabled) { mutableStateOf<Long?>(null) }
    val actionsVisible = showActions && enabled
    val doubleTapTimeout = LocalViewConfiguration.current.doubleTapTimeoutMillis
    val context = LocalContext.current
    val imageRequest = remember(context, entry.thumbnail) {
        buildOptimizedImageRequest(context = context, data = entry.thumbnail,
            targetWidthDp = 400, targetHeightDp = 225)
    }
    fun resume() {
        showActions = false
        lastTapAt = null
        val card = cardCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        val media = mediaCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        onClick(card?.let {
            ExpansionSource(cardRect = it, mediaRect = media, cornerRadius = 12.dp,
                thumbnailUrl = entry.thumbnail, title = entry.title, username = entry.channelName,
                isLive = false, isStreamCard = true)
        })
    }
    InteractiveCard(
        onClick = {
            val now = SystemClock.uptimeMillis()
            val previousTap = lastTapAt
            if (showActions && previousTap != null && now - previousTap <= doubleTapTimeout) {
                resume()
            } else {
                lastTapAt = now
                showActions = true
            }
        },
        onClickLabel = stringResource(R.string.home_show_history_actions),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { cardCoordinates = it },
    ) { mediaZoom ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).onGloballyPositioned { mediaCoordinates = it }) {
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    AsyncImage(imageRequest, entry.title, Modifier.fillMaxSize().then(mediaZoom), contentScale = ContentScale.Crop)
                }
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = Color.Black.copy(alpha = .75f),
                    shape = MaterialTheme.shapes.extraSmall) {
                    Text(stringResource(R.string.home_last_position, FormatUtils.formatDuration(entry.positionMs)),
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                if (entry.durationMs > 0) LinearProgressIndicator(
                    progress = { (entry.positionMs.toDouble() / entry.durationMs).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp, drawStopIndicator = {},
                )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title.ifBlank { entry.channelName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text(entry.channelName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (actionsVisible) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .48f))) {
                FilledIconButton(onClick = ::resume, modifier = Modifier.align(Alignment.Center).size(54.dp)) {
                    Icon(
                        painterResource(R.drawable.play_arrow_24dp_e3e3e3_fill1_wght400_grad0_opsz24),
                        stringResource(R.string.home_continue_watching),
                        Modifier.size(30.dp),
                    )
                }
                FilledIconButton(
                    onClick = {
                        showActions = false
                        lastTapAt = null
                        onRemove()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = .62f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                        stringResource(R.string.home_remove_history),
                    )
                }
            }
        }
    }
}
