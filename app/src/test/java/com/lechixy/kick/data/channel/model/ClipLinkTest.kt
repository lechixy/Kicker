package com.lechixy.kick.data.channel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipLinkTest {
    private val id = "clip_01M6W0BW1D4E268AVEWPMVP7TA"

    @Test fun clipIdComesFromKickClipPathOrQuery() {
        assertEquals(id, kickClipId("https://kick.com/rraenee/clips/$id?source=chat"))
        assertEquals(id, kickClipId("https://www.kick.com/rraenee?clip=$id"))
    }

    @Test fun similarHostsAndNonClipPathsKeepTheirNormalLinkBehavior() {
        assertNull(kickClipId("https://kick.com.example.org/rraenee/clips/$id"))
        assertNull(kickClipId("https://kick.com@elsewhere.test/rraenee/clips/$id"))
        assertNull(kickClipId("https://kick.com/rraenee/videos/$id"))
        assertNull(kickClipId("https://kick.com/rraenee?clip=invalid"))
    }
}
