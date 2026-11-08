package com.lechixy.kick.screens.clip

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lechixy.kick.R
import com.lechixy.kick.components.account.AccountFollowAction
import com.lechixy.kick.components.account.rememberAccountFollowAction
import com.lechixy.kick.components.channel.chat.ChatMessageItem
import com.lechixy.kick.components.rememberRecordingDownloadAction
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.util.buildOptimizedImageRequest

private val clipActionShape = RoundedCornerShape(8.dp)

@Composable
internal fun ClipReplayPanel(
    state: ClipPlayerUiState,
    positionMs: Long,
    showChat: Boolean,
    onToggleChat: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onWatchLivestream: (ClipDetail) -> Unit,
    onWatchFullVideo: (ClipDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        val detailsHeight = (maxHeight - 100.dp).coerceAtLeast(0.dp)
        val availableHeight = (maxHeight - 52.dp).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ClipControlButton(
                        R.drawable.chat_24dp_e3e3e3_fill0_wght400_grad0_opsz24,
                        if (showChat) R.string.player_hide_chat else R.string.player_show_chat,
                        onToggleChat,
                        tint = if (showChat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClipControlButton(R.drawable.close_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_close, onClose)
                }
                Text(
                    stringResource(R.string.chat),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.clip?.let { clip ->
                ClipChannelDetails(
                    clip, { onWatchLivestream(clip) }, { onWatchFullVideo(clip) },
                    Modifier.heightIn(max = if (showChat) detailsHeight else availableHeight)
                        .verticalScroll(rememberScrollState()),
                )
                if (showChat) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ClipChatReplay(state, positionMs, onRetry, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ClipChannelDetails(
    clip: ClipDetail,
    onWatchLivestream: () -> Unit,
    onWatchFullVideo: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preview = LocalInspectionMode.current
    val follow = if (preview) AccountFollowAction(true, stringResource(R.string.following), true) {}
        else rememberAccountFollowAction(clip.channel.slug, optimistic = true)
    val download = if (preview) { _ -> } else rememberRecordingDownloadAction()
    var showMore by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 48.dp, height = 52.dp)) {
                AsyncImage(
                    model = remember(context, clip.channel.profilePicture) {
                        buildOptimizedImageRequest(context, clip.channel.profilePicture, 44, 44)
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.TopCenter).size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
                if (clip.channel.isLive) {
                    Text(
                        stringResource(R.string.clip_live_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        clip.channel.username,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (clip.channel.isVerified) {
                        Icon(
                            painterResource(R.drawable.verified_badge),
                            stringResource(R.string.clip_verified),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                clip.video?.let {
                    Text(
                        it.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = follow.onClick,
                enabled = follow.enabled,
                modifier = Modifier.weight(1f),
                shape = clipActionShape,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painterResource(if (follow.following) R.drawable.favorite_24dp_e3e3e3_fill1_wght400_grad0_opsz24
                        else R.drawable.favorite_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (follow.following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(follow.label, Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val shareLabel = stringResource(R.string.clip_share)
            ClipActionButton(R.drawable.share_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.clip_share) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clip.shareUrl)
                context.startActivity(Intent.createChooser(send, shareLabel))
            }
            ClipActionButton(R.drawable.download_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.recording_download) { download(clip.toRecording()) }
            Box {
                ClipActionButton(R.drawable.more_vert_24dp_e3e3e3_fill0_wght400_grad0_opsz24, R.string.player_more) { showMore = true }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.channel_open_kick)) }, onClick = {
                        showMore = false
                        uriHandler.openUri(clip.shareUrl)
                    })
                }
            }
        }
        ClipNavigationButton(if (clip.channel.isLive) R.string.clip_watch_live else R.string.clip_open_channel, onWatchLivestream)
        if (clip.video != null) ClipNavigationButton(R.string.clip_watch_full_video, onWatchFullVideo)
    }
}

@Composable
private fun ClipActionButton(icon: Int, label: Int, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = clipActionShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) { Icon(painterResource(icon), stringResource(label), Modifier.size(20.dp)) }
}

@Composable
private fun ClipNavigationButton(label: Int, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = clipActionShape,
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.open_in_new_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ClipChatReplay(state: ClipPlayerUiState, positionMs: Long, onRetry: () -> Unit, modifier: Modifier) {
    val clip = state.clip ?: return
    val messages = state.chatMessages
    val visibleCount = messages.indexOfFirst { (it.recordingOffsetMs ?: 0) > positionMs }
        .takeIf { it >= 0 } ?: messages.size
    val list = rememberLazyListState()
    val latestId = messages.getOrNull(visibleCount - 1)?.id
    val atLatest = list.firstVisibleItemIndex == 0
    LaunchedEffect(latestId) {
        if (atLatest && visibleCount > 0) list.scrollToItem(0)
    }
    Column(modifier.fillMaxWidth()) {
        if (state.chatFailed) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.recorded_chat_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_retry)) }
            }
        }
        if (visibleCount > 0) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                reverseLayout = true,
                state = list,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                items(visibleCount, key = { messages[visibleCount - it - 1].id }) { index ->
                    ChatMessageItem(
                        message = messages[visibleCount - index - 1],
                        subscriberBadges = state.subscriberBadges,
                        streamerUsername = clip.channel.username,
                        streamerSlug = clip.channel.slug,
                    )
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                when {
                    state.chatLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    state.chatFailed -> Unit
                    else -> Text(
                        stringResource(when {
                            state.chatComplete && messages.isEmpty() -> R.string.clip_chat_empty
                            !state.chatAvailable -> R.string.recorded_chat_unavailable
                            else -> R.string.clip_chat_waiting
                        }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
