package com.lechixy.kick.data.account.model

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.account.model.Challenge
import com.lechixy.kick.data.account.model.ChallengeLoadFailure
import com.lechixy.kick.data.account.model.Condition
import com.lechixy.kick.data.account.model.DropTableEntry
import com.lechixy.kick.data.account.model.Window
import com.lechixy.kick.data.account.model.challengeCountdown
import com.lechixy.kick.data.account.model.decodeChallenges
import com.lechixy.kick.data.account.model.formatDropChance
import com.lechixy.kick.data.account.model.orderChallenges
import com.lechixy.kick.data.account.model.rewardAnimationImageUrl
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.remote.accountNetworkFailureCode
import com.lechixy.kick.data.account.repository.ChallengesRepository

class ChallengesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun challenge(now: Instant = Instant.now()) = Challenge(
        "daily-id", "Daily Reward", condition = Condition(8, 60, "watch_time_minutes"), status = "in_progress",
        recurrence = "daily", window = Window(now.minusSeconds(60).toString(), now.plusSeconds(3600).toString()),
        dropTable = listOf(DropTableEntry("common", 550000), DropTableEntry("uncommon", 280000),
            DropTableEntry("rare", 120000), DropTableEntry("epic", 47000), DropTableEntry("legendary", 2995), DropTableEntry("mythic", 5))
    )
    @Test fun animationBaseUrlsResolveToExistingGifRenditions() {
        assertEquals("https://static.kick.com/rewards/daily-rewards/idle.gif",
            rewardAnimationImageUrl("https://static.kick.com/rewards/daily-rewards/idle"))
        assertEquals("https://static.kick.com/rewards/halloween-2610/idle.gif?version=2",
            rewardAnimationImageUrl("https://static.kick.com/rewards/halloween-2610/idle?version=2"))
        assertEquals("https://static.kick.com/rewards/daily-rewards/idle.gif",
            rewardAnimationImageUrl("https://static.kick.com/rewards/daily-rewards/idle.gif"))
        assertEquals("https://example.com/idle", rewardAnimationImageUrl("https://example.com/idle"))
        assertNull(rewardAnimationImageUrl(null))
    }
    @Test fun limitedEventsPrecedeDailyRewardsAndKeepTheirRelativeOrder() {
        val daily = challenge().copy(id = "daily")
        val event = challenge().copy(id = "event", featured = true, activePeriod = challenge().window)
        val nextEvent = event.copy(id = "next-event")
        assertEquals(listOf("event", "next-event", "daily"), orderChallenges(listOf(daily, event, nextEvent)).map { it.id })
    }
    @Test fun probabilitiesPreserveSmallWeightsAndLocale() {
        val table = challenge().dropTable
        assertEquals("0.0005%", formatDropChance(table.last(), table, Locale.US))
        assertEquals("0.2995%", formatDropChance(table[4], table, Locale.US))
        assertEquals("12%", formatDropChance(table[2], table, Locale.US))
        assertEquals("0,0005%", formatDropChance(table.last(), table, Locale.forLanguageTag("tr")))
        assertEquals("0%", formatDropChance(DropTableEntry("rare", 0), listOf(DropTableEntry("rare", 0)), Locale.US))
    }
    @Test fun readinessHonorsWindowsAndClaimedStatus() {
        val now = Instant.now()
        val challenge = challenge(now)
        assertEquals(52, challenge.remaining.toInt())
        assertFalse(challenge.canClaim(now))
        val ready = challenge.copy(condition = challenge.condition.copy(progress = 60))
        assertTrue(ready.canClaim(now))
        assertFalse(ready.copy(status = "claimed").canClaim(now))
        assertFalse(ready.canClaim(now.plusSeconds(3600)))
        assertFalse(ready.copy(activePeriod = Window(now.plusSeconds(60).toString(), now.plusSeconds(120).toString())).canClaim(now))
        assertFalse(ready.copy(condition = Condition(0, 0, "watch_time_minutes")).canClaim(now))
        assertEquals("00:00:00", challengeCountdown(now.minusSeconds(1), now))
        assertEquals("01:00:00", challengeCountdown(now.plusSeconds(3600), now))
    }
    @Test fun schemaSupportsOptionalEventFieldsAndAnimations() {
        val data = json.decodeFromString<List<Challenge>>("""[{
            "id":"event","name":"Trick or Treat","description":"Win prizes","featured":true,
            "condition":{"progress":8,"threshold":180,"type":"watch_time_minutes"},
            "status":"in_progress","recurrence":"daily","drop_table":[{"rarity":"mythic","weighting":3}],
            "active_period":{"starts_at":"2026-10-24T00:00:00Z","ends_at":"2026-11-01T00:00:00Z"},
            "animations":{"idle_url":"idle","unbox_url":"unbox"},"unknown":"ignored"
        }]""").single()
        assertTrue(data.featured)
        assertEquals("unbox", data.animations?.unboxUrl)
        assertEquals(172, data.remaining.toInt())
    }
    @Test fun acceptsBareAndDataWrappedChallengeListsWithoutMaskingBadResponses() {
        val data = json.parseToJsonElement(json.encodeToString(listOf(challenge())))
        assertEquals(1, decodeChallenges(data, json).size)
        assertEquals(1, decodeChallenges(buildJsonObject { put("data", data); put("message", "success") }, json).size)
        assertTrue(runCatching { decodeChallenges(objectJson("""{"message":"error"}"""), json) }.isFailure)
        assertTrue(runCatching { decodeChallenges(json.parseToJsonElement("""[{"id":"broken"}]"""), json) }.isFailure)
    }
    @Test fun networkDiagnosticsOnlyExposeKnownCodes() {
        assertEquals("ECONNRESET", accountNetworkFailureCode(java.net.SocketException("ECONNRESET secret-token")))
        assertEquals("SOCKET_CLOSED", accountNetworkFailureCode(java.net.SocketException("Socket closed")))
        assertEquals("DNS", accountNetworkFailureCode(java.net.UnknownHostException("private-host")))
        assertEquals("UNKNOWN", accountNetworkFailureCode(Exception("secret-token")))
    }
    @Test fun authenticatedLoadingCanRetryAndClearsOnLogout() = runBlocking<Unit> {
        var fail = true
        val data = challenge()
        val auth = signedInAuth { request ->
            assertEquals("https://web.kick.com/api/v1/gamification/challenges", request.url.toString())
            assertEquals("Bearer access", request.header("Authorization"))
            if (fail) throw AccountApiException(403)
            json.parseToJsonElement(json.encodeToString(listOf(data)))
        }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.failed } }
            assertFalse(repo.state.value.loaded)
            assertEquals(ChallengeLoadFailure.Http, repo.state.value.failure)
            assertEquals(403, repo.state.value.httpStatus)
            fail = false; repo.refresh()
            withTimeout(3000) { repo.state.first { it.loaded } }
            assertNull(repo.state.value.failure)
            assertNull(repo.state.value.httpStatus)
            assertEquals(8, repo.state.value.challenges.single().condition.progress.toInt())
            auth.signOut()
            withTimeout(3000) { repo.state.first { !it.loaded && it.challenges.isEmpty() } }
        } finally { job.cancelAndJoin() }
    }
    @Test fun goalEventIsScopedToUserAndPrivateChannelAndSurvivesStaleFetch() = runBlocking<Unit> {
        val data = challenge()
        val auth = signedInAuth { json.parseToJsonElement(json.encodeToString(listOf(data))) }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            val payload = JsonPrimitive("""{"user_id":42,"challenge_id":"daily-id","recurrence":"daily","threshold_minutes":60}""")
            repo.onGoalReached("private-99", payload)
            assertEquals(8, repo.state.value.challenges.single().condition.progress.toInt())
            repo.onGoalReached("private-42", JsonPrimitive(payload.content.replace("42", "99")))
            assertEquals(8, repo.state.value.challenges.single().condition.progress.toInt())
            repo.onGoalReached("private-42", payload)
            assertTrue(repo.state.value.challenges.single().canClaim(Instant.now()))
            yield()
            withTimeout(3000) { repo.state.first { !it.loading } }
            assertTrue(repo.state.value.challenges.single().canClaim(Instant.now()))
        } finally { job.cancelAndJoin() }
    }
    @Test fun newDailyWindowDoesNotInheritYesterdayProgress() = runBlocking<Unit> {
        var data = challenge()
        val auth = signedInAuth { json.parseToJsonElement(json.encodeToString(listOf(data))) }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3000) { repo.state.first { it.loaded } }
            repo.onGoalReached("private-42", JsonPrimitive("""{"user_id":42,"challenge_id":"daily-id","recurrence":"daily","threshold_minutes":60}"""))
            withTimeout(3000) { repo.state.first { !it.loading } }
            data = challenge(Instant.now().plusSeconds(86400)).copy(condition = Condition(0, 60, "watch_time_minutes"))
            repo.refresh()
            withTimeout(3000) { repo.state.first { !it.loading && it.challenges.single().window == data.window } }
            assertEquals(0, repo.state.value.challenges.single().condition.progress.toInt())
        } finally { job.cancelAndJoin() }
    }
}
