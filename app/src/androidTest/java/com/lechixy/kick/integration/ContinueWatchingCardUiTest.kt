package com.lechixy.kick.integration

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.components.ContinueWatchingCard
import com.lechixy.kick.data.player.local.WatchHistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ContinueWatchingCardUiTest {
    @get:Rule val compose = createComposeRule()

    private val entry = WatchHistoryEntry("example", "test-channel", "Test channel", "Saved stream")

    @Test
    fun singleTapShowsActionsAndPlayResumes() {
        var resumed = 0
        compose.setContent {
            MaterialTheme {
                ContinueWatchingCard(entry, onRemove = {}) { resumed++ }
            }
        }

        compose.onNodeWithContentDescription(entry.title).performTouchInput { click() }
        val play = label(R.string.home_continue_watching)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(play).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(0, resumed) }
        compose.onNodeWithContentDescription(label(R.string.home_remove_history)).assertIsDisplayed()
        compose.onNodeWithContentDescription(play).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, resumed) }
    }

    @Test
    fun doubleTapResumesWithoutShowingActions() {
        var resumed = 0
        compose.setContent {
            MaterialTheme {
                ContinueWatchingCard(entry, onRemove = {}) { resumed++ }
            }
        }

        compose.onNodeWithContentDescription(entry.title).performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(1, resumed) }
        compose.onNodeWithContentDescription(label(R.string.home_remove_history)).assertDoesNotExist()
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
