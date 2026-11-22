package com.lechixy.kick.components

internal class LiveStreamRecovery {
    private var interruptedAt: Long? = null
    private var lastRetryAt: Long? = null
    private var nextStatusCheckAt = 0L
    private var consecutiveEndedResponses = 0

    fun reset() {
        interruptedAt = null
        lastRetryAt = null
        nextStatusCheckAt = 0L
        consecutiveEndedResponses = 0
    }

    fun onInterrupted(nowMs: Long) {
        if (interruptedAt != null) return
        interruptedAt = nowMs
        nextStatusCheckAt = nowMs + BUFFERING_GRACE_MS
    }

    fun shouldCheckStatus(nowMs: Long): Boolean {
        if (interruptedAt == null || nowMs < nextStatusCheckAt) return false
        nextStatusCheckAt = nowMs + STATUS_CHECK_INTERVAL_MS
        return true
    }

    fun onChannelStatus(sameBroadcast: Boolean?): Boolean {
        consecutiveEndedResponses = if (sameBroadcast == false) consecutiveEndedResponses + 1 else 0
        return consecutiveEndedResponses >= 2
    }

    fun shouldRetry(nowMs: Long, buffering: Boolean): Boolean {
        val since = interruptedAt ?: return false
        val retryAt = lastRetryAt ?: since
        val delayMs = if (buffering) BUFFERING_GRACE_MS else TERMINAL_RETRY_DELAY_MS
        return nowMs - retryAt >= delayMs
    }

    fun onRetry(nowMs: Long) {
        lastRetryAt = nowMs
    }

    private companion object {
        const val BUFFERING_GRACE_MS = 18_000L
        const val TERMINAL_RETRY_DELAY_MS = 3_000L
        const val STATUS_CHECK_INTERVAL_MS = 10_000L
    }
}
