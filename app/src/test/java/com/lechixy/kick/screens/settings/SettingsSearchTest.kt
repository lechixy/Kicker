package com.lechixy.kick.screens.settings

import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class SettingsSearchTest {
    @Test fun turkishSearchHandlesDottedAndDotlessIAndAsciiTyping() {
        val tr = Locale.forLanguageTag("tr")
        assertTrue(matchesSettingsQuery("YAZI BOYUTU", "Sohbet yazı boyutu", "", "Sohbet", tr))
        assertTrue(matchesSettingsQuery("baglanti", "Otomatik bağlantı", "", "Sohbet", tr))
        assertTrue(matchesSettingsQuery("sohbet boyut", "Yazı boyutu", "", "Sohbet", tr))
        assertFalse(matchesSettingsQuery("playback", "Yazı boyutu", "", "Sohbet", tr))
    }
    @Test fun searchesLocalizedDescriptionsAndAllQueryWords() {
        assertTrue(matchesSettingsQuery("video quality", "Default quality", "Choose video quality", "Playback", Locale.ENGLISH))
        assertFalse(matchesSettingsQuery("video missing", "Default quality", "Choose video quality", "Playback", Locale.ENGLISH))
    }
}
