package com.lechixy.kick.data.favorites.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

private fun favoriteName(value: String?) = value.orEmpty().trim().lowercase(Locale.ROOT)

@Serializable
data class FavoriteUser(val userId: Long? = null, val username: String, val slug: String = "")

/** Precomputed indexes keep sender matching O(1) while chat is moving. */
class FavoriteUsers(val users: List<FavoriteUser> = emptyList()) {
    private val ids = users.mapNotNull { it.userId }.toSet()
    private val names = users.flatMap { listOf(favoriteName(it.username), favoriteName(it.slug)) }.filter { it.isNotEmpty() }.toSet()
    private val unresolvedNames = users.filter { it.userId == null }
        .flatMap { listOf(favoriteName(it.username), favoriteName(it.slug)) }.filter { it.isNotEmpty() }.toSet()

    fun contains(userId: Long?, username: String?, slug: String? = null): Boolean {
        if (userId != null && userId in ids) return true
        val aliases = if (userId == null) names else unresolvedNames
        return favoriteName(username).takeIf { it.isNotEmpty() } in aliases ||
            favoriteName(slug).takeIf { it.isNotEmpty() } in aliases
    }

    fun toggle(user: FavoriteUser): FavoriteUsers {
        if (user.userId == null && favoriteName(user.username).isEmpty() && favoriteName(user.slug).isEmpty()) return this
        return if (contains(user.userId, user.username, user.slug)) FavoriteUsers(users.filterNot {
            FavoriteUsers(listOf(it)).contains(user.userId, user.username, user.slug)
        }) else FavoriteUsers(users + user)
    }
}

/** Device-local favorites are independent of Kick follows and the signed-in account. */
object FavoriteUsersStore {
    private var preferences: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableFavorites = MutableStateFlow(FavoriteUsers())
    val favorites = mutableFavorites.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("favorite_users", Context.MODE_PRIVATE)
        mutableFavorites.value = FavoriteUsers(runCatching {
            json.decodeFromString<List<FavoriteUser>>(prefs.getString("users", "[]") ?: "[]")
        }.getOrDefault(emptyList()))
        preferences = prefs
    }

    @Synchronized
    fun toggle(userId: Long?, username: String, slug: String = "") {
        val updated = mutableFavorites.value.toggle(FavoriteUser(userId, username, slug))
        mutableFavorites.value = updated
        preferences?.edit()?.putString("users", json.encodeToString(updated.users))?.apply()
    }
}
