package com.lechixy.kick.screens.events

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.EventClip
import com.lechixy.kick.data.channel.model.EventStream
import com.lechixy.kick.data.common.repository.KickRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class EventDetailUiState(
    val event: ChannelEvent? = null,
    val streams: List<EventStream>? = null,
    val clips: List<EventClip> = emptyList(),
    val loadingEvent: Boolean = false,
    val loadingStreams: Boolean = false,
    val loadingClips: Boolean = false,
    val eventFailed: Boolean = false,
    val streamsFailed: Boolean = false,
    val clipsFailed: Boolean = false
) {
    val isLoading get() = loadingEvent || loadingStreams || loadingClips
}

@HiltViewModel
internal class EventDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KickRepository
) : ViewModel() {
    val eventSlug = checkNotNull(savedState.get<String>("slug"))
    private val mutableState = MutableStateFlow(EventDetailUiState())
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (mutableState.value.isLoading) return
        mutableState.update {
            it.copy(
                loadingEvent = true, loadingStreams = true, loadingClips = true,
                eventFailed = false, streamsFailed = false, clipsFailed = false
            )
        }
        viewModelScope.launch {
            val event = load { repository.getEvent(eventSlug) }
            mutableState.update { it.copy(event = event ?: it.event, loadingEvent = false, eventFailed = event == null) }
        }
        viewModelScope.launch {
            val streams = load { repository.getEventStreams(eventSlug) }
            mutableState.update { it.copy(streams = streams ?: it.streams, loadingStreams = false, streamsFailed = streams == null) }
        }
        viewModelScope.launch {
            val clips = load { repository.getEventClips(eventSlug) }
            mutableState.update { it.copy(clips = clips ?: it.clips, loadingClips = false, clipsFailed = clips == null) }
        }
    }

    private suspend fun <T> load(fetch: suspend () -> T): T? = try {
        fetch()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}
