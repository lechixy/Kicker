package com.lechixy.kick.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

import com.lechixy.kick.data.browse.model.SearchData
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository

data class BrowseUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val searchData: SearchData? = null,
    val error: String? = null
)

class BrowseViewModel(
    private val repository: KickRepository = KickRepository(KickNetwork.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                searchData = null,
                error = null
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(350.milliseconds) // Debounce
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val data = repository.search(newQuery.trim())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchData = data,
                    error = null
                )
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Search failed"
                    )
                }
            }
        }
    }
}