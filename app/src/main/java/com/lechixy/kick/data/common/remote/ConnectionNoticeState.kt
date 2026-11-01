package com.lechixy.kick.data.common.remote

enum class ConnectionQuality { Online, Weak, Offline }
enum class ConnectionNotice { Weak, Offline, Restored }

/** Keeps initial connectivity and repeated callbacks from creating false reconnection messages. */
internal class ConnectionNoticeState {
    var quality: ConnectionQuality? = null
        private set
    var notice: ConnectionNotice? = null
        private set

    private var foreground = true

    fun setForeground(visible: Boolean) {
        if (foreground == visible) return
        foreground = visible
        // Background restrictions and recovery are not user-visible interruptions.
        notice = if (visible) when (quality) {
            ConnectionQuality.Offline -> ConnectionNotice.Offline
            ConnectionQuality.Weak -> ConnectionNotice.Weak
            else -> null
        } else null
    }

    fun update(next: ConnectionQuality) {
        if (quality == next) return
        val previous = quality
        quality = next
        notice = if (!foreground) null else when (next) {
            ConnectionQuality.Offline -> ConnectionNotice.Offline
            ConnectionQuality.Weak -> ConnectionNotice.Weak
            ConnectionQuality.Online -> if (previous == ConnectionQuality.Offline || previous == ConnectionQuality.Weak)
                ConnectionNotice.Restored else null
        }
    }

    fun expireRestored() {
        if (notice == ConnectionNotice.Restored) notice = null
    }
}
