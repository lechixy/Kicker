package com.lechixy.kick.data.chat.repository

import android.content.SharedPreferences
import com.lechixy.kick.data.account.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

@Serializable
internal data class SilencedUser(val id: Long, val username: String)

internal class SilencedUsersRepository(
    private val auth: AuthRepository,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
) {
    private val endpoint = "https://kick.com/api/v2/silenced-users".toHttpUrl()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val local = MutableStateFlow(preferences.getStringSet("users", emptySet()).orEmpty().toSet())
    private data class Remote(val generation: Long, val users: List<SilencedUser>)
    private val remote = MutableStateFlow<Remote?>(null)
    private var remoteNeedsRefresh = false
    val silencedUsers = combine(auth.session, local, remote) { session, names, snapshot ->
        val users = snapshot?.takeIf { session.signedIn && it.generation == session.generation }?.users.orEmpty()
        (users + names.map { SilencedUser(0, it) }).distinctBy { normalized(it.username) }
            .sortedBy { normalized(it.username) }
    }.stateIn(scope, SharingStarted.Eagerly, local.value.sorted().map { SilencedUser(0, it) })
    val mutedUsers = silencedUsers.map { users ->
        users.mapTo(mutableSetOf()) { normalized(it.username) }.toSet()
    }.stateIn(scope, SharingStarted.Eagerly, local.value)

    init {
        scope.launch {
            auth.session.map { it.signedIn to it.generation }.distinctUntilChanged().collect { (signedIn, generation) ->
                if (signedIn) {
                    try {
                        refresh(generation)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The management dialog and the next mutation can retry.
                    }
                } else {
                    remote.value = null
                }
            }
        }
    }

    suspend fun refresh(generation: Long = auth.session.value.generation) = mutex.withLock {
        loadRemote(generation)
    }

    suspend fun setMuted(userId: Long?, username: String, muted: Boolean) {
        val session = auth.session.value
        // Keep the local snapshot in sync even if the originating dialog closes during a write.
        scope.async {
            mutex.withLock {
                check(auth.session.value.generation == session.generation)
                val name = normalized(username)
                if (session.signedIn) {
                    val users = remote.value?.takeIf { it.generation == session.generation && !remoteNeedsRefresh }?.users
                        ?: loadRemote(session.generation)
                    val existing = users.firstOrNull { it.id == userId || normalized(it.username) == name }
                    if (muted && existing == null) {
                        require(userId != null && userId > 0)
                        updateRemote(session.generation) {
                            val response = auth.request(endpoint.toString(), "POST", buildJsonObject { put("user_id", userId) })
                            users + json.decodeFromJsonElement(SilencedUser.serializer(), checkNotNull(response["data"]))
                        }
                    } else if (!muted && existing != null) {
                        updateRemote(session.generation) {
                            val url = endpoint.newBuilder().addPathSegment(existing.id.toString()).build().toString()
                            auth.requestElement(url, "DELETE")
                            users.filterNot { it.id == existing.id }
                        }
                    }
                }
                if (!session.signedIn || !muted) {
                    val updated = if (muted) local.value + name else local.value - name
                    if (updated != local.value) {
                        preferences.edit().putStringSet("users", updated).apply()
                        local.value = updated
                    }
                }
            }
        }.await()
    }

    private suspend fun updateRemote(generation: Long, write: suspend () -> List<SilencedUser>) {
        // Reconcile with Kick before a retry if the write reaches the server but its response is lost.
        remoteNeedsRefresh = true
        remote.value = Remote(generation, write())
        remoteNeedsRefresh = false
    }

    private suspend fun loadRemote(generation: Long): List<SilencedUser> {
        val users = mutableListOf<SilencedUser>()
        val visited = mutableSetOf<String>()
        var next: String? = endpoint.toString()
        while (next != null) {
            check(auth.session.value.let { it.signedIn && it.generation == generation })
            val page = checkNotNull(endpoint.resolve(next))
            require(page.scheme == endpoint.scheme && page.host == endpoint.host && page.port == endpoint.port &&
                page.encodedPath == endpoint.encodedPath && visited.add(page.toString()))
            val response = auth.request(page.toString())
            users += json.decodeFromJsonElement(
                ListSerializer(SilencedUser.serializer()),
                checkNotNull(response["data"]),
            )
            next = (response["links"] as? JsonObject)?.get("next")
                ?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
        }
        return users.distinctBy { it.id }.also {
            remote.value = Remote(generation, it)
            remoteNeedsRefresh = false
        }
    }

    private fun normalized(username: String) = username.lowercase(Locale.ROOT)
}
