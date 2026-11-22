package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.lechixy.kick.R
import com.lechixy.kick.screens.channel.DropsUiState
import com.lechixy.kick.screens.settings.DropsBannerPreviewControls
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DropsChatContentTest {
    @get:Rule val compose = createComposeRule()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val title get() = label(R.string.developer_drops_sample_title)

    @After fun resetPreview() { DeveloperDropsPreview.hide() }

    @Test fun countdownHidesBannerAndReopeningChatRestartsIt() {
        var chatOpen by mutableStateOf(true)
        var dismissals = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val sample = developerDropsCampaign("Channel", "Category")
                Box(Modifier.size(320.dp, 360.dp).testTag("chat")) {
                    DropsChatContent(
                        banner = sample.banner.takeIf { chatOpen },
                        previewId = null,
                        onDismiss = { dismissals++ },
                        onOpen = { _, _ -> },
                        chatActive = chatOpen,
                        chatContent = { Box(Modifier.fillMaxSize().testTag("messages")) }
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(label(R.string.drops_how_to_earn)).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(9_500)
        val progress = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertTrue(progress.current in 0.45f..0.55f)
        compose.mainClock.advanceTimeBy(9_500)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(0, dismissals)
        assertEquals(compose.onNodeWithTag("chat").getUnclippedBoundsInRoot(),
            compose.onNodeWithTag("messages").getUnclippedBoundsInRoot())

        compose.runOnIdle { chatOpen = false }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { chatOpen = true }
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.drops_how_to_earn)).assertIsDisplayed()
    }

    @Test fun detailsAndDismissalKeepCompactPhoneComposerInPlace() {
        var chatOpen by mutableStateOf(true)
        var dismissed by mutableStateOf(false)
        var detailsOpen by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                val sample = developerDropsCampaign("Channel with a long name", "Category with a long name")
                Box(Modifier.size(320.dp, 248.dp).testTag("chat")) {
                    ChatComposerPanel(true, true, false, {}, {},
                        chatContent = {
                            DropsChatContent(
                                banner = sample.banner.takeIf { chatOpen && !dismissed },
                                previewId = null,
                                onDismiss = { dismissed = true },
                                onOpen = { _, _ -> detailsOpen = true },
                                chatActive = chatOpen,
                                chatContent = { Box(Modifier.fillMaxSize().testTag("messages")) }
                            )
                        }
                    ) { OutlinedTextField("", {}, Modifier.testTag("input")) }
                }
                if (detailsOpen) DropsCampaignDialog(DropsUiState.Success(listOf(sample)), {}, { detailsOpen = false })
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val input = compose.onNodeWithTag("input")
        val initialInput = input.getUnclippedBoundsInRoot()
        val initialMessages = compose.onNodeWithTag("messages").getUnclippedBoundsInRoot()
        assertTrue(initialMessages.bottom > initialMessages.top)
        assertEquals(initialInput.top, initialMessages.bottom)
        scrollToBannerActions()
        compose.onNodeWithText(label(R.string.action_view)).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(label(R.string.drops_and_rewards)).assertIsDisplayed()
        compose.runOnIdle { detailsOpen = false }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(initialInput, input.getUnclippedBoundsInRoot())

        compose.runOnIdle { chatOpen = false }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { chatOpen = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(title).assertIsDisplayed()
        scrollToBannerActions()
        compose.onNodeWithText(label(R.string.action_dismiss)).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        assertTrue(dismissed)
        compose.runOnIdle { chatOpen = false }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { chatOpen = true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(initialInput, input.getUnclippedBoundsInRoot())
    }

    @Test fun developerControlsShowHideAndRestartPreview() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val previewId by DeveloperDropsPreview.requestId.collectAsState()
            MaterialTheme {
                Column {
                    DropsBannerPreviewControls()
                    Box(Modifier.size(320.dp, 300.dp)) {
                        val sample = developerDropsCampaign("Channel", "Category")
                        DropsChatContent(sample.banner.takeIf { previewId != null }, previewId,
                            onDismiss = { DeveloperDropsPreview.hide() }, onOpen = { _, _ -> }, chatContent = {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val show = compose.onNodeWithText(label(R.string.developer_drops_show))
        val hide = compose.onNodeWithText(label(R.string.developer_drops_hide))
        hide.assertIsNotEnabled()
        show.performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(title).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(label(R.string.drops_how_to_earn)).assertDoesNotExist()
        show.performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(label(R.string.drops_how_to_earn)).assertIsDisplayed()
        hide.performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithText(title).assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(title).assertDoesNotExist()
    }

    private fun scrollToBannerActions() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> assertTrue(scrollBy(0f, 1_000f)) }
        compose.mainClock.advanceTimeBy(600)
    }
}
