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
import com.lechixy.kick.components.StreamCard
import com.lechixy.kick.components.VideoCard
import com.lechixy.kick.data.browse.model.BrowseCategory
import com.lechixy.kick.data.channel.model.ChannelVideo
import com.lechixy.kick.data.channel.model.ChannelEvent
import com.lechixy.kick.data.channel.model.ChannelEventCategory
import com.lechixy.kick.data.common.model.Category
import com.lechixy.kick.data.common.model.Livestream
import com.lechixy.kick.screens.events.UpcomingEventCard

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

    @Test fun compactStreamsKeepTwoTagsOnOneLineAndMatchCardsWithoutTags() {
        val longCategory = "Grand Theft Auto V – Very Long Roleplay Category"
        compose.setContent {
            MaterialTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamCard(
                        Livestream(id = "long", title = "A long live title that needs truncation",
                            category = Category(1, longCategory, "gta"), language = "en"),
                        modifier = Modifier.width(156.dp).testTag("with-tags"),
                        compact = true,
                    )
                    StreamCard(
                        Livestream(id = "short", title = "Short"),
                        modifier = Modifier.width(156.dp).testTag("without-tags"),
                        compact = true,
                    )
                }
            }
        }
        val category = compose.onNodeWithText(longCategory, useUnmergedTree = true)
        val language = compose.onNodeWithText("English", useUnmergedTree = true)
        category.assertIsDisplayed()
        language.assertIsDisplayed()
        assertEquals(category.getUnclippedBoundsInRoot().top.value, language.getUnclippedBoundsInRoot().top.value, .1f)
        assertSameCardHeight("with-tags", "without-tags")
    }

    @Test fun videoCardsMatchWhenOnlyOneHasLongTitleAndMetadata() {
        compose.setContent {
            MaterialTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoCard(
                        ChannelVideo(id = 1, sessionTitle = "A video with a very long title that must fit on one line",
                            createdAt = "2026-01-01T13:20:00Z", categories = listOf(Category(1, "Long category", "long"))),
                        modifier = Modifier.width(156.dp).testTag("video-with-metadata"),
                    )
                    VideoCard(
                        ChannelVideo(id = 2, sessionTitle = "Short"),
                        modifier = Modifier.width(156.dp).testTag("video-without-metadata"),
                    )
                }
            }
        }
        assertSameCardHeight("video-with-metadata", "video-without-metadata")
    }

    @Test fun upcomingCardsMatchWhenOnlyOneHasDescriptionAndTags() {
        compose.setContent {
            MaterialTheme {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpcomingEventCard(
                        ChannelEvent(
                            title = "An upcoming event with a title that must fit on one line",
                            description = "A description long enough to occupy both lines in a narrow card.",
                            startTime = "2026-06-17T10:00:00Z", endTime = "2026-06-17T18:00:00Z",
                            category = ChannelEventCategory(name = "A long category"), defaultLanguage = "en",
                        ),
                        modifier = Modifier.width(156.dp).testTag("event-with-metadata"),
                        isReminderSet = false, isReminderPending = false,
                        onClick = {}, onReminderClick = {},
                    )
                    UpcomingEventCard(
                        ChannelEvent(title = "Short"),
                        modifier = Modifier.width(156.dp).testTag("event-without-metadata"),
                        isReminderSet = false, isReminderPending = false,
                        onClick = {}, onReminderClick = {},
                    )
                }
            }
        }
        assertSameCardHeight("event-with-metadata", "event-without-metadata")
    }

    private fun assertSameCardHeight(firstTag: String, secondTag: String) {
        val first = compose.onNodeWithTag(firstTag).getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag(secondTag).getUnclippedBoundsInRoot()
        assertEquals((first.bottom - first.top).value, (second.bottom - second.top).value, .1f)
    }

}
