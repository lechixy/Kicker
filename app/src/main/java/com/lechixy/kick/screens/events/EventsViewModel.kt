package com.lechixy.kick.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.common.repository.KickRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

internal data class EventsScreenUiState(
    val isLoading: Boolean = true,
    val failedFeeds: Int = 0,
    val featuredEvent: ChannelEvent? = null,
    val liveEvents: List<ChannelEvent> = emptyList(),
    val upcomingEvents: List<ChannelEvent> = emptyList(),
    val reminderIds: Set<String> = emptySet(),
    val pendingReminderIds: Set<String> = emptySet()
) {
    val hasEvents get() = featuredEvent != null || liveEvents.isNotEmpty() || upcomingEvents.isNotEmpty()
}

@HiltViewModel
internal class EventsViewModel @Inject constructor(private val repository: KickRepository) : ViewModel() {
    private val state = MutableStateFlow(EventsScreenUiState(isLoading = false))
    val uiState = state.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val snackbarMessages = messages.receiveAsFlow()
    private val auth = AccountGraph.auth
    private var reminderGeneration = auth.session.value.generation

    init {
        viewModelScope.launch {
            auth.session.map { it.generation }.distinctUntilChanged().collect(::updateReminderSession)
        }
        refresh()
    }

    fun refresh() {
        if (state.value.isLoading) return
        state.update { it.copy(isLoading = true, failedFeeds = 0) }
        viewModelScope.launch {
            coroutineScope {
                val featured = async { loadFeed { repository.browseEvents(collection = "featured") } }
                val live = async { loadFeed { repository.browseEvents(status = "live") } }
                val upcoming = async { loadFeed { repository.browseEvents(status = "upcoming") } }
                val featuredEvents = featured.await()
                val liveEvents = live.await()
                val upcomingEvents = upcoming.await()
                state.update {
                    it.copy(
                        isLoading = false,
                        failedFeeds = listOf(featuredEvents, liveEvents, upcomingEvents).count { feed -> feed == null },
                        featuredEvent = if (featuredEvents == null) it.featuredEvent else featuredEvents.firstOrNull(),
                        liveEvents = liveEvents ?: it.liveEvents,
                        upcomingEvents = upcomingEvents ?: it.upcomingEvents
                    )
                }
            }
        }
    }

    fun toggleReminder(id: String) {
        val session = auth.session.value
        if (!session.signedIn) {
            messages.trySend(R.string.events_reminder_sign_in)
            return
        }
        updateReminderSession(session.generation)
        if (id in state.value.pendingReminderIds) return
        val wasSet = id in state.value.reminderIds
        state.update {
            it.copy(
                reminderIds = if (wasSet) it.reminderIds - id else it.reminderIds + id,
                pendingReminderIds = it.pendingReminderIds + id
            )
        }
        viewModelScope.launch {
            val url = HttpUrl.Builder().scheme("https").host("web.kick.com")
                .addPathSegments("api/v1/events").addPathSegment(id).addPathSegment("reminder").build()
            try {
                auth.requestElement(url.toString(), if (wasSet) "DELETE" else "POST")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (session.generation == auth.session.value.generation) {
                    state.update {
                        it.copy(reminderIds = if (wasSet) it.reminderIds + id else it.reminderIds - id)
                    }
                }
                messages.trySend(R.string.events_reminder_failed)
            } finally {
                if (session.generation == reminderGeneration) {
                    state.update { it.copy(pendingReminderIds = it.pendingReminderIds - id) }
                }
            }
        }
    }

    private fun updateReminderSession(generation: Long) {
        if (generation == reminderGeneration) return
        reminderGeneration = generation
        state.update { it.copy(reminderIds = emptySet(), pendingReminderIds = emptySet()) }
    }

    private suspend fun loadFeed(load: suspend () -> List<ChannelEvent>): List<ChannelEvent>? =
        try {
            load()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}
