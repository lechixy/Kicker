package com.lechixy.kick.components

import org.junit.Assert.*
import org.junit.Test

class NavigationSelectionTest {
    @Test fun channelSelectionReplacesScreenSelectionUntilMinimizedOrClosed() {
        assertFalse(navigationDestinationSelected("settings/general", "rraenee", "settings"))
        assertTrue(navigationDestinationSelected("settings/general", null, "settings"))
        assertTrue(navigationDestinationSelected("following", null, "following"))
        assertTrue(navigationDestinationSelected("browse?query=kick", null, "browse"))
        assertFalse(navigationDestinationSelected("home", null, "settings"))
    }
}
