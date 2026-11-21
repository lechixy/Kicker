package com.lechixy.kick.data.settings.model

internal enum class FullscreenChatBlurStyle(val value: String) {
    Soft("soft"),
    FrostedGlass("frosted_glass");

    companion object {
        fun fromValue(value: String): FullscreenChatBlurStyle =
            entries.firstOrNull { it.value == value } ?: Soft
    }
}
