package com.lechixy.kick.data.account.model

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.account.model.Challenge
import com.lechixy.kick.data.account.model.Condition
import com.lechixy.kick.data.account.model.decodeChallengeClaim
import com.lechixy.kick.data.account.model.shieldRarity
import com.lechixy.kick.data.account.model.shieldReelPlan
import com.lechixy.kick.data.account.repository.ChallengesRepository

class ChallengeClaimTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val ready = Challenge("challenge-id", "Reward", condition = Condition(60, 60, "watch_time_minutes"), status = "completed")
    private val response = objectJson("""{"challenge_id":"challenge-id","reveal_type":"roulette","roulette":[{"id":"other","item_url":"https://cdn.example/Other_Rare_shield.png"},{"id":"winner","item_url":"https://cdn.example/Test_Common_shield.png"}],"winner":{"id":"winner","rarity":"common","card_url":"https://cdn.example/card.png","thumbnail_url":"https://cdn.example/shield.png"}}""")

    @Test fun decodingAndReelAlwaysUseTheServerWinner() {
        for (body in listOf(response, buildJsonObject { put("data", response) })) {
            val claim = decodeChallengeClaim(body, ready.id, json)
            val plan = shieldReelPlan(claim)
            assertEquals(claim.winner.id, plan.items[plan.winnerIndex].id)
            assertTrue(plan.winnerIndex >= 48)
            assertTrue(plan.landingOffsetFraction in -0.25f..0.25f)
            assertEquals(plan, shieldReelPlan(claim))
        }
        assertTrue(runCatching { decodeChallengeClaim(response, "different-challenge", json) }.isFailure)
    }

    @Test fun missingWinnerInReelIsAppendedWithoutSubstitutingAnotherPrize() {
        val claim = decodeChallengeClaim(response, ready.id, json).copy(roulette = emptyList())
        val plan = shieldReelPlan(claim)
        assertEquals("winner", plan.items[plan.winnerIndex].id)
        assertEquals(claim.winner.thumbnailUrl, plan.items[plan.winnerIndex].itemUrl)
        assertEquals("uncommon", shieldRarity("https://cdn.example/Test_Uncommon_shield.png"))
        assertEquals("rare", shieldRarity("https://cdn.example/Test_Rare_shield.png"))
        assertEquals("mythic", shieldRarity("https://cdn.example/Test_Mythical_shield.png"))
        assertEquals("common", shieldRarity("https://cdn.example/hashed-name.png"))
    }

    @Test fun doubleTapAndAcknowledgementSendOnlyOnePostAndStalePollingCannotUnclaim() = runBlocking {
        var posts = 0
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<JsonObject>()
        val auth = signedInAuth { request ->
            if (request.url.encodedPath.endsWith("/claim")) {
                posts++
                assertEquals("POST", request.method)
                assertEquals("/api/v1/gamification/challenges/challenge-id/claim", request.url.encodedPath)
                entered.complete(Unit)
                finish.await()
            } else json.parseToJsonElement(json.encodeToString(listOf(ready)))
        }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3_000) { repo.state.first { it.loaded } }
            repo.claim(ready); repo.claim(ready)
            assertEquals(ready.id, repo.state.value.claimingId)
            withTimeout(3_000) { entered.await() }
            assertEquals(1, posts)
            finish.complete(response)
            withTimeout(3_000) { repo.state.first { it.reward != null } }
            assertTrue(repo.state.value.challenges.single().claimed)
            repo.acknowledgeReward()
            withTimeout(3_000) { repo.state.first { !it.loading } }
            assertNull(repo.state.value.reward)
            assertTrue(repo.state.value.challenges.single().claimed)
            repo.claim(ready); yield()
            assertEquals(1, posts)
        } finally { job.cancelAndJoin() }
    }

    @Test fun acceptedButMalformedRewardDoesNotEnableAnotherClaim() = runBlocking {
        var posts = 0
        val auth = signedInAuth { request ->
            if (request.method == "POST") { posts++; objectJson("""{"message":"OK"}""") }
            else json.parseToJsonElement(json.encodeToString(listOf(ready)))
        }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3_000) { repo.state.first { it.loaded } }
            repo.claim(ready)
            withTimeout(3_000) { repo.state.first { it.claimFailed && !it.loading } }
            assertTrue(repo.state.value.challenges.single().claimed)
            repo.claim(ready); yield()
            assertEquals(1, posts)
        } finally { job.cancelAndJoin() }
    }

    @Test fun signOutDuringClaimCannotRevealPreviousAccountsReward() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val auth = signedInAuth { request ->
            if (request.method == "POST") { entered.complete(Unit); awaitCancellation() }
            else json.parseToJsonElement(json.encodeToString(listOf(ready)))
        }
        val job = SupervisorJob()
        val repo = ChallengesRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            withTimeout(3_000) { repo.state.first { it.loaded } }
            repo.claim(ready)
            entered.await()
            auth.signOut()
            withTimeout(3_000) { repo.state.first { !it.loaded } }
            assertNull(repo.state.value.reward)
            assertNull(repo.state.value.claimingId)
        } finally { job.cancelAndJoin() }
    }
}
