package com.lechixy.kick.data.following.repository

import com.lechixy.kick.data.account.repository.AuthRepository
import com.lechixy.kick.data.account.remote.string
import com.lechixy.kick.data.following.model.FollowedChannel
import com.lechixy.kick.data.favorites.local.FavoriteUsers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.Locale

data class RailFollowingState(val channels: List<FollowedChannel> = emptyList(), val loading: Boolean = false,
    val loaded: Boolean = false, val failed: Boolean = false)

/** Rank only the five channels returned by the first page. Favorites update without network requests. */
internal fun rankRailChannels(channels: List<FollowedChannel>, favorites: FavoriteUsers): List<FollowedChannel> = channels.take(5)
    .sortedWith(compareByDescending<FollowedChannel> { favorites.contains(null, it.username, it.slug) }
        .thenByDescending { it.live }.thenByDescending { it.viewers ?: 0 }.thenBy { it.username.lowercase(Locale.ROOT) })

/** The rail intentionally fetches the first page only; the full Following screen has its own pagination. */
class RailFollowingRepository(private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(RailFollowingState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    init {
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect {
                job?.cancel(); mutable.value = RailFollowingState()
            }
        }
    }
    fun retry() = refresh()
    fun invalidate() { if (state.value.loaded) refresh() }
    fun refresh() {
        val session = auth.session.value
        if (!session.signedIn) return
        job?.cancel()
        mutable.update { it.copy(loading = true, failed = false) }
        job = scope.launch {
            try {
                val response = auth.request("https://kick.com/api/v2/channels/followed")
                val channels = response["channels"] as? JsonArray ?: error("Missing followed channels")
                val parsed = channels.take(5).map { value ->
                    val item = value.jsonObject
                    val slug = item.string("channel_slug")?.takeIf { it.matches(Regex("[A-Za-z0-9_][A-Za-z0-9_-]{0,99}")) } ?: error("Invalid channel slug")
                    FollowedChannel(slug, item.string("user_username")?.takeIf { it.isNotBlank() } ?: slug,
                        item.string("profile_picture"), item["is_live"]?.jsonPrimitive?.booleanOrNull ?: false,
                        item.string("session_title"), item.string("category_name"), item.string("viewer_count")?.toIntOrNull(),
                        item["show_view_count"]?.jsonPrimitive?.booleanOrNull ?: true)
                }.distinctBy { it.slug.lowercase(Locale.ROOT) }
                if (!auth.session.value.signedIn || auth.session.value.generation != session.generation) return@launch
                mutable.value = RailFollowingState(parsed, loaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (auth.session.value.signedIn && auth.session.value.generation == session.generation)
                    mutable.update { it.copy(loading = false, failed = true) }
            }
        }
    }
}
