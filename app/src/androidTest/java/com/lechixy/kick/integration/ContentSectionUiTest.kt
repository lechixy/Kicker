package com.lechixy.kick.integration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import com.lechixy.kick.components.CardSection
import com.lechixy.kick.components.CategoryCard
import com.lechixy.kick.components.SectionHeader
import com.lechixy.kick.data.browse.model.BrowseCategory

class ContentSectionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun showTestActivity() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Test fun phonePreviewUsesTwoCardsAndMatchingSkeletonGeometry() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(360.dp)) {
                    CardSection(title = "Live", data = (1..10).toList(), previewRows = 1) {
                        Text("stream$it", Modifier.fillMaxWidth().height(50.dp).testTag("real"))
                    }
                    CardSection<Int>(title = "Loading", data = emptyList(), previewRows = 1, loading = true,
                        skeleton = { Box(Modifier.fillMaxWidth().height(50.dp).testTag("skeleton")) }) {}
                }
            }
        }
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithTag("real").fetchSemanticsNodes().size == 2 }.getOrDefault(false)
        }
        compose.onAllNodesWithTag("real").assertCountEquals(2)
        compose.onAllNodesWithTag("skeleton").assertCountEquals(2)
        val actual = compose.onAllNodesWithTag("real")[0].getUnclippedBoundsInRoot()
        val skeleton = compose.onAllNodesWithTag("skeleton")[0].getUnclippedBoundsInRoot()
        assertEquals((actual.right - actual.left).value, (skeleton.right - skeleton.left).value, .1f)
        assertEquals((actual.bottom - actual.top).value, (skeleton.bottom - skeleton.top).value, .1f)
    }
    @Test fun tabletPreviewUsesTwoCompleteRows() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredWidth(900.dp)) {
                    CardSection(title = "Live", data = (1..20).toList(), previewRows = 2) {
                        Text("stream$it", Modifier.fillMaxWidth().height(30.dp).testTag("real"))
                    }
                }
            }
        }
        compose.onAllNodesWithTag("real").assertCountEquals(6)
        val first = compose.onAllNodesWithTag("real")[0].getUnclippedBoundsInRoot()
        val fourth = compose.onAllNodesWithTag("real")[3].getUnclippedBoundsInRoot()
        assertEquals(first.left.value, fourth.left.value, .1f)
        assertTrue(fourth.top > first.top)
    }
    @Test fun categorySearchCardHidesTagsAndKeepsClickAction() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                CategoryCard(BrowseCategory(name = "Marvel Rivals", slug = "marvel-rivals", tags = listOf("Shooter")),
                    { clicks++ }, Modifier.width(160.dp), showTag = false, showViewers = false)
            }
        }
        compose.onNodeWithText("Shooter").assertDoesNotExist()
        compose.onNodeWithText("Marvel Rivals").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }
    @OptIn(ExperimentalTestApi::class)
    @Test fun actionableHeaderOwnsTheMoreActionAndHighlightsOnHover() {
        var clicks = 0
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF00C8A0))) {
                Surface(color = Color(0xFF101010), modifier = Modifier.width(360.dp)) {
                    SectionHeader("Live section", onMore = { clicks++ })
                }
            }
        }
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithText("Live section").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        val header = compose.onNodeWithText("Live section")
        header.assertHasClickAction()
        val before = header.captureToImage().toPixelMap()
        header.performMouseInput { enter(); moveTo(center) }
        compose.waitForIdle()
        val after = header.captureToImage().toPixelMap()
        assertTrue(after[after.width / 2, after.height - 4].green > before[before.width / 2, before.height - 4].green)
        header.performMouseInput { exit() }
        header.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun ordinaryHeaderHasNoMoreActionAndPosterSkeletonKeepsTheSameSize() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(180.dp)) {
                    SectionHeader("Categories")
                    CategoryCard(BrowseCategory(name = "Game", slug = "game"), {}, Modifier.testTag("poster"))
                    CategoryCard(BrowseCategory(name = "", slug = ""), {}, Modifier.testTag("loading-poster"), loading = true)
                }
            }
        }
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithText("Categories").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithText("Categories").assertHasNoClickAction()
        val poster = compose.onNodeWithTag("poster").getUnclippedBoundsInRoot()
        val skeleton = compose.onNodeWithTag("loading-poster").getUnclippedBoundsInRoot()
        assertEquals((poster.bottom - poster.top).value, (skeleton.bottom - skeleton.top).value, .1f)
        // Aspect-ratio measurement rounds to physical pixels on fractional screen densities.
        assertEquals(270f, (poster.bottom - poster.top).value, 1f)
    }

}
