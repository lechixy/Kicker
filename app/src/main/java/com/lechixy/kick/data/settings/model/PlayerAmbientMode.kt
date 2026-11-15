package com.lechixy.kick.data.settings.model

internal enum class PlayerAmbientMode(val value: String) {
    Off("off"),
    Dynamic("dynamic"),
    Live("live");

    companion object {
        fun fromValue(value: String): PlayerAmbientMode = entries.firstOrNull { it.value == value } ?: Off
    }
}
