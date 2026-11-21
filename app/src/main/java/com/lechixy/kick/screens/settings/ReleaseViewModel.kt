package com.lechixy.kick.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.data.releases.*
import com.lechixy.kick.data.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReleaseViewModel @Inject constructor(private val settings: SettingsRepository, private val releases: ReleaseRepository) : ViewModel() {
    private val pending = MutableStateFlow<List<ReleaseDialogRequest>>(emptyList())
    val dialogs = pending.asStateFlow()
    private val historyState = MutableStateFlow(ReleaseHistoryState(loading = true))
    val history = historyState.asStateFlow()
    private var historyJob: kotlinx.coroutines.Job? = null
    init {
        historyJob = viewModelScope.launch {
            val current = BuildConfig.VERSION_NAME
            val whatsNew = settings.recordStartupVersion(current)
            val fetched = refreshHistory(versionForNotes = current.takeIf { whatsNew })
            val requests = buildList {
                if (whatsNew) add(ReleaseDialogRequest(ReleaseDialogMode.WhatsNew,
                    fetched.firstOrNull { compareVersions(it.tagName, current) == 0 } ?: GitHubRelease(tagName = current)))
                newestRelease(fetched, current)?.let { add(ReleaseDialogRequest(ReleaseDialogMode.UpdateAvailable, it)) }
            }
            pending.update { requests + it }
        }
    }
    fun loadHistory(retry: Boolean = false) {
        if (historyJob?.isActive == true) return
        if (retry) historyState.update { it.copy(loading = true, failed = false) }
        historyJob = viewModelScope.launch {
            refreshHistory(retry)
        }
    }

    private suspend fun refreshHistory(retry: Boolean = false, versionForNotes: String? = null): List<GitHubRelease> {
        val response = releases.releases(retry, versionForNotes)
        historyState.value = ReleaseHistoryState(response.releases, loaded = !response.refreshFailed, failed = response.refreshFailed)
        return response.releases
    }
    fun dismiss() { pending.update { it.drop(1) } }
}

data class ReleaseHistoryState(val releases: List<GitHubRelease> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val failed: Boolean = false) {
    val latestPublicationTime = releases.asSequence()
        .filterNot { it.draft }
        .mapNotNull { it.publicationTime }
        .maxOrNull()
}
