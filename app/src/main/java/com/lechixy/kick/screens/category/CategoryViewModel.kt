package com.lechixy.kick.screens.category

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lechixy.kick.R
import com.lechixy.kick.data.account.AccountGraph
import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.channel.model.ChannelClip
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.data.common.repository.KickRepository
import com.lechixy.kick.data.settings.model.SettingsKeys
import com.lechixy.kick.data.settings.repository.SettingsRepository
import com.lechixy.kick.util.preferredStreamLanguageCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class CategoryTab(@StringRes val label: Int) {
    LIVESTREAMS(R.string.events_live_streams),
    CLIPS(R.string.clips),
}

internal enum class CategoryStreamSort(val query: String, @StringRes val label: Int) {
    VIEWERS("viewer_count_desc", R.string.category_sort_viewers),
    FEATURED("featured", R.string.category_sort_featured),
}

@Immutable
internal data class CategoryUiState(
    val category: BrowseCategory? = null,
    val tab: CategoryTab = CategoryTab.LIVESTREAMS,
    val streams: List<Livestream> = emptyList(),
    val clips: List<ChannelClip> = emptyList(),
    val sort: CategoryStreamSort = CategoryStreamSort.VIEWERS,
    val language: String? = null,
    val nextCursor: String? = null,
    val loadingCategory: Boolean = true,
    val loadingContent: Boolean = true,
    val loadingMore: Boolean = false,
    val followingPending: Boolean = false,
    val categoryFailed: Boolean = false,
    val contentFailed: Boolean = false,
)

@HiltViewModel
internal class CategoryViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val repository: KickRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val slug = checkNotNull(savedState.get<String>("slug"))
    private val auth = AccountGraph.auth
    private val following = AccountGraph.following
    val session = auth.session

    private val mutableState = MutableStateFlow(
        CategoryUiState(
            tab = CategoryTab.entries.firstOrNull { it.name == savedState.get<String>("tab") }
                ?: CategoryTab.LIVESTREAMS,
            sort = CategoryStreamSort.entries.firstOrNull { it.name == savedState.get<String>("sort") }
                ?: CategoryStreamSort.VIEWERS,
            language = savedState["language"],
        )
    )
    val state = mutableState.asStateFlow()
    private val messages = Channel<Int>(Channel.BUFFERED)
    val snackbarMessages = messages.receiveAsFlow()
    private var displayLanguage: String? = null
    private var categoryJob: Job? = null
    private var contentJob: Job? = null

    init {
        viewModelScope.launch {
            settings.get(SettingsKeys.RECOMMENDED_LIVESTREAM_LANGUAGE).collect { preference ->
                val language = preferredStreamLanguageCode(preference) ?: "en"
                if (displayLanguage != language) {
                    displayLanguage = language
                    if (state.value.tab == CategoryTab.LIVESTREAMS) loadContent()
                }
            }
        }
        viewModelScope.launch {
            session.map { it.generation to it.signedIn }.distinctUntilChanged().collect {
                mutableState.update {
                    it.copy(category = it.category?.copy(followed = false), followingPending = false)
                }
                loadCategory()
            }
        }
    }

    fun refresh() {
        if (state.value.followingPending) loadContent() else loadCategory()
    }

    fun selectTab(tab: CategoryTab) {
        if (tab == state.value.tab) return
        savedState["tab"] = tab.name
        mutableState.update { it.copy(tab = tab) }
        loadContent()
    }

    fun selectSort(sort: CategoryStreamSort) {
        if (sort == state.value.sort) return
        savedState["sort"] = sort.name
        mutableState.update { it.copy(sort = sort) }
        loadContent()
    }

    fun selectLanguage(language: String?) {
        if (language == state.value.language) return
        savedState["language"] = language
        mutableState.update { it.copy(language = language) }
        loadContent()
    }

    fun retryContent() = loadContent(state.value.nextCursor)

    fun loadMore() {
        val current = state.value
        if (current.loadingContent || current.loadingMore || current.contentFailed) return
        current.nextCursor?.let { loadContent(it) }
    }

    fun toggleFollow() {
        val current = state.value
        val category = current.category ?: return
        val account = session.value
        if (!account.signedIn || current.loadingCategory || current.followingPending) return
        val follow = !category.followed
        mutableState.update {
            it.copy(
                category = category.copy(
                    followed = follow,
                    followersCount = (category.followersCount + if (follow) 1 else -1).coerceAtLeast(0),
                ),
                followingPending = true,
            )
        }
        viewModelScope.launch {
            try {
                following.toggleCategoryFollow(category.slug)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (session.value.generation == account.generation) {
                    mutableState.update { it.copy(category = category) }
                    messages.trySend(R.string.category_follow_failed)
                }
            } finally {
                if (session.value.generation == account.generation) {
                    mutableState.update { it.copy(followingPending = false) }
                }
            }
        }
    }

    private fun loadCategory() {
        categoryJob?.cancel()
        val account = session.value
        mutableState.update { it.copy(loadingCategory = true, categoryFailed = false) }
        categoryJob = viewModelScope.launch {
            try {
                val category = if (account.signedIn) following.getCategory(slug) else repository.getCategory(slug)
                mutableState.update {
                    it.copy(category = category, loadingCategory = false)
                }
                loadContent()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loadingCategory = false, categoryFailed = true) }
            }
        }
    }

    private fun loadContent(cursor: String? = null) {
        val current = state.value
        val category = current.category ?: return
        val language = displayLanguage
        if (current.tab == CategoryTab.LIVESTREAMS && language == null) return
        contentJob?.cancel()
        mutableState.update {
            it.copy(
                streams = if (cursor == null) emptyList() else it.streams,
                clips = if (cursor == null) emptyList() else it.clips,
                nextCursor = cursor,
                loadingContent = cursor == null,
                loadingMore = cursor != null,
                contentFailed = false,
            )
        }
        contentJob = viewModelScope.launch {
            try {
                when (current.tab) {
                    CategoryTab.LIVESTREAMS -> {
                        val page = repository.getCategoryLivestreams(
                            category.id, current.sort.query, checkNotNull(language), current.language, cursor,
                        )
                        mutableState.update {
                            it.copy(
                                streams = (it.streams + page.livestreams).distinctBy(Livestream::id),
                                nextCursor = page.pagination?.nextCursor?.takeIf { next ->
                                    next.isNotBlank() && next != cursor && page.livestreams.isNotEmpty()
                                },
                            )
                        }
                    }
                    CategoryTab.CLIPS -> {
                        val page = repository.getCategoryClips(category.slug, cursor)
                        mutableState.update {
                            it.copy(
                                clips = (it.clips + page.clips).distinctBy(ChannelClip::id),
                                nextCursor = page.nextCursor?.takeIf { next ->
                                    next.isNotBlank() && next != cursor && page.clips.isNotEmpty()
                                },
                            )
                        }
                    }
                }
                mutableState.update { it.copy(loadingContent = false, loadingMore = false) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(loadingContent = false, loadingMore = false, contentFailed = true) }
            }
        }
    }
}
