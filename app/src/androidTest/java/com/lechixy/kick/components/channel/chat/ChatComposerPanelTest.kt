package com.lechixy.kick.components.channel.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import org.junit.Before
import com.lechixy.kick.R
import com.lechixy.kick.screens.channel.DropsUiState
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class ChatComposerPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Before fun prepare() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
            it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent(content)
        compose.mainClock.advanceTimeByFrame()
        compose.waitUntil(20_000) {
            runCatching { compose.onAllNodes(isRoot()).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
    }

    @Test fun startsCollapsedAndHidesFiveSecondsAfterOpening() {
        compose.mainClock.autoAdvance = false
        show {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                ChatComposerPanel(true, expanded, true, { expanded = it }, {}) {
                    OutlinedTextField("", {}, placeholder = { androidx.compose.material3.Text("Test input") })
                }
            }
        }
        compose.onNodeWithText("Test input").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.chat_show_composer)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Test input").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_800)
        compose.onNodeWithText("Test input").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithText("Test input").assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.chat_show_composer)).assertIsDisplayed()
    }

    @Test fun focusedInputAndDraftPreventAutomaticHidingAndManualHidingKeepsDraft() {
        compose.mainClock.autoAdvance = false
        show {
            var expanded by remember { mutableStateOf(false) }
            var draft by remember { mutableStateOf("") }
            var focused by remember { mutableStateOf(false) }
            MaterialTheme {
                ChatComposerPanel(true, expanded, draft.isBlank() && !focused, { expanded = it }, {}) {
                    OutlinedTextField(draft, { draft = it }, Modifier.onFocusChanged { focused = it.isFocused })
                }
            }
        }
        compose.onNodeWithContentDescription(label(R.string.chat_show_composer)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasSetTextAction()).performClick()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Saved draft")
        compose.onNodeWithContentDescription(label(R.string.chat_hide_composer)).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithContentDescription(label(R.string.chat_show_composer)).performClick()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Saved draft").assertIsDisplayed()
    }

    @Test fun accessoriesSuspendTimerAndClosingThemStartsAFreshFiveSeconds() {
        var accessoryOpen by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        show {
            var expanded by remember { mutableStateOf(true) }
            MaterialTheme {
                ChatComposerPanel(true, expanded, !accessoryOpen, { expanded = it }, {}) {
                    androidx.compose.material3.Text("Composer contents")
                }
            }
        }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Composer contents").assertIsDisplayed()
        compose.runOnIdle { accessoryOpen = false }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(4_800)
        compose.onNodeWithText("Composer contents").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Composer contents").assertDoesNotExist()
    }

    @Test fun collapsedComposerFloatsInsideTheFullMessageArea() {
        show {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("chat")) {
                    ChatComposerPanel(true, expanded, false, { expanded = it }, {},
                        chatContent = { Box(Modifier.fillMaxSize().testTag("messages")) }
                    ) {
                        OutlinedTextField("", {}, placeholder = { androidx.compose.material3.Text("Test input") })
                    }
                }
            }
        }
        val chat = compose.onNodeWithTag("chat").getUnclippedBoundsInRoot()
        val messages = compose.onNodeWithTag("messages")
        val restore = compose.onNodeWithContentDescription(label(R.string.chat_show_composer))
        messages.assertHeightIsEqualTo(chat.bottom - chat.top)
        restore.assertIsDisplayed()
        restore.performClick()
        compose.onNodeWithText("Test input").assertIsDisplayed()
        val expandedMessages = messages.getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(expandedMessages.bottom - expandedMessages.top < chat.bottom - chat.top)
        compose.onNodeWithContentDescription(label(R.string.chat_hide_composer)).performClick()
        messages.assertHeightIsEqualTo(chat.bottom - chat.top)
    }

    @Test fun largerLayoutsKeepComposerVisible() {
        compose.mainClock.autoAdvance = false
        show {
            MaterialTheme {
                ChatComposerPanel(false, false, true, {}, {}) {
                    androidx.compose.material3.Text("Composer contents")
                }
            }
        }
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNodeWithText("Composer contents").assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.chat_show_composer)).assertDoesNotExist()
    }

    @Test fun compactFooterButtonsMatchAndComposerOpensOverMultipleFrames() {
        compose.mainClock.autoAdvance = false
        show {
            var expanded by remember { mutableStateOf(false) }
            MaterialTheme {
                ChatComposerPanel(
                    compact = true, expanded = expanded, allowAutoHide = false,
                    onExpandedChange = { expanded = it }, onHide = {},
                    collapsedActions = { DropsRewardsButton(DropsUiState.Empty, compactOverlay = true) {} },
                    chatContent = { Box(Modifier.fillMaxSize().testTag("messages")) }
                ) { OutlinedTextField("", {}, Modifier.testTag("input")) }
            }
        }
        val restore = compose.onNodeWithContentDescription(label(R.string.chat_show_composer))
        val drops = compose.onNodeWithContentDescription(label(R.string.drops_and_rewards))
        val chatBounds = restore.getUnclippedBoundsInRoot()
        val dropsBounds = drops.getUnclippedBoundsInRoot()
        org.junit.Assert.assertEquals(chatBounds.right - chatBounds.left, dropsBounds.right - dropsBounds.left)
        org.junit.Assert.assertEquals(chatBounds.bottom - chatBounds.top, dropsBounds.bottom - dropsBounds.top)
        org.junit.Assert.assertEquals(chatBounds.bottom, dropsBounds.bottom)
        org.junit.Assert.assertTrue(dropsBounds.right < chatBounds.left)

        val messages = compose.onNodeWithTag("messages")
        val initial = messages.getUnclippedBoundsInRoot().bottom
        restore.performClick()
        compose.mainClock.advanceTimeBy(100)
        val animating = messages.getUnclippedBoundsInRoot().bottom
        compose.mainClock.advanceTimeBy(300)
        val expanded = messages.getUnclippedBoundsInRoot().bottom
        org.junit.Assert.assertTrue(animating < initial && animating > expanded)
        compose.onNodeWithTag("input").assertIsDisplayed()
    }
}
