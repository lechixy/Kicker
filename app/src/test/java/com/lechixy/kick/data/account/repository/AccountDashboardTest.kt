package com.lechixy.kick.data.account.repository

import com.lechixy.kick.data.account.support.objectJson
import com.lechixy.kick.data.account.support.signedInAuth

import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

import com.lechixy.kick.data.account.model.AccountChannelInfo
import com.lechixy.kick.data.account.model.AccountDashboardSection
import com.lechixy.kick.data.account.model.AccountLevelResponse
import com.lechixy.kick.data.account.remote.AccountApiException
import com.lechixy.kick.data.account.repository.AccountDashboardRepository

class AccountDashboardTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val channel = """{"slug":"tester","banner_image":{"src":"banner"},"offline_banner_image":{"src":"offline"},"user":{"username":"Tester","profile_pic":"avatar","bio":"ignored"}}"""
    private val level = """{"data":{"level":21,"progress_xp":725,"xp_to_next_level":883,"total_xp":11104,"user_id":42,"badge":{"animated_image_url":"badge.gif","image_url":"badge.png","metadata":{"level":21}}}}"""

    @Test fun schemaMapsProgressImagesAndTierBoundaries() {
        val info = json.decodeFromString<AccountChannelInfo>(channel)
        assertEquals("banner", info.banner?.src)
        assertEquals("offline", info.offlineBanner?.src)
        assertEquals("avatar", info.user.avatar)
        val data = json.decodeFromString<AccountLevelResponse>(level).data
        assertEquals(725f / 1608f, data.progress, 0.0001f)
        assertEquals("badge.gif", data.badge?.animatedImage)
        for ((number, tier) in listOf(0 to 20, 9 to 20, 10 to 20, 19 to 20, 20 to 20, 21 to 20, 99 to 90, 100 to 90)) {
            assertEquals("https://static.kick.com/tier/background/level-$tier.webp", data.copy(level = number).tierBackground)
        }
        assertEquals(0f, data.copy(progressXp = 0, xpToNextLevel = 0).progress)
        assertEquals(1f, data.copy(progressXp = 100, xpToNextLevel = 0).progress)
    }

    @Test fun dashboardUsesCurrentAccountAndLoadsZeroDayStreak() = runBlocking<Unit> {
        val paths = mutableSetOf<String>()
        val auth = signedInAuth { request ->
            synchronized(paths) { paths += request.url.encodedPath }
            assertEquals("Bearer access", request.header("Authorization"))
            when (request.url.encodedPath) {
                "/api/v2/channels/tester/info" -> objectJson(channel)
                "/api/v1/gamification/user/level" -> objectJson(level)
                "/api/v1/gamification/users/42/streak" -> objectJson("""{"data":{"length_days":0}}""")
                else -> error("Unexpected URL")
            }
        }
        val job = SupervisorJob()
        val repository = AccountDashboardRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            yield()
            repository.refresh()
            val loaded = withTimeout(3000.milliseconds) { repository.state.first { !it.loading && it.channel != null && it.level != null && it.streak != null } }
            assertEquals(0, loaded.streak?.days)
            assertTrue(loaded.failures.isEmpty())
            assertEquals(3, paths.size)
            auth.signOut()
            withTimeout(3000.milliseconds) { repository.state.first { it.channel == null && it.level == null && it.streak == null } }
        } finally { job.cancelAndJoin() }
    }

    @Test fun unavailableStreakDoesNotDiscardSuccessfulProfileAndLevel() = runBlocking<Unit> {
        val auth = signedInAuth { request ->
            when {
                request.url.encodedPath.endsWith("/info") -> objectJson(channel)
                request.url.encodedPath.endsWith("/level") -> objectJson(level)
                else -> throw AccountApiException(403)
            }
        }
        val job = SupervisorJob()
        val repository = AccountDashboardRepository(auth, CoroutineScope(coroutineContext + job))
        try {
            yield()
            repository.refresh()
            val loaded = withTimeout(3000.milliseconds) { repository.state.first { !it.loading && it.level != null && it.channel != null } }
            assertNull(loaded.streak)
            assertEquals(setOf(AccountDashboardSection.Streak), loaded.failures)
            assertTrue(auth.session.value.signedIn)
        } finally { job.cancelAndJoin() }
    }
}
