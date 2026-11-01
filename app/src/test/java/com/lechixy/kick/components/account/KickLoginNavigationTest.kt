package com.lechixy.kick.components.account

import org.junit.Assert.*
import org.junit.Test

class KickLoginNavigationTest {
    @Test fun kickAndGoogleCanCompleteTheRoundTrip() {
        listOf("https://kick.com/?auth=sign-in", "https://accounts.google.com/o/oauth2/v2/auth?state=test",
            "https://accounts.google.com/v3/signin/identifier", "https://kick.com/social/google/callback?code=test&state=test",
            "https://web.kick.com/").forEach { assertTrue(it, isAllowedLoginUrl(it)) }
        assertFalse(isKickLoginUrl("https://accounts.google.com/"))
    }

    @Test fun googleSessionHandoffAndRegionalAccountsDoNotInterruptLogin() {
        listOf(
            "https://accounts.youtube.com/accounts/SetSID?continue=https%3A%2F%2Faccounts.google.com",
            "https://accounts.google.com.tr/accounts/SetSID?continue=https%3A%2F%2Fkick.com",
            "https://accounts.google.co.uk/accounts/SetSID",
            "https://accounts.google.de/", "https://accounts.google.com.au/"
        ).forEach {
            assertTrue(it, isAllowedLoginUrl(it))
            assertFalse(it, isKickLoginUrl(it))
        }
    }

    @Test fun lookalikesUnsafeSchemesAndUnexpectedPortsAreBlocked() {
        listOf("http://kick.com/", "javascript:alert(1)", "intent://login", "https://kick.com.evil.test/",
            "https://accounts.google.com.evil.test/", "https://evilkick.com/", "https://kick.com@evil.test/",
            "https://evil.test@kick.com/", "https://kick.com:444/", "https://google.com/", "malformed",
            "https://accounts.youtube.com.evil.test/accounts/SetSID", "https://youtube.com/",
            "https://accounts.google.com.tr.evil.test/", "https://accounts.google.evil/",
            "https://evil.test@accounts.youtube.com/", "http://accounts.google.com.tr/",
            "https://accounts.youtube.com:444/", "https://mail.google.com/").forEach {
            assertFalse(it, isAllowedLoginUrl(it))
        }
    }
}
