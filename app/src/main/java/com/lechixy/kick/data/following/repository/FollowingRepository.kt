package com.lechixy.kick.data.following.repository

import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.account.remote.userMessage
import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.common.model.FollowedLivestream
import com.lechixy.kick.data.following.model.FollowedChannel
import com.lechixy.kick.data.following.model.FollowingState

class FollowingRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    val rail = RailFollowingRepository(auth, scope)
    private val mutableState = MutableStateFlow(FollowingState())
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var streamsJob: Job? = null
    private val json = Json { ignoreUnknownKeys = true }
    private data class Page(val channels: List<FollowedChannel>, val nextCursor: String?)
    private val pages = mutableListOf<Page>()
    private val mutationMutex = Mutex()
    private val seenCursors = mutableSetOf<String>()
    private var retryReset = true

    init {
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect { (signedIn, _) ->
                loadJob?.cancel(); streamsJob?.cancel(); pages.clear(); seenCursors.clear(); mutableState.value = FollowingState()
                if (signedIn) refresh()
            }
        }
    }

    fun refresh() {
        load(reset = true)
        refreshStreams()
    }

    fun refreshStreams() {
        if (!auth.session.value.signedIn) return
        streamsJob?.cancel()
        streamsJob = scope.launch {
            val generation = auth.session.value.generation
            mutableState.update { it.copy(streamsLoading = true, streamsError = null) }
            try {
                val streams = auth.requestArray("https://kick.com/api/v1/user/livestreams")
                    .map { json.decodeFromJsonElement<FollowedLivestream>(it) }
                    .filter { it.isLive }.map { it.toLivestream() }.distinctBy { it.channel?.slug?.lowercase() }
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(livestreams = streams, streamsLoading = false, streamsLoaded = true) }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.update { it.copy(streamsLoading = false, streamsError = error.userMessage()) }
                }
            }
        }
    }

    fun loadMore() {
        if (state.value.loading || state.value.nextCursor == null) return
        val nextIndex = state.value.pageIndex + 1
        if (nextIndex < pages.size) showPage(nextIndex) else load(reset = false)
    }

    fun previousPage() {
        if (!state.value.loading && state.value.pageIndex > 0) showPage(state.value.pageIndex - 1)
    }

    private fun showPage(index: Int) {
        val page = pages[index]
        mutableState.update { it.copy(pageChannels = page.channels, pageIndex = index, nextCursor = page.nextCursor, error = null) }
    }

    fun retryPage() = load(reset = retryReset)

    private fun load(reset: Boolean) {
        if (!auth.session.value.signedIn) return
        if (!reset && (state.value.loading || state.value.nextCursor == null)) return
        loadJob?.cancel()
        loadJob = scope.launch {
            val generation = auth.session.value.generation
            retryReset = reset
            val cursor = if (reset) null else state.value.nextCursor
            mutableState.value = state.value.copy(loading = true, error = null)
            try {
                val url = "https://kick.com/api/v2/channels/followed-page".toHttpUrl().newBuilder()
                    .apply { if (cursor != null) addQueryParameter("cursor", cursor) }.build().toString()
                val response = auth.request(url)
                val channels = response["channels"] as? JsonArray ?: error("Missing channels")
                val parsed = channels.map { element ->
                    val item = element.jsonObject
                    FollowedChannel(
                        item.string("channel_slug")?.takeIf { it.isNotBlank() } ?: error("Missing slug"),
                        item.string("user_username") ?: item.string("channel_slug")!!,
                        item.string("profile_picture"), item["is_live"]?.jsonPrimitive?.booleanOrNull ?: false,
                        item.string("session_title"), item.string("category_name"), item.string("viewer_count")?.toIntOrNull(),
                        item["show_view_count"]?.jsonPrimitive?.booleanOrNull ?: true)
                }
                if (generation != auth.session.value.generation || !auth.session.value.signedIn) return@launch
                if (reset) { seenCursors.clear(); pages.clear() }
                if (cursor != null) seenCursors.add(cursor)
                val next = response.string("nextCursor")?.takeUnless { it.isBlank() || it in seenCursors }
                val page = parsed.distinctBy { it.slug.lowercase() }
                pages.add(Page(page, next))
                mutableState.update { it.copy(
                    channels = ((if (reset) emptyList() else it.channels) + parsed).distinctBy { it.slug.lowercase() },
                    loaded = true, loading = false, nextCursor = next,
                    pageChannels = page, pageIndex = pages.lastIndex) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == auth.session.value.generation && auth.session.value.signedIn) {
                    mutableState.value = state.value.copy(loading = false, error = error.userMessage())
                }
            }
        }
    }

    /** Current status comes from /me; the paginated list alone cannot prove a negative. */
    suspend fun isFollowing(slug: String): Boolean {
        require(slug.matches(CHANNEL_SLUG))
        val response = auth.request("https://kick.com/api/v2/channels/$slug/me")
        val membership = (response["data"] as? JsonObject) ?: response
        return (membership["is_following"] as? JsonPrimitive)?.booleanOrNull
            ?: error("Missing follow status")
    }

    suspend fun setFollowing(slug: String, following: Boolean) {
        val generation = auth.session.value.generation
        mutationMutex.withLock {
            check(auth.session.value.signedIn && generation == auth.session.value.generation) { "Session changed" }
            require(slug.matches(CHANNEL_SLUG))
            auth.requestElement("https://kick.com/api/v2/channels/$slug/follow", if (following) "POST" else "DELETE")
            if (generation == auth.session.value.generation) refreshAfterFollowChange()
        }
    }

    fun refreshAfterFollowChange() {
        refresh()
        rail.invalidate()
    }

    private companion object {
        val CHANNEL_SLUG = Regex("[A-Za-z0-9_-]+")
    }
}
