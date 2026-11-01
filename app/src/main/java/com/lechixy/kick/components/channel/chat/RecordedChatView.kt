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
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.chat.model.*
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.player.model.RecordedPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun RecordedChatView(
    channel: ChannelDetail, recording: RecordedPlayback, playerHost: PlayerHostState,
    modifier: Modifier = Modifier, onHideChat: (() -> Unit)? = null, onOpenChannel: (String) -> Unit = {}
) {
    // Clip creation time is not the source stream's timeline; only full recordings have this anchor.
    val start = remember(recording.id, recording.startTime) {
        recordingStartInstant(recording.startTime).takeIf { recording.directUrl == null }
    }
    if (start == null) {
        Box(modifier) { ContentState(stringResource(R.string.recorded_chat_unavailable)) }
        return
    }
    val timeline = remember(channel.id, recording.id, start) { RecordedChatTimeline(start) }
    val repository = remember { KickRepository(KickNetwork.api) }
    val position = playerHost.recordingPositionMs
    val bucket = position / 5000
    var previousBucket by remember(timeline) { mutableStateOf<Long?>(null) }
    var version by remember(timeline) { mutableIntStateOf(0) }
    var loading by remember(timeline) { mutableStateOf(true) }
    var failed by remember(timeline) { mutableStateOf(false) }
    var retry by remember(timeline) { mutableIntStateOf(0) }
    LaunchedEffect(timeline, bucket, retry) {
        val reset = previousBucket == null || bucket < previousBucket!! || bucket > previousBucket!! + 1
        if (reset) { timeline.clear(); version++; loading = true }
        previousBucket = bucket
        // Fetch the current boundary plus a short lookahead so messages can appear on time.
        val boundaries = if (reset || failed) listOf(bucket * 5000, (bucket + 1) * 5000) else listOf((bucket + 1) * 5000)
        failed = false
        while (true) {
            try {
                for (boundary in boundaries) {
                    val history = repository.getRecordedChatHistory(channel.id, timeline.requestTime(boundary))
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
                delay(5000)
            }
        }
    }
    val messages = remember(timeline, version, position) { timeline.visible(position) }
    key(recording.id) {
        ChatView(messages = messages,
            connectionState = if (loading) ChatConnectionState.CONNECTING else ChatConnectionState.CONNECTED,
            subscriberBadges = channel.subscriberBadges, streamerUsername = channel.user?.username,
            streamerSlug = channel.slug, channelId = channel.id, isReplay = true,
            onHideChat = onHideChat, onOpenChannel = onOpenChannel, modifier = modifier,
            composer = {
                if (failed) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.recorded_chat_failed), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { retry++ }) { Text(stringResource(R.string.chat_retry)) }
                }
            })
    }
}
