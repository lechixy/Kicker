package com.lechixy.kick.components

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import com.lechixy.kick.util.FormatUtils

@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    onClick: (ExpansionSource?) -> Unit,
) {
    var cardCoordinates by remember(entry.key) { mutableStateOf<LayoutCoordinates?>(null) }
    var mediaCoordinates by remember(entry.key) { mutableStateOf<LayoutCoordinates?>(null) }
    var menuOpen by remember(entry.key) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(context, entry.thumbnail) {
        com.lechixy.kick.util.buildOptimizedImageRequest(context = context, data = entry.thumbnail,
            targetWidthDp = 400, targetHeightDp = 225)
    }
    fun resume() {
        val card = cardCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        val media = mediaCoordinates?.takeIf { it.isAttached }?.boundsInRoot()
        onClick(card?.let {
            ExpansionSource(cardRect = it, mediaRect = media, cornerRadius = 12.dp,
                thumbnailUrl = entry.thumbnail, title = entry.title, username = entry.channelName,
                isLive = false, isStreamCard = true)
        })
    }
    Box {
        Card(onClick = { menuOpen = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().onGloballyPositioned { cardCoordinates = it },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f))) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).onGloballyPositioned { mediaCoordinates = it }) {
                AsyncImage(imageRequest, entry.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Surface(Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .75f),
                    shape = MaterialTheme.shapes.extraSmall) {
                    Text(stringResource(R.string.home_last_position, FormatUtils.formatDuration(entry.positionMs)),
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White)
                }
            }
            if (entry.durationMs > 0) LinearProgressIndicator(
                progress = { (entry.positionMs.toDouble() / entry.durationMs).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Butt,
                gapSize = 0.dp, drawStopIndicator = {})
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title.ifBlank { entry.channelName }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text(entry.channelName, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menuOpen && enabled, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_continue_watching)) },
                    leadingIcon = { Icon(painterResource(R.drawable.play_circle_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null) },
                    onClick = { menuOpen = false; resume() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_remove_history)) },
                    leadingIcon = { Icon(painterResource(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24), null) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}
