package com.lechixy.kick.data.following.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FollowManager {
    private const val PREFS_NAME = "kick_follows_prefs"
    private const val KEY_FOLLOWED = "followed_channels"

    private val _followedSlugs = MutableStateFlow<Set<String>>(emptySet())
    val followedSlugs: StateFlow<Set<String>> = _followedSlugs.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            sharedPreferences = prefs
            val saved = prefs.getStringSet(KEY_FOLLOWED, emptySet()) ?: emptySet()
            _followedSlugs.value = saved.map { it.trim().lowercase() }.toSet()
        }
    }

    fun isFollowing(slug: String): Boolean {
        val normalized = slug.trim().lowercase()
        return _followedSlugs.value.contains(normalized)
    }

    fun toggleFollow(slug: String) {
        val normalized = slug.trim().lowercase()
        val current = _followedSlugs.value.toMutableSet()
        if (current.contains(normalized)) {
            current.remove(normalized)
        } else {
            current.add(normalized)
        }
        sharedPreferences?.edit()?.putStringSet(KEY_FOLLOWED, current)?.apply()
        _followedSlugs.value = current
    }
}