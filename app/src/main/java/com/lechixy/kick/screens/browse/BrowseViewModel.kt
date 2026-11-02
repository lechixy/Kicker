package com.lechixy.kick.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import com.lechixy.kick.data.account.remote.userMessage
import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.common.remote.KickNetwork
import com.lechixy.kick.data.common.repository.KickRepository

data class BrowseUiState(val categories: List<BrowseCategory> = emptyList(), val loading: Boolean = true,
    val page: Int = 0, val lastPage: Int = 1, val error: String? = null, val loadingMore: Boolean = false)
class BrowseViewModel : ViewModel() {
    private val repository = KickRepository(KickNetwork.api)
    private val mutableState = MutableStateFlow(BrowseUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init { refresh() }
    fun refresh() { job?.cancel(); load(true) }
    fun more() { if (!state.value.loading && !state.value.loadingMore && state.value.page < state.value.lastPage) load(false) }
    private fun load(reset: Boolean) {
        job = viewModelScope.launch {
            mutableState.update { it.copy(loading = reset, loadingMore = !reset, error = null) }
            try {
                val result = repository.getCategories(if (reset) 1 else state.value.page + 1)
                mutableState.update { it.copy(categories = ((if (reset) emptyList() else it.categories) + result.data)
                    .distinctBy { it.slug }.sortedByDescending { it.viewers }, page = result.currentPage,
                    lastPage = result.lastPage, loading = false, loadingMore = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutableState.update { it.copy(loading = false, loadingMore = false, error = e.userMessage()) }
            }
        }
    }
}
