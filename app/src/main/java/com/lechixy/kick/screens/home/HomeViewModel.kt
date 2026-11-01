package com.lechixy.kick.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.account.remote.userMessage
import com.lechixy.kick.data.common.model.FollowedLivestream
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val kickRepository: KickRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val auth = AccountGraph.auth
    private val json = Json { ignoreUnknownKeys = true }
    private var homeJob: Job? = null
    private var followingJob: Job? = null

    init {
        loadStreams()
        viewModelScope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect {
                followingJob?.cancel()
                _uiState.update { state -> state.copy(following = FollowingLiveState(signedIn = it.first)) }
                if (it.first) loadFollowing()
            }
        }
    }

    fun loadStreams() = loadHome(refreshing = false)

    fun refresh() {
        loadHome(refreshing = true)
        loadFollowing()
    }

    private fun loadHome(refreshing: Boolean) {
        homeJob?.cancel()
        homeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = !refreshing, isRefreshing = refreshing, error = null, categoriesError = null) }
            supervisorScope {
                launch {
                    try {
                        val categories = kickRepository.getCategories().data.sortedByDescending { it.viewers }
                        _uiState.update { it.copy(categories = categories, categoriesError = null) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _uiState.update { it.copy(categoriesError = error.userMessage()) }
                    }
                }
                launch {
                    try {
                        val language = settingsRepository.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).first()
                        val streams = kickRepository.getRecommendedLivestreams(language)
                        _uiState.update { it.copy(streams = streams) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        _uiState.update { it.copy(error = error.userMessage()) }
                    }
                }
                launch {
                    try {
                        val totals = kickRepository.getLiveTotals()
                        _uiState.update { it.copy(liveTotals = totals) }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        // Optional totals must not prevent recommendations from loading.
                    }
                }
            }
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    fun loadFollowing() {
        followingJob?.cancel()
        if (!auth.session.value.signedIn) return
        followingJob = viewModelScope.launch {
            val generation = auth.session.value.generation
            _uiState.update { it.copy(following = it.following.copy(loading = true, error = null)) }
            try {
                val response = auth.requestArray("https://kick.com/api/v1/user/livestreams")
                val streams = json.decodeFromJsonElement<List<FollowedLivestream>>(response)
                    .filter { it.isLive }.map { it.toLivestream() }.distinctBy { it.channel?.slug }
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    _uiState.update { it.copy(following = FollowingLiveState(signedIn = true, streams = streams, loaded = true)) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    _uiState.update { it.copy(following = it.following.copy(loading = false, error = error.userMessage())) }
                }
            }
        }
    }
}
