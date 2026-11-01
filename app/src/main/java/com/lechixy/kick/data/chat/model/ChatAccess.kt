package com.lechixy.kick.data.chat.model

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant

import com.lechixy.kick.data.chat.model.ChannelMembership
import com.lechixy.kick.data.chat.model.ChatSettings

data class ChatAccess(
    val settings: ChatSettings,
    val member: ChannelMembership,
    val identity: ChatIdentity? = null
)
