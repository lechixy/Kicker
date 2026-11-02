package com.lechixy.kick.components.channel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lechixy.kick.screens.channel.ChannelTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChannelTabPagerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingBetweenCompactAndWideTabsKeepsLastTabVisible() {
        var wide by mutableStateOf(false)
        var fullscreen by mutableStateOf(false)
        compose.setContent {
            val tabs = if (wide) ChannelTab.entries.filter { it != ChannelTab.CHAT }
                else ChannelTab.entries
            // Match ChannelScreen: fullscreen and width changes replace PagerState.
            val state = key(wide, fullscreen) {
                rememberPagerState(initialPage = tabs.lastIndex) { tabs.size }
            }
            ChannelTabPager(state, tabs, Modifier.fillMaxSize()) { Text(it.name) }
        }
        compose.onNodeWithText(ChannelTab.CATEGORIES.name).assertIsDisplayed()
        repeat(3) {
            compose.runOnIdle { fullscreen = true }
            compose.runOnIdle { wide = true }
            compose.onNodeWithText(ChannelTab.CATEGORIES.name).assertIsDisplayed()
            compose.runOnIdle { fullscreen = false }
            compose.runOnIdle { wide = false }
            compose.onNodeWithText(ChannelTab.CATEGORIES.name).assertIsDisplayed()
        }
    }
}
