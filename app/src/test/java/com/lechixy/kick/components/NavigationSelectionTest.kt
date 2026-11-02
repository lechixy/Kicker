package com.lechixy.kick.components

import org.junit.Assert.*
import org.junit.Test

class NavigationSelectionTest {
    @Test fun channelSelectionReplacesScreenSelectionUntilMinimizedOrClosed() {
        assertFalse(navigationDestinationSelected("settings/general", "rraenee", "settings"))
        assertTrue(navigationDestinationSelected("settings/general", null, "settings"))
        assertTrue(navigationDestinationSelected("following", null, "following"))
        assertTrue(navigationDestinationSelected("search?query=kick", null, "search"))
        assertTrue(navigationDestinationSelected("browse?slug=just-chatting", null, "browse"))
        assertFalse(navigationDestinationSelected("search?query=kick", null, "browse"))
        assertFalse(navigationDestinationSelected("browse?slug=just-chatting", null, "search"))
        assertFalse(navigationDestinationSelected("home", null, "settings"))
    }
}
