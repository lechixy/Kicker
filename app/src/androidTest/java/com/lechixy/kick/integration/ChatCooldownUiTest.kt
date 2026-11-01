package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.ChatComposerTextField
import com.lechixy.kick.components.channel.chat.ChatSendButton
import com.lechixy.kick.data.chat.repository.AccountChatRepository

@RunWith(AndroidJUnit4::class)
class ChatCooldownUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun showWindow() { compose.activityRule.scenario.onActivity {
        it.setShowWhenLocked(true); it.setTurnScreenOn(true)
        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }

    @Test fun buttonShowsCooldownWhileDraftRemainsEditableAndSurvivesExpiry() {
        var draft by mutableStateOf("My next message")
        var remaining by mutableLongStateOf(10_000)
        var sends = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            Column(Modifier.width(600.dp)) {
                ChatComposerTextField(draft, { draft = it }, true)
                ChatSendButton(false, false, AccountChatRepository.Cooldown(remaining, 10_000), true, { sends++ })
            }
        } }
        val label = context.getString(R.string.chat_cooldown_short, 10L)
        compose.onNodeWithText(label).assertIsDisplayed().assertIsNotEnabled()
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("Updated draft")
        compose.runOnIdle { assertEquals("Updated draft", draft); remaining = 0 }
        compose.onNodeWithText(label).assertDoesNotExist()
        field.assertTextContains("Updated draft")
        compose.onNodeWithText(context.getString(R.string.chat_send)).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }
    }

    @Test fun sendingAndLoadingTakePriorityOverTheCooldownLabel() {
        var sending by mutableStateOf(true)
        var loading by mutableStateOf(true)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            ChatSendButton(sending, loading, AccountChatRepository.Cooldown(10_000, 10_000), true, {})
        } }
        val cooldown = context.getString(R.string.chat_cooldown_short, 10L)
        compose.onNodeWithText(context.getString(R.string.chat_sending)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(cooldown).assertDoesNotExist()
        compose.runOnIdle { sending = false }
        compose.onNodeWithText(context.getString(R.string.chat_loading)).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText(cooldown).assertDoesNotExist()
        compose.runOnIdle { loading = false }
        compose.onNodeWithText(cooldown).assertIsDisplayed().assertIsNotEnabled()
    }
}
