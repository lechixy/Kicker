package com.lechixy.kick.data.chat.model

import com.lechixy.kick.util.FormatUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ChatTimestampMode(val value: String) {
    LIVE_AND_VIDEO("live_and_video"), ONLY_LIVE("only_live"), ONLY_VIDEO("only_video"), DONT_SHOW("dont_show");

    fun shows(isReplay: Boolean): Boolean = when (this) {
        LIVE_AND_VIDEO -> true
        ONLY_LIVE -> !isReplay
        ONLY_VIDEO -> isReplay
        DONT_SHOW -> false
    }

    companion object {
        fun fromValue(value: String): ChatTimestampMode = entries.firstOrNull { it.value == value } ?: ONLY_VIDEO
    }
}

private val liveTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun chatTimestamp(message: ChatMessage, mode: ChatTimestampMode, isReplay: Boolean,
    zone: ZoneId = ZoneId.systemDefault()): String? {
    if (!mode.shows(isReplay)) return null
    return if (isReplay) message.recordingOffsetMs?.takeIf { it >= 0 }?.let(FormatUtils::formatDuration)
    else recordingStartInstant(message.createdAt)?.let { liveTimeFormat.withZone(zone).format(it) }
}
