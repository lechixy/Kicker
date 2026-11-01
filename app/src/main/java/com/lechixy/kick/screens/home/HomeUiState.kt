package com.lechixy.kick.screens.home

import com.lechixy.kick.data.common.model.LiveTotals
import com.lechixy.kick.data.common.model.Livestream

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val categories: List<com.lechixy.kick.data.browse.model.BrowseCategory> = emptyList(),
    val categoriesError: String? = null,
    val liveTotals: LiveTotals? = null,
    val following: FollowingLiveState = FollowingLiveState(),
    val error: String? = null
)

data class FollowingLiveState(
    val signedIn: Boolean = false,
    val streams: List<Livestream> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null
)
