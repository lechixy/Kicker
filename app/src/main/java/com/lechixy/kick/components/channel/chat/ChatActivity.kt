package com.lechixy.kick.components.channel.chat

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/** A retained chat can keep its measured rows without doing live work offscreen. */
internal val LocalChatContentActive = compositionLocalOf { true }

@Composable
internal fun <T> StateFlow<T>.collectAsChatState(): State<T> {
    val active = LocalChatContentActive.current
    val owner = LocalLifecycleOwner.current
    return produceState(value, this, active, owner) {
        if (active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collectAsChatState.collect { value = it }
        }
    }
}
