package com.lechixy.kick.integration

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lechixy.kick.R
import com.lechixy.kick.components.channel.chat.ChatMessageItem
import com.lechixy.kick.components.channel.chat.LocalOpenChatClip
import com.lechixy.kick.data.channel.model.ClipChannel
import com.lechixy.kick.data.channel.model.ClipCreator
import com.lechixy.kick.data.channel.model.ClipDetail
import com.lechixy.kick.data.chat.model.ChatMessage
import com.lechixy.kick.screens.clip.ClipPlaybackState
import com.lechixy.kick.screens.clip.ClipPlayerScreen
import com.lechixy.kick.screens.clip.ClipPlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipPlayerUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "clip_01M6W0BW1D4E268AVEWPMVP7TA"

    @Before fun wakeTestActivity() {
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
        }
    }

    @Test fun chatLinkAndPreviewOpenTheSameClip() {
        val clipUrl = "https://kick.com/rraenee/clips/$id"
        val normalUrl = "https://example.com"
        val clips = mutableListOf<String>()
        val otherLinks = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalOpenChatClip provides { clips += it },
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) { otherLinks += uri }
                },
            ) {
                MaterialTheme { ChatMessageItem(ChatMessage(content = "$clipUrl\n$normalUrl")) }
            }
        }

        for (url in listOf(clipUrl, normalUrl)) {
            val text = compose.onNodeWithText(url, substring = true, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            val start = layout.layoutInput.text.text.indexOf(url)
            text.performTouchInput { click(layout.getBoundingBox(start + 5).center) }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.clip_preview_label)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(id, id), clips)
            assertEquals(listOf(normalUrl), otherLinks)
        }
    }

    @Test fun chatAdaptsToAvailableWidthAndHidesLaterMessagesOnRewind() {
        val position = mutableStateOf(4_000L)
        val wide = mutableStateOf(false)
        val clip = ClipDetail(
            id, "Clip", 12.0, "",
            startedAt = "2026-11-07T21:58:54Z", createdAt = "2026-11-07T21:59:31Z",
            channel = ClipChannel(1, "Streamer", "streamer"),
            creator = ClipCreator(2, "Viewer", "viewer"),
        )
        val state = ClipPlayerUiState(
            clip = clip, loading = false, chatLoading = false, chatComplete = true,
            chatMessages = listOf(
                ChatMessage(id = "first", content = "First message", recordingOffsetMs = 1_000),
                ChatMessage(id = "second", content = "Later message", recordingOffsetMs = 3_000),
            ),
        )
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MaterialTheme {
                    ClipPlayerScreen(
                        uiState = state,
                        playback = ClipPlaybackState(positionMs = position.value, durationMs = 12_000, buffering = false),
                        fullscreen = false, showChat = true, onPlayPauseToggle = {}, onSeekTo = {}, onMuteToggle = {},
                        onFullscreenToggle = {}, onChatVisibilityToggle = {}, onCloseClick = {}, onRetryPlayback = {},
                        onRetryChat = {}, onWatchLivestream = {}, onWatchFullVideo = {},
                        video = { Box(Modifier.fillMaxSize().testTag("clip-video")) },
                        modifier = if (wide.value) Modifier.size(860.dp, 470.dp) else Modifier.size(380.dp, 690.dp),
                    )
                }
            }
        }
        val video = compose.onNodeWithTag("clip-video").getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText(compose.activity.getString(R.string.chat)).getUnclippedBoundsInRoot()
        assertTrue(heading.top >= video.bottom)
        compose.onNodeWithText("Later message", substring = true).assertIsDisplayed()
        compose.runOnIdle { position.value = 1_000L }
        compose.onNodeWithText("Later message", substring = true).assertDoesNotExist()
        compose.onNodeWithText("First message", substring = true).assertIsDisplayed()

        if (compose.activity.resources.configuration.screenWidthDp >= 600) {
            compose.runOnIdle { wide.value = true }
            val wideVideo = compose.onNodeWithTag("clip-video").getUnclippedBoundsInRoot()
            val wideHeading = compose.onNodeWithText(compose.activity.getString(R.string.chat)).getUnclippedBoundsInRoot()
            assertTrue(wideHeading.left >= wideVideo.right)
        }
    }
}
