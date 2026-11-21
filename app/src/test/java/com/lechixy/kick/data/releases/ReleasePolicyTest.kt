package com.lechixy.kick.data.releases

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReleasePolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun comparesNumericVersionsInsteadOfStrings() {
        assertEquals(0, compareVersions(" v1.2.0 ", "V1.2"))
        assertTrue(compareVersions("1.10.0", "1.9.9")!! > 0)
        assertTrue(compareVersions("2.0", "1.99.99")!! > 0)
        assertEquals(0, compareVersions("1.0.0+build.1", "1.0"))
    }
    @Test fun respectsPrereleasePrecedence() {
        val ordered = listOf("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0")
        ordered.zipWithNext { left, right -> assertTrue("$left < $right", compareVersions(left, right)!! < 0) }
        assertNull(compareVersions("release-main", "1.0"))
    }
    @Test fun excludesDraftsAndPrereleasesForStableUsersRegardlessOfApiOrder() {
        val releases = listOf(GitHubRelease("1.1"), GitHubRelease("9.0", draft = true),
            GitHubRelease("2.0-beta.1", prerelease = true), GitHubRelease("1.10"), GitHubRelease("1.2"))
        assertEquals("1.10", newestRelease(releases, "1.0")?.tagName)
        assertEquals("2.0-beta.1", newestRelease(releases, "2.0-alpha.1")?.tagName)
        assertNull(newestRelease(releases, "10.0"))
    }

    @Test fun sharesReleaseHistoryAcrossConcurrentReadsAndAppRestarts() = runBlocking {
        val cacheFile = temporaryFolder.root.resolve("releases.preferences_pb")
        var time = 0L
        var attempts = 0
        var offline = false
        val firstRelease = GitHubRelease("v1.0", body = "Release notes", publishedAt = "2026-10-09T12:30:00Z")
        val secondRelease = firstRelease.copy(tagName = "v2.0")
        var currentRelease = firstRelease
        fun repository(scope: CoroutineScope) = ReleaseRepository(
            cache = PreferenceDataStoreFactory.create(scope = scope) { cacheFile },
            fetchReleases = {
                attempts++
                if (offline) throw IOException("offline")
                listOf(currentRelease, GitHubRelease("v3.0", draft = true))
            },
            now = { time },
        )

        val firstJob = Job()
        try {
            val first = repository(CoroutineScope(Dispatchers.IO + firstJob))
            val about = async { first.releases() }
            val changelog = async { first.releases() }
            assertEquals(listOf(firstRelease), about.await().releases)
            assertEquals(about.await(), changelog.await())
            assertEquals(1, attempts)
        } finally {
            firstJob.cancelAndJoin()
        }

        val restartJob = Job()
        try {
            val restarted = repository(CoroutineScope(Dispatchers.IO + restartJob))
            assertEquals(ReleaseHistoryResponse(listOf(firstRelease)), restarted.releases())
            assertEquals(1, attempts)

            time += TimeUnit.HOURS.toMillis(1)
            offline = true
            assertEquals(ReleaseHistoryResponse(listOf(firstRelease), refreshFailed = true), restarted.releases())
            assertEquals(2, attempts)
            assertTrue(restarted.releases().refreshFailed)
            assertEquals(2, attempts)

            offline = false
            currentRelease = secondRelease
            assertEquals(ReleaseHistoryResponse(listOf(secondRelease)), restarted.releases(retry = true))
            assertEquals(3, attempts)

            currentRelease = secondRelease.copy(tagName = "v2.1")
            assertEquals(listOf(currentRelease), restarted.releases(versionForNotes = "2.1").releases)
            assertEquals(4, attempts)
        } finally {
            restartJob.cancelAndJoin()
        }
    }
}
