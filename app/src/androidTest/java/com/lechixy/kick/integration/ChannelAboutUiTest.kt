package com.lechixy.kick.integration

import com.lechixy.kick.data.account.remote.string

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

import com.lechixy.kick.R
import com.lechixy.kick.components.channel.ChannelAboutTabContent
import com.lechixy.kick.data.channel.model.ChannelDetail
import com.lechixy.kick.data.channel.model.ChannelSectionState
import com.lechixy.kick.data.channel.model.ChannelUser
import com.lechixy.kick.data.channel.model.KickChannelLinkDto
import com.lechixy.kick.data.channel.model.KickGoalDto

@RunWith(AndroidJUnit4::class)
class ChannelAboutUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun showWindow() { compose.activityRule.scenario.onActivity {
        it.setShowWhenLocked(true); it.setTurnScreenOn(true)
        it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } }

    @Test fun existingProfileActiveGoalAndOrderedLinksRenderAndOpenSafely() {
        val opened = mutableListOf<String>()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ChannelAboutTabContent(
                    ChannelDetail(7, slug = "tester", followersCount = "1700000", verified = true,
                        user = ChannelUser(8, "Tester", bio = "contact@example.com", instagram = "tester")),
                    ChannelSectionState(value = listOf(
                        KickChannelLinkDto(2, 7, order = 2, title = "Second panel", link = "javascript:alert(1)"),
                        KickChannelLinkDto(1, 7, order = 1, title = "First panel", link = "https://example.com/first")), loaded = true),
                    ChannelSectionState(value = listOf(KickGoalDto("g", "7", "subscriptions", "active", 4000, 3583)), loaded = true),
                    onRetry = {}, onLinkClick = { opened += it })
            }
        } }
        compose.onNodeWithText("contact@example.com").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.channel_goal_remaining, 417)).assertIsDisplayed()
        compose.onNodeWithText("First panel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("https://example.com/first"), opened) }
        compose.onNodeWithText("Second panel").performScrollTo().assertIsNotEnabled()
    }

    @Test fun linkFailureKeepsExistingProfileVisibleAndOffersRetry() {
        var retries = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { MaterialTheme {
            ChannelAboutTabContent(ChannelDetail(7, slug = "tester", user = ChannelUser(8, "Tester", bio = "Existing bio")),
                ChannelSectionState(failed = true), ChannelSectionState(value = emptyList(), loaded = true), { retries++ })
        } }
        compose.onNodeWithText("Existing bio").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.chat_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
