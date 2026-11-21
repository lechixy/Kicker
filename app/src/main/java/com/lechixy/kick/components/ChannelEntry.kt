package com.lechixy.kick.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Stable
internal class ChannelEntry(
    private val host: PlayerHostState,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val loadChannel: suspend (String) -> ChannelDetail,
    private val onLoadFailed: () -> Unit,
) {
    private data class Opening(
        val slug: String,
        val source: ExpansionSource?,
        val detail: ChannelDetail,
        val watchLive: Boolean,
    )

    private var approval by mutableStateOf<Opening?>(null)
    private var job: Job? = null

    fun open(slug: String, source: ExpansionSource?, watchLive: Boolean = false) {
        cancel()
        val openLive = watchLive || source?.isLive == true
        job = scope.launch {
            try {
                val askBeforeMatureLive = settings.get(SettingsKeys.ASK_BEFORE_MATURE_LIVE).first()
                val reopeningRecording = !openLive && host.slug.equals(slug, ignoreCase = true) && host.recording != null
                if (!askBeforeMatureLive || reopeningRecording) {
                    host.open(slug, source)
                    if (openLive) host.requestLivePlayback()
                    return@launch
                }

                val detail = loadChannel(slug)
                ensureActive()
                val stream = detail.livestream
                if (stream?.isMature == true && !host.isMatureLivestreamApproved(slug, stream.id)) {
                    approval = Opening(slug, source, detail, openLive)
                } else {
                    host.openPreloadedChannel(slug, source, detail, openLive, matureApproved = stream?.isMature == true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onLoadFailed()
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        approval = null
    }

    @Composable
    fun Confirmation() {
        val opening = approval ?: return
        key(opening.slug, opening.detail.livestream?.id) {
            MatureLiveConfirmationDialog(
                onDismiss = { if (approval === opening) approval = null },
                onConfirm = { dontAskAgain ->
                    if (approval === opening) {
                        approval = null
                        host.openPreloadedChannel(opening.slug, opening.source, opening.detail, opening.watchLive,
                            matureApproved = true)
                        if (dontAskAgain) scope.launch { settings.set(SettingsKeys.ASK_BEFORE_MATURE_LIVE, false) }
                    }
                },
            )
        }
    }
}
