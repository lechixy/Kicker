package com.lechixy.kick.screens.clip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.channel.model.SubscriberBadge
import com.lechixy.kick.data.channel.repository.ClipRepository
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.RecordedChatTimeline
import com.lechixy.kick.data.chat.model.recordingStartInstant
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ClipPlayerUiState(
    val clip: ClipDetail? = null,
    val loading: Boolean = true,
    val chatMessages: List<ChatMessage> = emptyList(),
    val subscriberBadges: List<SubscriberBadge> = emptyList(),
    val chatLoading: Boolean = true,
    val chatAvailable: Boolean = true,
    val chatFailed: Boolean = false,
    val chatComplete: Boolean = false,
)

private const val CHAT_PAGE_MS = 5_000L

internal class ClipPlayerViewModel(private val clipId: String) : ViewModel() {
    private val repository = KickRepository(KickNetwork.api)
    private val _state = MutableStateFlow(ClipPlayerUiState())
    val state = _state.asStateFlow()
    private var metadataJob: Job? = null
    private var chatJob: Job? = null
    private var timeline: RecordedChatTimeline? = null
    private var requestedPage: Long? = null
    private val loadedPages = mutableSetOf<Long>()

    init { load() }

    fun load() {
        if (metadataJob?.isActive == true) return
        metadataJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val clip = ClipRepository.getClip(clipId)
                timeline = recordingStartInstant(clip.startedAt)?.let {
                    RecordedChatTimeline(it, endMs = clip.durationMs)
                }
                _state.update {
                    it.copy(clip = clip, loading = false, chatLoading = timeline != null, chatAvailable = timeline != null)
                }
                updatePosition(0L)
                loadSubscriberBadges(clip)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun updatePosition(positionMs: Long) {
        val clip = _state.value.clip ?: return
        val start = timeline?.start ?: return
        // Kick returns the five-second UTC bucket containing the requested instant.
        val startOffset = Math.floorMod(start.toEpochMilli(), CHAT_PAGE_MS)
        val lastPage = (startOffset + clip.durationMs) / CHAT_PAGE_MS
        val page = (startOffset + positionMs.coerceIn(0, clip.durationMs)) / CHAT_PAGE_MS
        if (page != requestedPage) loadChat(page, lastPage, startOffset)
    }

    fun retryChat(positionMs: Long) {
        requestedPage = null
        updatePosition(positionMs)
    }

    private fun loadChat(page: Long, lastPage: Long, startOffset: Long) {
        val clip = _state.value.clip ?: return
        val timeline = timeline ?: return
        requestedPage = page
        chatJob?.cancel()
        // Prioritize messages around the playhead, then fill any gaps left by a forward seek.
        val pages = (sequenceOf(page, page + 1, page - 1) + (0L until page).asSequence())
            .distinct().filter { it in 0..lastPage && it !in loadedPages }.toList()
        chatJob = viewModelScope.launch {
            _state.update { it.copy(chatLoading = pages.isNotEmpty(), chatFailed = false) }
            var failed = false
            for (target in pages) {
                try {
                    val boundary = (target * CHAT_PAGE_MS - startOffset).coerceAtLeast(0)
                    val requestTime = if (target == 0L) clip.startedAt else timeline.requestTime(boundary)
                    val history = repository.getRecordedChatHistory(clip.channel.id, requestTime)
                        ?: error("Missing recorded chat history")
                    timeline.add(history.messages, boundary)
                    loadedPages.add(target)
                    _state.update {
                        it.copy(
                            chatMessages = timeline.visible(clip.durationMs, limit = Int.MAX_VALUE),
                            chatComplete = loadedPages.size.toLong() == lastPage + 1,
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                }
            }
            _state.update { it.copy(chatLoading = false, chatFailed = failed) }
        }
    }

    private fun loadSubscriberBadges(clip: ClipDetail) {
        viewModelScope.launch {
            try {
                val channel = repository.getChannel(clip.channel.slug)
                _state.update { it.copy(subscriberBadges = channel.subscriberBadges) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Chat history still carries the selected v2 and standard badges.
            }
        }
    }
}
