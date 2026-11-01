package com.lechixy.kick.data.following.model

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

import com.lechixy.kick.data.common.model.Livestream

data class FollowedChannel(
    val slug: String, val username: String, val avatar: String?, val live: Boolean,
    val title: String?, val category: String?, val viewers: Int?, val showViewers: Boolean
)

data class FollowingState(
    val channels: List<FollowedChannel> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val nextCursor: String? = null,
    val pageChannels: List<FollowedChannel> = emptyList(),
    val pageIndex: Int = 0,
    val livestreams: List<Livestream> = emptyList(),
    val streamsLoading: Boolean = false,
    val streamsLoaded: Boolean = false,
    val streamsError: String? = null
)
