package com.lechixy.kick.data.settings.model

internal enum class FullscreenChatShape(val value: String) {
    Square("square"),
    Rounded("rounded"),
    Pill("pill");

    companion object {
        fun fromValue(value: String): FullscreenChatShape = entries.firstOrNull { it.value == value } ?: Rounded
    }
}
