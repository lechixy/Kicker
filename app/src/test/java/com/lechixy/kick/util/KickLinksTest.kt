package com.lechixy.kick.util

import com.lechixy.kick.screens.channel.ChannelTab
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KickLinksTest {
    @Test
    fun incomingLinksOnlyAcceptDeclaredKickHosts() {
        assertEquals("kick.com", incomingKickLink("https://kick.com/some-channel")?.host)
        assertEquals("www.kick.com", incomingKickLink("http://www.kick.com/some-channel")?.host)
        assertNull(incomingKickLink("https://kick.com.example.org/some-channel"))
        assertNull(incomingKickLink("https://kick.com@evil.example/some-channel"))
        assertNull(incomingKickLink("https://user@kick.com/some-channel"))
        assertNull(incomingKickLink("https://kick.com:8443/some-channel"))
        assertNull(incomingKickLink("intent://kick.com/some-channel"))
    }

    @Test
    fun routesKickContentToNativeScreens() {
        assertEquals(KickLink.Channel("some-channel"), route("https://www.kick.com/Some-Channel/?ref=share"))
        assertEquals(KickLink.Channel("some-channel", ChannelTab.VIDEOS), route("https://kick.com/some-channel/videos"))
        assertEquals(KickLink.Clip("clip_abc123"), route("https://kick.com/some-channel?clip=clip_abc123"))
        assertEquals(KickLink.Clip("clip_abc123"), route("https://kick.com/some-channel/clips/clip_abc123"))
        assertEquals(KickLink.Category("just-chatting"), route("https://kick.com/category/just-chatting"))
        assertEquals(KickLink.Screen("following"), route("https://kick.com/following"))
        val videoId = "12345678-1234-1234-1234-123456789abc"
        assertEquals(KickLink.Recording("some-channel", videoId), route("https://kick.com/some-channel/videos/$videoId"))
    }

    @Test
    fun webOnlyPagesStayInTheBrowserTab() {
        assertNull(route("https://kick.com/privacy-policy"))
        assertNull(route("https://kick.com/settings/profile"))
        assertNull(route("https://kick.com/some-channel/subscribe"))
        assertNull(route("https://example.org/some-channel"))
        assertNull(route("https://kick.com/some%2Fchannel"))
    }

    private fun route(url: String) = kickLinkDestination(url.toHttpUrl())
}
