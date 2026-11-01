package com.lechixy.kick.components.channel.chat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ChatEventTextTest {
    @Test fun translationCanReorderStyledNamesAndCounts() {
        val name = eventUser("alice", Color.Red)
        val count = eventUser("3", Color.Green, username = null)
        val result = eventTextSegments("%2\$s gifts from %1\$s", name, count)
        assertEquals("3 gifts from alice", result.joinToString("") { it.text })
        assertEquals("alice", result.last().username)
        assertEquals(Color.Red, result.last().color)
        assertNull(result.first().username)
    }

    @Test fun literalPercentRepeatedArgumentsAndUserTextArePreserved() {
        val user = eventUser("user%2\$s", Color.Red)
        val result = eventTextSegments("%1\$s: 100%% / %1\$s", user)
        assertEquals("user%2\$s: 100% / user%2\$s", result.joinToString("") { it.text })
        assertEquals(2, result.count { it.username == "user%2\$s" })
    }
}
