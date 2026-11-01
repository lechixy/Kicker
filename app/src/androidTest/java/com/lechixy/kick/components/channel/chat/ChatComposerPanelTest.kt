package com.lechixy.kick.components.channel.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import org.junit.Before
import com.lechixy.kick.R
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
        compose.onNodeWithText(label(R.string.chat_show_composer)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Test input").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4_800)
        compose.onNodeWithText("Test input").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Test input").assertDoesNotExist()
        compose.onNodeWithText(label(R.string.chat_show_composer)).assertIsDisplayed()
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
        compose.onNodeWithText(label(R.string.chat_show_composer)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasSetTextAction()).performClick()
        compose.mainClock.advanceTimeBy(6_000)
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Saved draft")
        compose.onNodeWithText(label(R.string.chat_hide_composer)).performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.chat_show_composer)).performClick()
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
        compose.onNodeWithText(label(R.string.chat_show_composer)).assertDoesNotExist()
    }
}
