package com.lechixy.kick.data.releases

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
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
}
