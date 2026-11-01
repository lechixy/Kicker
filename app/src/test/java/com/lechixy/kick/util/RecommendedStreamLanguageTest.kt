package com.lechixy.kick.util

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RecommendedStreamLanguageTest {
    @Test fun regionControlsInitialRecommendationIndependentlyFromAppLanguage() {
        assertEquals("tr", recommendedStreamLanguage(Locale.forLanguageTag("en-TR")))
        assertEquals("en", recommendedStreamLanguage(Locale.forLanguageTag("tr-US")))
        assertEquals("de", recommendedStreamLanguage(Locale.forLanguageTag("en-DE")))
        assertEquals("pt", recommendedStreamLanguage(Locale.forLanguageTag("en-BR")))
    }
    @Test fun multilingualRegionsRespectTheSystemLanguage() {
        assertEquals("fr", recommendedStreamLanguage(Locale.CANADA_FRENCH))
        assertEquals("en", recommendedStreamLanguage(Locale.CANADA))
        assertEquals("it", recommendedStreamLanguage(Locale.forLanguageTag("it-CH")))
        assertEquals("nl", recommendedStreamLanguage(Locale.forLanguageTag("nl-BE")))
    }
    @Test fun kickCodesAreLowercaseIsoBaseLanguages() {
        assertEquals("tr", kickLanguageCode("tr_TR"))
        assertEquals("en", kickLanguageCode(" EN-us "))
        assertEquals("zh", kickLanguageCode("zh-Hant-TW"))
        assertEquals("he", kickLanguageCode("iw-IL"))
        assertNull(kickLanguageCode("not-a-language"))
        assertEquals("en", recommendedStreamLanguage(Locale.ROOT))
    }
}
