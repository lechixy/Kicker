package com.lechixy.kick.components.account

import org.junit.Assert.*
import org.junit.Test

class KickLoginUserAgentTest {
    @Test fun tabletGetsMobileLayoutWithoutPinningAnOldChromeOrAnotherDevice() {
        val input = "Mozilla/5.0 (Linux; Android 16; SM-X930 Build/TEST; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.31 Safari/537.36"
        val result = kickLoginUserAgent(input)
        assertTrue(result.contains("Chrome/144.0.7559.31 Mobile Safari/537.36"))
        assertTrue(result.contains("Android 16; SM-X930"))
        assertFalse(result.contains("; wv"))
        assertFalse(result.contains("Version/4.0"))
    }
    @Test fun phoneDoesNotAcquireDuplicateMobileTokens() {
        val input = "Mozilla/5.0 (Linux; Android 16; Pixel; wv) AppleWebKit/537.36 Version/4.0 Chrome/144.0.0.0 Mobile Safari/537.36"
        val result = kickLoginUserAgent(input)
        assertEquals(1, Regex("\\bMobile\\b").findAll(result).count())
        assertEquals(result, kickLoginUserAgent(result))
    }
}
