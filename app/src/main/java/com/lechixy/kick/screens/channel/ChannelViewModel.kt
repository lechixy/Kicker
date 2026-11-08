package com.lechixy.kick.screens.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.ChannelLivestream
import com.lechixy.kick.data.channel.model.ChannelSectionState
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.KickChannelLinkDto
import com.lechixy.kick.data.channel.model.KickGoalDto
import com.lechixy.kick.data.chat.model.ChatHistoryData
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.data.chat.model.ChatWebSocketEvent
import com.lechixy.kick.data.chat.model.PinnedMessageInfo
import com.lechixy.kick.data.chat.model.PointsUpdatedEvent
import com.lechixy.kick.data.chat.model.StopStreamBroadcastEvent
import com.lechixy.kick.data.chat.model.StreamerIsLiveEvent
import com.lechixy.kick.data.chat.remote.ChatConnectionState
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository

data class ChannelUiState(
    val isLoading: Boolean = true,
    val channel: ChannelDetail? = null,
    val event: ChannelEvent? = null,
    val videos: List<ChannelVideo> = emptyList(),
    val clips: List<ChannelClip> = emptyList(),
    val links: ChannelSectionState<List<KickChannelLinkDto>> = ChannelSectionState(),
    val goals: ChannelSectionState<List<KickGoalDto>> = ChannelSectionState(),
    val popularClips: ChannelSectionState<List<ChannelClip>> = ChannelSectionState(loading = true),
    val recentCategories: ChannelSectionState<List<com.lechixy.kick.data.browse.model.BrowseCategory>> = ChannelSectionState(loading = true),
    val clipsError: String? = null,
    val isClipsLoading: Boolean = true,
    val clipSort: ChannelMediaSort = ChannelMediaSort.RECENT,
    val clipTime: ChannelClipTime = ChannelClipTime.ALL,
    val clipCursor: String? = null,
    val chatHistory: ChatHistoryData? = null,
    val error: String? = null,
    val liveAnnouncementId: Long? = null,
    val lastHourGiftedSubsRank: Int? = null,
    val raid: com.lechixy.kick.data.chat.model.ChannelRaid? = null
)

class ChannelViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    companion object {
        private const val TAG = "ChannelViewModel"
        private const val VIEWERS_POLL_INTERVAL_MS = 60_000L
        private const val LIVE_METADATA_RETRY_DELAY_MS = 2_500L
        private const val LIVE_METADATA_MAX_ATTEMPTS = 4
    }

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    val highTraffic = repository.chatWebSocketClient.highTraffic
    val chatMessages: StateFlow<List<ChatMessage>> = repository.chatWebSocketClient.messages
    val pinnedMessage: StateFlow<PinnedMessageInfo?> = repository.chatWebSocketClient.pinnedMessage
    val userColors: StateFlow<Map<String, String>> = repository.chatWebSocketClient.userColors
    val chatEvents: SharedFlow<ChatWebSocketEvent> = repository.chatWebSocketClient.events
    val pointsUpdated: StateFlow<PointsUpdatedEvent?> = repository.chatWebSocketClient.pointsUpdated
    val chatConnectionState: StateFlow<ChatConnectionState> = repository.chatWebSocketClient.connectionState

    private val _chatSettings = MutableStateFlow(kotlinx.serialization.json.JsonObject(emptyMap()))
    val chatSettings = _chatSettings.asStateFlow()
    private val _emotes = MutableStateFlow(com.lechixy.kick.data.chat.model.EmotePickerUiState(isLoading = true))
    val emotes = _emotes.asStateFlow()
    private var emotesJob: Job? = null
    private var livePlaybackRefreshJob: Job? = null
    private var channelLoadJob: Job? = null
    private var clipsJob: Job? = null
    private val emoteCache = mutableMapOf<String, List<com.lechixy.kick.data.chat.model.EmoteCategory>>()

    fun loadEmotes(retry: Boolean = false) {
        val slug = loadedSlug ?: return
        val cached = emoteCache[slug].takeUnless { retry }
        if (cached != null) {
            _emotes.value = com.lechixy.kick.data.chat.model.EmotePickerUiState(categories = cached)
            return
        }
        if (emotesJob?.isActive == true && !retry) return
        emotesJob?.cancel()
        _emotes.value = com.lechixy.kick.data.chat.model.EmotePickerUiState(isLoading = true)
        emotesJob = viewModelScope.launch {
            try {
                val categories = if (com.lechixy.kick.data.account.AccountGraph.auth.session.value.signedIn)
                    com.lechixy.kick.data.account.AccountGraph.chat.emotes(slug) else repository.getEmotes(slug)
                emoteCache[slug] = categories
                if (loadedSlug == slug) _emotes.value = com.lechixy.kick.data.chat.model.EmotePickerUiState(categories = categories)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (loadedSlug == slug) _emotes.value = com.lechixy.kick.data.chat.model.EmotePickerUiState(error = "load_failed")
            }
        }
    }
    private val seenRules = mutableSetOf<Pair<Long, Long>>()
    fun rulesSeen(generation: Long, channelId: Long) = (generation to channelId) in seenRules
    fun markRulesSeen(generation: Long, channelId: Long) { seenRules.add(generation to channelId) }

    private var viewersPollingJob: Job? = null
    private var liveRefreshJob: Job? = null
    private var leaderboardJob: Job? = null
    private var aboutJob: Job? = null
    private var loadedSlug: String? = null

    private var observedAccount: Pair<Long, Long?>? = null

    init {
        observeWebSocketEvents()
        viewModelScope.launch {
            com.lechixy.kick.data.account.AccountGraph.auth.session
                .collect { session ->
                    if (observedAccount != (session.generation to session.profile?.userId)) {
                        observedAccount = session.generation to session.profile?.userId
                        emotesJob?.cancel()
                        emoteCache.clear()
                        loadEmotes(retry = true)
                        repository.chatWebSocketClient.refreshAccountConnection()
                    }
                }
        }
    }

    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            repository.chatWebSocketClient.events.collect { wsEvent ->
                when (wsEvent) {
                    is ChatWebSocketEvent.SettingsChanged -> _chatSettings.update {
                        com.lechixy.kick.data.chat.policy.ChatSettingsUpdates.merge(it, wsEvent.settings)
                    }
                    is ChatWebSocketEvent.Raid -> _uiState.update {
                        if (!recordingChat && it.channel?.id == wsEvent.event.channel.id) it.copy(raid = wsEvent.event) else it
                    }
                    is ChatWebSocketEvent.StreamerIsLive -> handleStreamerIsLive(wsEvent.event)
                    is ChatWebSocketEvent.StopStreamBroadcast -> handleStopStreamBroadcast(wsEvent.event)
                    else -> Unit
                }
            }
        }
    }

    fun dismissRaid() { _uiState.update { it.copy(raid = null) } }

    fun consumeLiveAnnouncement(announcementId: Long) {
        _uiState.update { current ->
            if (current.liveAnnouncementId == announcementId) {
                current.copy(liveAnnouncementId = null)
            } else {
                current
            }
        }
    }

    private fun handleStreamerIsLive(event: StreamerIsLiveEvent) {
        val liveInfo = event.livestream ?: return
        val currentChannel = _uiState.value.channel ?: return
        if (liveInfo.channelId != null && liveInfo.channelId != currentChannel.id) return

        liveRefreshJob?.cancel()

        val normalizedStartTime = liveInfo.createdAt
            ?.trim()
            ?.replace('T', ' ')
            ?.substringBefore('.')
            ?.trimEnd('Z')
            ?.takeIf { it.isNotBlank() }

        val optimisticLivestream = ChannelLivestream(
            id = liveInfo.id,
            channelId = liveInfo.channelId ?: currentChannel.id,
            sessionTitle = liveInfo.sessionTitle ?: currentChannel.livestream?.sessionTitle,
            source = liveInfo.source ?: currentChannel.livestream?.source,
            createdAt = liveInfo.createdAt ?: currentChannel.livestream?.createdAt,
            startTime = normalizedStartTime ?: currentChannel.livestream?.startTime,
            isLive = true,
            viewerCount = currentChannel.livestream?.viewerCount ?: 0,
            language = currentChannel.livestream?.language,
            langIso = currentChannel.livestream?.langIso,
            isMature = currentChannel.livestream?.isMature ?: false,
            thumbnail = currentChannel.livestream?.thumbnail,
            tags = currentChannel.livestream?.tags.orEmpty(),
            categories = currentChannel.livestream?.categories?.ifEmpty { currentChannel.recentCategories }
                ?: currentChannel.recentCategories
        )

        val announcementId = System.nanoTime()
        _uiState.update { current ->
            val ch = current.channel ?: return@update current
            current.copy(
                channel = ch.copy(livestream = optimisticLivestream),
                liveAnnouncementId = announcementId
            )
        }

        startViewersPolling(liveInfo.id)

        val slug = loadedSlug ?: currentChannel.slug
        liveRefreshJob = viewModelScope.launch {
            repeat(LIVE_METADATA_MAX_ATTEMPTS) { attempt ->
                if (attempt > 0) {
                    delay(LIVE_METADATA_RETRY_DELAY_MS.milliseconds)
                }
                try {
                    val freshChannel = repository.getChannel(slug)
                    val freshEvent = runCatching { repository.getChannelEvent(freshChannel.id) }.getOrNull()
                    var hasCompleteLiveMetadata = false

                    _uiState.update { current ->
                        val currentCh = current.channel ?: return@update current
                        // StopStreamBroadcast geldiyse canlı yayını tekrar açma
                        if (currentCh.livestream == null) return@update current

                        val mergedLivestream = freshChannel.livestream?.copy(
                            sessionTitle = freshChannel.livestream.sessionTitle
                                ?: liveInfo.sessionTitle
                                ?: currentCh.livestream.sessionTitle,
                            startTime = freshChannel.livestream.startTime
                                ?: normalizedStartTime
                                ?: currentCh.livestream.startTime
                        ) ?: currentCh.livestream.copy(
                            sessionTitle = liveInfo.sessionTitle ?: currentCh.livestream.sessionTitle
                        )

                        hasCompleteLiveMetadata =
                            freshChannel.livestream != null && !freshChannel.playbackUrl.isNullOrBlank()

                        current.copy(
                            channel = freshChannel.copy(
                                playbackUrl = freshChannel.playbackUrl ?: currentCh.playbackUrl,
                                livestream = mergedLivestream
                            ),
                            event = freshEvent ?: current.event
                        )
                    }

                    if (hasCompleteLiveMetadata) return@launch
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleStopStreamBroadcast(event: StopStreamBroadcastEvent) {
        val currentChannel = _uiState.value.channel ?: return
        val targetChannelId = event.livestream?.channel?.id
        if (targetChannelId != null && targetChannelId != currentChannel.id) return

        liveRefreshJob?.cancel()
        viewersPollingJob?.cancel()

        val updatedIsBanned = event.livestream?.channel?.isBanned ?: currentChannel.isBanned
        _uiState.update { current ->
            val ch = current.channel ?: return@update current
            current.copy(
                channel = ch.copy(
                    livestream = null,
                    isBanned = updatedIsBanned
                ),
                liveAnnouncementId = null
            )
        }

        val slug = loadedSlug ?: currentChannel.slug
        viewModelScope.launch {
            try {
                val updatedVideos = repository.getChannelVideos(slug).filterNot { it.isLive }
                _uiState.update { it.copy(videos = updatedVideos) }
            } catch (_: Exception) {
            }
        }
    }

    fun loadChannel(slug: String, autoConnectChat: Boolean = true) {
        channelLoadJob?.cancel()
        channelLoadJob = viewModelScope.launch {
            if (loadedSlug != null && !loadedSlug.equals(slug, ignoreCase = true)) {
                repository.chatWebSocketClient.disconnect()
                _chatSettings.value = kotlinx.serialization.json.JsonObject(emptyMap())
            }
            aboutJob?.cancel()
            clipsJob?.cancel()
            loadedSlug = slug
            emotesJob?.cancel()
            loadEmotes()
            liveRefreshJob?.cancel()
            viewersPollingJob?.cancel()
            leaderboardJob?.cancel()
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, liveAnnouncementId = null, lastHourGiftedSubsRank = null, raid = null)

            try {
                val channelDeferred = async { repository.getChannel(slug) }
                val videosDeferred = async {
                    try {
                        repository.getChannelVideos(slug).filterNot { it.isLive }
                    } catch (_: Exception) {
                        emptyList<ChannelVideo>()
                    }
                }

                val channel = channelDeferred.await()
                val eventDeferred = async {
                    try {
                        repository.getChannelEvent(channel.id)
                    } catch (_: Exception) {
                        null
                    }
                }
                val historyDeferred = async {
                    try {
                        repository.getChatHistory(channel.id)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load chat history for channelId=${channel.id}", e)
                        null
                    }
                }
                val videos = videosDeferred.await()
                val event = eventDeferred.await()
                val history = historyDeferred.await()

                if (loadedSlug != slug) return@launch
                _uiState.value = ChannelUiState(
                    isLoading = false,
                    channel = channel,
                    event = event,
                    videos = videos,
                    chatHistory = history,
                    error = null
                )

                // Clip loading must not delay live playback or channel metadata.
                startLeaderboardPolling(channel.id)
                loadClips()
                loadHomeSections()
                channel.chatroom?.id?.let { chatroomId ->
                    repository.chatWebSocketClient.seedHistory(channel.id, chatroomId, history?.messages.orEmpty())
                }
                if (autoConnectChat) {
                    connectChat()
                }

                repository.chatWebSocketClient.updatePinnedMessage(history?.pinnedMessage)
                repository.chatWebSocketClient.cacheUserColorsFromMessages(history?.messages.orEmpty())

                channel.livestream?.id?.let { livestreamId ->
                    startViewersPolling(livestreamId)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _uiState.value = ChannelUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load channel"
                )
            }
        }
    }

    fun loadAbout(retry: Boolean = false) {
        val channel = _uiState.value.channel ?: return
        if (aboutJob?.isActive == true) return
        val links = _uiState.value.links
        val goals = _uiState.value.goals
        val fetchLinks = !links.loaded && (!links.failed || retry)
        val fetchGoals = !goals.loaded && (!goals.failed || retry)
        if (!fetchLinks && !fetchGoals) return
        aboutJob = viewModelScope.launch {
            if (fetchLinks) launch {
                _uiState.update { it.copy(links = ChannelSectionState(loading = true)) }
                val result = try { ChannelSectionState(value = repository.getChannelLinks(channel.slug), loaded = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { ChannelSectionState<List<KickChannelLinkDto>>(failed = true) }
                _uiState.update { if (it.channel?.id == channel.id) it.copy(links = result) else it }
            }
            if (fetchGoals) launch {
                _uiState.update { it.copy(goals = ChannelSectionState(loading = true)) }
                val result = try { ChannelSectionState(value = repository.getChannelGoals(channel.slug), loaded = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { ChannelSectionState<List<KickGoalDto>>(failed = true) }
                _uiState.update { if (it.channel?.id == channel.id) it.copy(goals = result) else it }
            }
        }
    }

    fun loadHomeSections() {
        val channel = _uiState.value.channel ?: return
        viewModelScope.launch {
            launch {
                _uiState.update { it.copy(popularClips = ChannelSectionState(loading = true)) }
                val result = try { ChannelSectionState(value = repository.getPopularChannelClips(channel.id), loaded = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { ChannelSectionState<List<ChannelClip>>(failed = true) }
                _uiState.update { if (it.channel?.id == channel.id) it.copy(popularClips = result) else it }
            }
            launch {
                _uiState.update { it.copy(recentCategories = ChannelSectionState(loading = true)) }
                val result = try { ChannelSectionState(value = repository.getRecentChannelCategories(channel.id), loaded = true) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { ChannelSectionState<List<com.lechixy.kick.data.browse.model.BrowseCategory>>(failed = true) }
                _uiState.update { if (it.channel?.id == channel.id) it.copy(recentCategories = result) else it }
            }
        }
    }

    fun retryClips() {
        loadClips(append = _uiState.value.clips.isNotEmpty())
    }

    fun updateClipFilters(sort: ChannelMediaSort, time: ChannelClipTime) {
        val state = _uiState.value
        if (state.isLoading || (state.clipSort == sort && state.clipTime == time)) return
        _uiState.update { it.copy(clipSort = sort, clipTime = time) }
        loadClips()
    }

    fun loadMoreClips() {
        if (!_uiState.value.isClipsLoading) loadClips(append = true)
    }

    private fun loadClips(append: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading) return
        val channel = state.channel ?: return
        val slug = loadedSlug ?: return
        val cursor = if (append) state.clipCursor ?: return else null
        clipsJob?.cancel()
        _uiState.update {
            it.copy(
                clips = if (append) it.clips else emptyList(),
                clipCursor = cursor,
                isClipsLoading = true,
                clipsError = null
            )
        }
        clipsJob = viewModelScope.launch {
            try {
                val page = repository.getChannelClips(channel.id, state.clipSort.query, state.clipTime.query, cursor)
                if (loadedSlug == slug) _uiState.update {
                    it.copy(
                        clips = (it.clips + page.clips).distinctBy(ChannelClip::id),
                        clipCursor = page.cursor?.takeIf { it.isNotBlank() && it != cursor },
                        isClipsLoading = false
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (loadedSlug == slug) _uiState.update {
                    it.copy(isClipsLoading = false, clipsError = "load_failed")
                }
            }
        }
    }

    private fun startViewersPolling(livestreamId: Long) {
        viewersPollingJob?.cancel()
        viewersPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(VIEWERS_POLL_INTERVAL_MS.milliseconds)
                updateLivestreamViewers(livestreamId)
            }
        }
    }

    private fun startLeaderboardPolling(channelId: Long) {
        leaderboardJob?.cancel()
        leaderboardJob = viewModelScope.launch {
            while (isActive) {
                val rank = try { repository.getLastHourGiftedSubsRank(channelId) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                _uiState.update { if (it.channel?.id == channelId) it.copy(lastHourGiftedSubsRank = rank) else it }
                delay(60_000L.milliseconds)
            }
        }
    }

    suspend fun updateLivestreamViewers(livestreamId: Long) {
        try {
            val viewerInfo = repository.getCurrentViewers(livestreamId) ?: return
            _uiState.update { current ->
                val currentChannel = current.channel
                if (currentChannel?.livestream != null && currentChannel.livestream.id == livestreamId) {
                    current.copy(
                        channel = currentChannel.copy(
                            livestream = currentChannel.livestream.copy(
                                viewerCount = viewerInfo.viewers
                            )
                        )
                    )
                } else {
                    current
                }
            }
        } catch (_: Exception) {
            // Ağ veya API hatalarında kullanıcı deneyimini kesintiye uğratma
        }
    }

    fun refreshLivePlayback(onReady: (ChannelDetail) -> Unit) {
        val requested = _uiState.value.channel ?: return
        livePlaybackRefreshJob?.cancel()
        livePlaybackRefreshJob = viewModelScope.launch {
            // The live HLS URL may have expired or the broadcast may have changed while watching a recording.
            val fresh = try {
                repository.getChannel(requested.slug)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                requested
            }
            if (_uiState.value.channel?.id != requested.id) return@launch
            _uiState.update { it.copy(channel = fresh) }
            if (fresh.livestream != null && !fresh.playbackUrl.isNullOrBlank()) onReady(fresh)
        }
    }

    private var recordingChat = false
    fun setRecordingChat(enabled: Boolean) {
        recordingChat = enabled
        if (enabled) { dismissRaid(); disconnectChat() }
    }

    fun connectChat() {
        if (recordingChat) return
        val channel = _uiState.value.channel ?: return
        val chatroomId = channel.chatroom?.id ?: return
        val existingHistory = _uiState.value.chatHistory
        if (existingHistory != null) {
            repository.chatWebSocketClient.seedHistory(channel.id, chatroomId, existingHistory.messages)
        }
        repository.chatWebSocketClient.connect(
            channelId = channel.id,
            chatroomId = chatroomId
        )
        if (existingHistory != null) {
            if (repository.chatWebSocketClient.pinnedMessage.value == null && existingHistory.pinnedMessage != null) {
                repository.chatWebSocketClient.updatePinnedMessage(existingHistory.pinnedMessage)
            }
            repository.chatWebSocketClient.cacheUserColorsFromMessages(existingHistory.messages)
        } else {
            viewModelScope.launch {
                try {
                    val history = repository.getChatHistory(channel.id)
                    if (_uiState.value.channel?.id != channel.id) return@launch
                    repository.chatWebSocketClient.seedHistory(channel.id, chatroomId, history?.messages.orEmpty())
                    repository.chatWebSocketClient.updatePinnedMessage(history?.pinnedMessage)
                    repository.chatWebSocketClient.cacheUserColorsFromMessages(history?.messages.orEmpty())
                    if (history != null) {
                        _uiState.update { it.copy(chatHistory = history) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load chat history in connectChat for channelId=${channel.id}", e)
                }
            }
        }
    }

    fun disconnectChat() {
        repository.chatWebSocketClient.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        liveRefreshJob?.cancel()
        viewersPollingJob?.cancel()
        repository.chatWebSocketClient.disconnect()
    }
}
enum class ChannelMediaSort(val label: Int, val query: String) {
    RECENT(R.string.channel_sort_recent, "date"),
    VIEWS(R.string.channel_sort_views, "views")
}

enum class ChannelClipTime(val label: Int, val query: String) {
    ALL(R.string.channel_clip_all_time, "all"),
    DAY(R.string.channel_clip_day, "day"),
    WEEK(R.string.channel_clip_week, "week"),
    MONTH(R.string.channel_clip_month, "month")
}
