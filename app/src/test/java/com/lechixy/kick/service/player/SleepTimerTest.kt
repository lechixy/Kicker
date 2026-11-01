package com.lechixy.kick.service.player

import org.junit.Assert.*
import org.junit.Test
import androidx.media3.common.Player
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking

class SleepTimerTest {
    @Test fun delayedTicksCatchUpFromDeadlineInsteadOfExtendingTheTimer() {
        val deadline = 90 * 60_000L
        assertEquals(5400, sleepTimerRemainingSeconds(deadline, 0))
        assertEquals(3600, sleepTimerRemainingSeconds(deadline, 30 * 60_000L))
        assertEquals(1, sleepTimerRemainingSeconds(deadline, deadline - 1))
        assertEquals(0, sleepTimerRemainingSeconds(deadline, deadline))
        assertEquals(0, sleepTimerRemainingSeconds(deadline, deadline + 5000))
    }

    @Test fun countdownDisplayRoundsUpAndTransitionsAtMinuteBoundaries() {
        assertEquals(90, sleepTimerDisplayMinutes(5400))
        assertEquals(90, sleepTimerDisplayMinutes(5341))
        assertEquals(89, sleepTimerDisplayMinutes(5340))
        assertEquals(60, sleepTimerDisplayMinutes(3600))
        assertEquals(1, sleepTimerDisplayMinutes(1))
        assertEquals(0, sleepTimerDisplayMinutes(0))
        assertEquals(0, sleepTimerDisplayMinutes(-1))
    }
    @Test fun countdownKeepsRunningWhilePausedAndPausesBeforeTheCompletionNotification() = runBlocking {
        var clock = 0L
        val events = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "pause" -> { events += "pause"; null }
                "isPlaying" -> false
                else -> error("Unexpected player query: ${method.name}")
            }
        } as Player
        val ticks = mutableListOf<Int>()
        runSleepTimerCountdown(player, 60_000L, nowMs = { clock }, onTick = { ticks += it },
            onFinished = { events += "finished" }, waitNextTick = { clock += 35_000 })
        assertEquals(listOf(60, 25, 0), ticks)
        assertEquals(listOf("pause", "finished"), events)
    }

    @Test fun completionFailureCannotPreventPlaybackFromPausing() = runBlocking {
        var paused = false
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            if (method.name == "pause") paused = true
            null
        } as Player
        try {
            runSleepTimerCountdown(player, 1L, nowMs = { 2L }, onTick = {},
                onFinished = { throw SecurityException("Notification permission denied") })
            fail("Expected simulated notification failure")
        } catch (_: SecurityException) {
            assertTrue(paused)
        }
    }

}
