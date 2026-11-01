package com.lechixy.kick.data.chat.model

import java.time.Instant

data class Mode(val enabled: Boolean, val durationSeconds: Long = 0)
data class ChatSettings(
    val allowLinks: Boolean,
    val emotesOnly: Boolean,
    val subscribersOnly: Boolean,
    val followersOnly: Mode,
    val minimumAccountAge: Mode,
    val slowMode: Mode,
    val rules: String
)
data class ChannelMembership(
    val following: Boolean, val followingSince: Instant?, val subscriber: Boolean,
    val banned: Boolean, val privileged: Boolean
)

