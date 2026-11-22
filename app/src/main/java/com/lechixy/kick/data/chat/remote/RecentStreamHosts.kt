package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.StreamHostedEvent

internal class RecentStreamHosts {
    private data class Delivery(
        val event: StreamHostedEvent,
        val receivedAt: Long,
        val paired: Boolean = false,
    )

    private val recent = mutableListOf<Delivery>()

    fun accept(event: StreamHostedEvent, now: Long): Boolean {
        val message = event.message
        if (event.user.username.isBlank() || message.numberOfViewers < 0) return false

        recent.removeAll { now - it.receivedAt >= 60_000L }
        if (message.id != null && recent.any { it.event.message.id == message.id }) return false

        // Only the nested payload carries an event ID. Match each flat delivery once.
        val counterpart = recent.indexOfLast {
            !it.paired && now - it.receivedAt <= 5_000L &&
                (it.event.message.id == null) != (message.id == null) &&
                it.event.user.username.equals(event.user.username, ignoreCase = true) &&
                it.event.message.numberOfViewers == message.numberOfViewers &&
                it.event.message.optionalMessage.orEmpty() == message.optionalMessage.orEmpty()
        }
        if (counterpart >= 0) {
            val previous = recent.removeAt(counterpart)
            recent.add(Delivery(if (message.id != null) event else previous.event, now, paired = true))
            return false
        }

        if (recent.size >= 64) recent.removeAt(0)
        recent.add(Delivery(event, now))
        return true
    }

    fun clear() = recent.clear()
}
