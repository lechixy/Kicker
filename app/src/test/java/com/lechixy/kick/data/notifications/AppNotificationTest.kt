package com.lechixy.kick.data.notifications

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AppNotificationTest {
    private val live = Json.parseToJsonElement("""{"user_id":29081495,"title":"<strong>RRaenee</strong> just went live!","description":"EightbornV","path":"/rraenee"}""")
    private val goal = Json.parseToJsonElement("""{"user_id":29081495,"challenge_id":"019f1234","recurrence":"daily","threshold_minutes":60}""")
    @Test fun liveEventSupportsEncodedJsonAndRoutesOnlyToItsChannelSlug() {
        val event = decodeAccountNotification("private-userfeed.29081495", "NotifyFollowersStreamHasStarted", JsonPrimitive(live.toString()), 29081495, 4)!!
        assertEquals(NotificationKind.StreamStarted, event.kind)
        assertEquals("rraenee", event.channelSlug)
        assertEquals(4L, event.generation)
        assertEquals("EightbornV", event.description)
    }
    @Test fun rejectsOtherUsersWrongTopicsAndUnsafePaths() {
        assertNull(decodeAccountNotification("private-userfeed.1", "NotifyFollowersStreamHasStarted", live, 29081495, 4))
        assertNull(decodeAccountNotification("private-userfeed.1", "NotifyFollowersStreamHasStarted", live, 1, 4))
        for (path in listOf("https://example.com", "//example.com", "/rraenee/other", "/../settings", "")) {
            val malicious = JsonObject(live.jsonObject + ("path" to JsonPrimitive(path)))
            assertNull(decodeAccountNotification("private-userfeed.29081495", "NotifyFollowersStreamHasStarted", malicious, 29081495, 4))
        }
    }
    @Test fun goalEventRequiresPrivateUserChannelAndPositiveThreshold() {
        val event = decodeAccountNotification("private-29081495", "App\\Events\\GamificationWatchTimeGoalReached", goal, 29081495, 5)!!
        assertEquals(NotificationKind.WatchTimeGoal, event.kind)
        assertEquals(60, event.thresholdMinutes)
        assertNull(decodeAccountNotification("private-userfeed.29081495", "GamificationWatchTimeGoalReached", goal, 29081495, 5))
        val invalid = JsonObject(goal.jsonObject + ("threshold_minutes" to JsonPrimitive(0)))
        assertNull(decodeAccountNotification("private-29081495", "GamificationWatchTimeGoalReached", invalid, 29081495, 5))
    }
    @Test fun malformedOrUnrelatedPublicationsAreIgnored() {
        for (raw in listOf(JsonPrimitive("{bad"), JsonNull, JsonArray(emptyList()))) {
            assertNull(decodeAccountNotification("private-29081495", "GamificationWatchTimeGoalReached", raw, 29081495, 1))
        }
        assertNull(decodeAccountNotification("private-29081495", "UnknownEvent", goal, 29081495, 1))
    }
    @Test fun queueIsBoundedRetainsVisibleItemAndSuppressesReplays() {
        val queue = NotificationQueue()
        fun item(index: Int) = AppNotification("$index", NotificationKind.WatchTimeGoal, 1, 1)
        repeat(100) { queue.add(item(it), it.toLong()) }
        assertEquals(5, queue.items.value.size)
        assertEquals("0", queue.items.value.first().id)
        assertEquals("99", queue.items.value.last().id)
        queue.dismiss("99")
        queue.add(item(99), 100)
        assertEquals(4, queue.items.value.size)
        queue.add(item(99), 60_100)
        assertEquals(5, queue.items.value.size)
        queue.clearAccountNotifications()
        assertTrue(queue.items.value.isEmpty())
        queue.add(item(99), 60_101)
        assertEquals(1, queue.items.value.size)
    }

    @Test fun timerFeedbackReplacesThePreviousActionAndSurvivesAccountChanges() {
        val queue = NotificationQueue()
        val remote = AppNotification("goal", NotificationKind.WatchTimeGoal, 1, 1)
        val started = AppNotification("start", NotificationKind.SleepTimerStarted, -1, -1, timerMinutes = 15)
        val stopped = AppNotification("stop", NotificationKind.SleepTimerStopped, -1, -1)
        queue.add(remote, 1)
        queue.add(started, 2)
        assertEquals(listOf(started, remote), queue.items.value)
        queue.add(stopped, 3)
        assertEquals(listOf(stopped, remote), queue.items.value)
        queue.clearAccountNotifications()
        assertEquals(listOf(stopped), queue.items.value)
    }
}
