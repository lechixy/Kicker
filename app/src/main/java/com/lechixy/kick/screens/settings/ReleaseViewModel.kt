package com.lechixy.kick.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.BuildConfig
import com.lechixy.kick.data.releases.*
import com.lechixy.kick.data.settings.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
            var failed = false
            val fetched = try { releases.releases() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true; emptyList() }
            historyState.value = ReleaseHistoryState(fetched, loaded = !failed, failed = failed)
            val requests = buildList {
                if (whatsNew) add(ReleaseDialogRequest(ReleaseDialogMode.WhatsNew,
                    fetched.firstOrNull { compareVersions(it.tagName, current) == 0 } ?: GitHubRelease(tagName = current)))
                newestRelease(fetched, current)?.let { add(ReleaseDialogRequest(ReleaseDialogMode.UpdateAvailable, it)) }
            }
            pending.update { requests + it }
        }
    }
    fun loadHistory(retry: Boolean = false) {
        if (historyJob?.isActive == true || (history.value.loaded && !retry)) return
        historyState.update { it.copy(loading = true, failed = false) }
        historyJob = viewModelScope.launch {
            try { historyState.value = ReleaseHistoryState(releases.releases(), loaded = true) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { historyState.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun dismiss() { pending.update { it.drop(1) } }
    fun preview(mode: ReleaseDialogMode) {
        val sample = GitHubRelease(tagName = if (mode == ReleaseDialogMode.WhatsNew) BuildConfig.VERSION_NAME else "99.0.0",
            name = null, body = "# Kick\n\n- **Chat** & playback improvements\n- In-app notifications\n\n### Preview\n*Markdown* with `inline code`.\n\n```kotlin\nval version = \"99.0.0\"\n```", publishedAt = "2026-11-01T12:00:00Z")
        pending.update { it + ReleaseDialogRequest(mode, sample) }
    }
}

data class ReleaseHistoryState(val releases: List<GitHubRelease> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val failed: Boolean = false)
