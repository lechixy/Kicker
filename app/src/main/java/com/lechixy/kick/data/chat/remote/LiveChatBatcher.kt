package com.lechixy.kick.data.chat.remote

import com.lechixy.kick.data.chat.model.ChatMessage

/** Bounded pending work; callers serialize access with the socket client's monitor. */
internal class LiveChatBatcher {
    private val pending = LinkedHashMap<String, ChatMessage>()
    private val arrivals = ArrayDeque<Long>()
    private var busyUntil = Long.MIN_VALUE

    fun add(message: ChatMessage, nowMs: Long) {
        pending[message.id] = message
        if (pending.size > 250) pending.remove(pending.keys.first())
        while (arrivals.isNotEmpty() && nowMs - arrivals.first() >= 1_000) arrivals.removeFirst()
        arrivals.addLast(nowMs)
        if (arrivals.size >= 20) busyUntil = nowMs + 5_000
        // Only the threshold is needed, even during arbitrarily large bursts.
        while (arrivals.size > 20) arrivals.removeFirst()
    }

    fun isBusy(nowMs: Long): Boolean = nowMs < busyUntil

    fun drain(nowMs: Long, sample: Boolean = true): List<ChatMessage> {
        val batch = pending.values.toList()
        pending.clear()
        if (!sample || !isBusy(nowMs)) return batch
        // Keep service events, but limit expensive new chat rows to 20 per second.
        val newest = batch.filter { it.type == "message" || it.type == "reply" }.takeLast(5).mapTo(hashSetOf()) { it.id }
        return batch.filter { it.type !in listOf("message", "reply") || it.id in newest }
    }

    fun clear() {
        pending.clear()
        arrivals.clear()
        busyUntil = Long.MIN_VALUE
    }
}
