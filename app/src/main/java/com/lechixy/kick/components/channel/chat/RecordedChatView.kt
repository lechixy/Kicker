package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lechixy.kick.R
import com.lechixy.kick.components.ContentState
import com.lechixy.kick.components.PlayerHostState
import com.lechixy.kick.components.PlayerMode
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.*
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.model.RecordedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun RecordedChatView(
    channel: ChannelDetail, recording: RecordedPlayback, playerHost: PlayerHostState,
    modifier: Modifier = Modifier, onHideChat: (() -> Unit)? = null, onOpenChannel: (String) -> Unit = {},
    autoHideHeader: Boolean = false
) {
    val chat = recordedChatForPresentation(channel.id, recording, playerHost)
    if (chat == null) {
        Box(modifier) { ContentState(stringResource(R.string.recorded_chat_unavailable)) }
        return
    }
    key(recording.id) {
        ChatView(messages = chat.messages,
            connectionState = if (chat.loading) ChatConnectionState.CONNECTING else ChatConnectionState.CONNECTED,
            subscriberBadges = channel.subscriberBadges, streamerUsername = channel.user?.username,
            streamerSlug = channel.slug, channelId = channel.id, isReplay = true,
            onHideChat = onHideChat, onOpenChannel = onOpenChannel, modifier = modifier,
            autoHideHeader = autoHideHeader,
            playerControlsVisible = playerHost.isControlsVisible,
            composer = {
                if (chat.failed) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.recorded_chat_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = chat.retry) { Text(stringResource(R.string.chat_retry)) }
                }
            })
    }
}

internal data class RecordedChatState(
    val messages: List<ChatMessage>,
    val loading: Boolean,
    val failed: Boolean,
    val retry: () -> Unit,
)

private data class FullscreenRecordedChat(val chat: RecordedChatState?)
private val LocalFullscreenRecordedChat = compositionLocalOf<FullscreenRecordedChat?> { null }

@Composable
internal fun FullscreenRecordedChatBox(
    host: PlayerHostState,
    overlayEnabled: Boolean,
    isInPipMode: Boolean,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val channel = host.channel
    val recording = host.recording
    val fullscreenChat = if (host.mode == PlayerMode.Fullscreen && !isInPipMode && channel != null && recording != null) {
        // Both surfaces can draw the same messages during a fullscreen crossfade.
        val active by remember(host, overlayEnabled) {
            derivedStateOf { overlayEnabled || host.isSideChatOpen(isWide = true) || host.chatOpenProgress.value > 0f }
        }
        FullscreenRecordedChat(rememberRecordedChat(channel.id, recording, host, active))
    } else null
    CompositionLocalProvider(LocalFullscreenRecordedChat provides fullscreenChat) {
        Box(modifier, content = content)
    }
}

@Composable
internal fun recordedChatForPresentation(
    channelId: Long,
    recording: RecordedPlayback,
    host: PlayerHostState,
): RecordedChatState? {
    val fullscreen = LocalFullscreenRecordedChat.current
    return if (fullscreen != null) fullscreen.chat else rememberRecordedChat(channelId, recording, host)
}

@Composable
private fun rememberRecordedChat(
    channelId: Long,
    recording: RecordedPlayback,
    playerHost: PlayerHostState,
    active: Boolean = LocalChatContentActive.current,
): RecordedChatState? {
    // Clip creation time is not the source stream's timeline; only full recordings have this anchor.
    val start = remember(recording.id, recording.startTime) {
        recordingStartInstant(recording.startTime).takeIf { recording.directUrl == null }
    }
    if (start == null) return null
    val seek = playerHost.recordingChatSeek
    val timeline = remember(channelId, recording.id, start, seek) { RecordedChatTimeline(start) }
    val repository = remember { KickRepository(KickNetwork.api) }
    val position by key(timeline) {
        val initialPosition = remember { playerHost.recordingPositionMs }
        produceState(initialPosition, playerHost, active) {
            if (active) snapshotFlow { playerHost.recordingPositionMs }.collect { value = it }
        }
    }
    val bucket by remember(timeline) { derivedStateOf { position / 5000 } }
    var previousBucket by remember(timeline) { mutableStateOf<Long?>(null) }
    var version by remember(timeline) { mutableIntStateOf(0) }
    var loading by remember(timeline) { mutableStateOf(true) }
    var failed by remember(timeline) { mutableStateOf(false) }
    var retry by remember(timeline) { mutableIntStateOf(0) }
    LaunchedEffect(timeline, bucket, retry, active) {
        if (!active) return@LaunchedEffect
        val reset = previousBucket == null || bucket < previousBucket!! || bucket > previousBucket!! + 1
        if (reset) { timeline.clear(); version++; loading = true }
        previousBucket = bucket
        // Fetch the current boundary plus a short lookahead so messages can appear on time.
        val boundaries = if (reset || failed) listOf(bucket * 5000, (bucket + 1) * 5000) else listOf((bucket + 1) * 5000)
        failed = false
        while (true) {
            try {
                for (boundary in boundaries) {
                    val history = repository.getRecordedChatHistory(channelId, timeline.requestTime(boundary))
                        ?: error("Missing recorded chat history")
                    timeline.add(history.messages, bucket * 5000)
                    version++
                }
                failed = false
                loading = false
                break
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                loading = false
                failed = true
                delay(5000.milliseconds)
            }
        }
    }
    val messages by remember(timeline, version) { derivedStateOf { timeline.visible(position) } }
    return RecordedChatState(messages, loading, failed) { retry++ }
}
