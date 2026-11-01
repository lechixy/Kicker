package com.lechixy.kick.service.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

import com.lechixy.kick.util.player.NotificationUtils

internal fun sleepTimerRemainingSeconds(deadlineMs: Long, nowMs: Long): Int =
    ((deadlineMs - nowMs).coerceAtLeast(0L).let { (it + 999L) / 1000L }).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun sleepTimerDisplayMinutes(seconds: Int): Int = ((seconds.coerceAtLeast(0).toLong() + 59) / 60).toInt()

data class SleepTimerState(val selectedMinutes: Int = 0, val remainingSeconds: Int = 0)

/** Owned by playback, never by a screen or by the notification permission callback. */
object SleepTimer {
    private val mutableState = MutableStateFlow(SleepTimerState())
    val state = mutableState.asStateFlow()
    private val deadlines = MutableStateFlow<Long?>(null)
    private var runner: Job? = null

    fun start(minutes: Int) {
        require(minutes in 0..1440)
        mutableState.value = SleepTimerState(minutes, minutes * 60)
        deadlines.value = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else null
    }

    fun attach(context: Context, player: Player, scope: CoroutineScope) {
        runner?.cancel()
        val appContext = context.applicationContext
        runner = scope.launch(Dispatchers.Main.immediate) {
            deadlines.collectLatest { deadline ->
                if (deadline == null) {
                    NotificationUtils.cancelTimerNotification(appContext)
                    return@collectLatest
                }
                var lastDisplayedMinutes = -1
                runSleepTimerCountdown(player, deadline, onTick = { remaining ->
                    mutableState.update { it.copy(remainingSeconds = remaining) }
                    val displayedMinutes = sleepTimerDisplayMinutes(remaining)
                    if (remaining > 0 && displayedMinutes != lastDisplayedMinutes) {
                        NotificationUtils.updateTimerNotification(appContext, remaining)
                        lastDisplayedMinutes = displayedMinutes
                    }
                }, onFinished = {
                    mutableState.value = SleepTimerState()
                    NotificationUtils.showTimerEndedNotification(appContext)
                })
            }
        }
    }

    fun detach(context: Context) {
        runner?.cancel()
        runner = null
        deadlines.value = null
        mutableState.value = SleepTimerState()
        NotificationUtils.cancelTimerNotification(context)
    }
}

/** Uses an elapsed-time deadline; paused playback and skipped ticks cannot stop or extend it. */
internal suspend fun runSleepTimerCountdown(
    player: Player,
    deadlineMs: Long,
    nowMs: () -> Long = SystemClock::elapsedRealtime,
    onTick: (Int) -> Unit,
    onFinished: () -> Unit,
    waitNextTick: suspend () -> Unit = { delay(1000) }
) {
    while (true) {
        currentCoroutineContext().ensureActive()
        val remaining = sleepTimerRemainingSeconds(deadlineMs, nowMs())
        onTick(remaining)
        if (remaining == 0) {
            // Do this before resetting UI state or attempting the completion notification.
            player.pause()
            onFinished()
            return
        }
        waitNextTick()
    }
}
