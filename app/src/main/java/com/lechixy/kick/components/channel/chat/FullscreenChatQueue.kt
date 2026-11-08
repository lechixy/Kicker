package com.lechixy.kick.components.channel.chat

internal class FullscreenChatQueue {
    private var previousIds: Set<String>? = null
    private val pending = ArrayDeque<String>()

    val hasPending get() = pending.isNotEmpty()

    fun update(sourceIds: List<String>, eligibleIds: List<String>) {
        pending.retainAll(eligibleIds.toSet())
        val previous = previousIds
        val incoming = if (previous == null) eligibleIds.takeLast(1) else eligibleIds.filter { it !in previous }
        pending.addAll(incoming)
        while (pending.size > 4) pending.removeFirst()
        previousIds = sourceIds.toSet()
    }

    fun next(): String? = pending.removeFirstOrNull()
}
